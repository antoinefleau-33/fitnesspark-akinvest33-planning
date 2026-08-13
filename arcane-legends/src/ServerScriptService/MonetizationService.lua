--[[
	MonetizationService.lua (ModuleScript - ServerScriptService)
	Gamepasses (cache + rafraichissement a l'achat en jeu) et developer
	products avec ProcessReceipt IDEMPOTENT (historique des PurchaseId
	dans le profil : aucun double-credit possible).
	Tous les IDs sont dans Config (0 = TODO_UTILISATEUR, a creer sur
	create.roblox.com puis a renseigner dans Config.lua).
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local MarketplaceService = game:GetService("MarketplaceService")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local MonetizationService = {}

local services = nil

-- Cache de possession des gamepasses : [player] = { passKey = bool }
local passCache = {}

----------------------------------------------------------------
-- Gamepasses
----------------------------------------------------------------

local function findPassKeyById(passId)
	for passKey, def in pairs(Config.GamePasses) do
		if def.Id == passId then
			return passKey
		end
	end
	return nil
end

-- Possession d'un gamepass (depuis le cache ; un ID a 0 = jamais possede)
function MonetizationService.OwnsPass(player, passKey)
	local cache = passCache[player]
	return cache ~= nil and cache[passKey] == true
end

-- Interroge Roblox pour un pass donne et met le cache a jour
local function queryPass(player, passKey)
	local def = Config.GamePasses[passKey]
	if not def or def.Id == 0 then
		return false -- TODO_UTILISATEUR : ID non configure
	end
	local ok, owns = pcall(function()
		return MarketplaceService:UserOwnsGamePassAsync(player.UserId, def.Id)
	end)
	if ok and passCache[player] then
		passCache[player][passKey] = owns == true
	end
	return ok and owns == true
end

-- Effets immediats a l'obtention d'un pass (achat en jeu ou detection au login)
local function applyPassEffects(player, passKey)
	if passKey == "VoidLord" then
		local data = services.DataManager.GetProfile(player)
		if data and not data.VoidLordGranted then
			data.VoidLordGranted = true
			services.PetService.GrantPet(player, "seigneurvide", true)
			Remotes.get().Notify:FireClient(player,
				"Le Seigneur du Vide (x3.5) a rejoint tes familiers !", "succes")
		end
	end
	services.Economy.PushSnapshot(player)
end

local function preloadPasses(player)
	passCache[player] = {}
	for passKey, _ in pairs(Config.GamePasses) do
		task.spawn(function()
			if queryPass(player, passKey) then
				applyPassEffects(player, passKey)
			end
		end)
	end
end

----------------------------------------------------------------
-- Developer products (ProcessReceipt idempotent)
----------------------------------------------------------------

local function findProductKeyById(productId)
	for productKey, def in pairs(Config.Products) do
		if def.Id == productId then
			return productKey
		end
	end
	return nil
end

local function grantProduct(player, productKey)
	local def = Config.Products[productKey]
	local data = services.DataManager.GetProfile(player)
	if not def or not data then
		return false
	end

	if productKey == "StarterPack" then
		if data.StarterPackOwned then
			-- Achat duplique d'un pack UNIQUE (ne devrait pas arriver : l'UI le
			-- masque une fois possede). On credite mana + gemmes pour ne pas
			-- leser l'acheteur, mais pas de second familier exclusif.
			services.Economy.AddMana(player, def.Mana)
			services.Economy.AddGems(player, def.Gems, false)
		else
			data.StarterPackOwned = true
			services.Economy.AddMana(player, def.Mana)
			services.Economy.AddGems(player, def.Gems, false)
			services.PetService.GrantPet(player, def.Pet, true)
		end
		Remotes.get().Notify:FireClient(player, "Pack de Départ reçu : merci pour ton soutien !", "succes")
		return true
	end

	if def.Gems then
		services.Economy.AddGems(player, def.Gems, false)
		Remotes.get().Notify:FireClient(player,
			"+" .. tostring(def.Gems) .. " gemmes ! Merci pour ton soutien !", "gemme")
		return true
	end

	return false
end

local function processReceipt(receiptInfo)
	local player = Players:GetPlayerByUserId(receiptInfo.PlayerId)
	if not player then
		-- Joueur deconnecte : Roblox re-essaiera a sa prochaine connexion
		return Enum.ProductPurchaseDecision.NotProcessedYet
	end

	local data = services.DataManager.GetProfile(player)
	if not data then
		return Enum.ProductPurchaseDecision.NotProcessedYet -- profil pas charge
	end
	if not services.DataManager.CanSave(player) then
		-- Session temporaire : on ne consomme JAMAIS un achat qu'on ne peut
		-- pas persister (sinon credit perdu a la deconnexion).
		return Enum.ProductPurchaseDecision.NotProcessedYet
	end

	-- IDEMPOTENCE : cle STRING du PurchaseId dans le profil
	local purchaseKey = tostring(receiptInfo.PurchaseId)
	if data.Purchases[purchaseKey] then
		return Enum.ProductPurchaseDecision.PurchaseGranted -- deja credite
	end

	local productKey = findProductKeyById(receiptInfo.ProductId)
	if not productKey then
		warn("[Monetization] ProductId inconnu : " .. tostring(receiptInfo.ProductId))
		return Enum.ProductPurchaseDecision.NotProcessedYet
	end

	local granted = grantProduct(player, productKey)
	if not granted then
		return Enum.ProductPurchaseDecision.NotProcessedYet
	end

	data.Purchases[purchaseKey] = true
	-- Sauvegarde immediate (best effort) : si elle echoue, l'autosave et la
	-- save de deconnexion prendront le relais ; l'historique en memoire
	-- garantit deja l'absence de double-credit pendant la session.
	task.spawn(function()
		services.DataManager.SaveProfile(player)
	end)
	return Enum.ProductPurchaseDecision.PurchaseGranted
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function MonetizationService.Init(registry)
	services = registry

	Players.PlayerAdded:Connect(preloadPasses)
	for _, player in ipairs(Players:GetPlayers()) do
		preloadPasses(player)
	end
	Players.PlayerRemoving:Connect(function(player)
		passCache[player] = nil
	end)

	-- Rafraichissement du cache quand un gamepass est achete EN JEU
	MarketplaceService.PromptGamePassPurchaseFinished:Connect(function(player, passId, purchased)
		if not purchased then
			return
		end
		local passKey = findPassKeyById(passId)
		if passKey and passCache[player] then
			passCache[player][passKey] = true
			applyPassEffects(player, passKey)
			Remotes.get().Notify:FireClient(player,
				"Gamepass « " .. Config.GamePasses[passKey].Name .. " » activé. Merci !", "succes")
		end
	end)

	MarketplaceService.ProcessReceipt = processReceipt
end

return MonetizationService
