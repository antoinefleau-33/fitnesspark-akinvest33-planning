--[[
	DataManager.lua (ModuleScript - ServerScriptService)
	Chargement / sauvegarde des profils joueurs.
	- Retry + backoff exponentiel sur toutes les operations DataStore
	- Autosave periodique, save a la deconnexion, BindToClose
	- Echec de chargement => session TEMPORAIRE jamais sauvegardee
	  (on n'ecrase jamais de vraies donnees avec un profil vide)
	- Toutes les cles de tables persistees sont des STRINGS (contrainte JSON)
	Lua 5.1 pur.
]]

local DataStoreService = game:GetService("DataStoreService")
local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local DataManager = {}

local services = nil
local store = nil

-- [player] = { Data = table, CanSave = bool }
local sessions = {}

-- Modele de profil. Toutes les cles de sous-tables persistees sont des strings.
local PROFILE_TEMPLATE = {
	Version = 1,
	Mana = 0,
	Gems = 0,
	Rebirths = 0,
	TotalMana = 0,                -- mana totale gagnee (classement global)
	Zones = { ["1"] = true },     -- zoneId (string) -> true
	Pets = {},                    -- uid (string) -> { Id = petId, Equipped = bool }
	PetCounter = 0,
	AutoTrain = false,
	StarterPackOwned = false,
	VoidLordGranted = false,
	Purchases = {},               -- PurchaseId (string) -> true (idempotence ProcessReceipt)
}

----------------------------------------------------------------
-- Outils internes
----------------------------------------------------------------

local function profileKey(userId)
	return "Joueur_" .. tostring(userId)
end

-- Appelle fn avec retry + backoff exponentiel. Retourne ok, resultatOuErreur.
local function withRetry(operationName, fn)
	local delay = Config.Data.RetryBaseDelay
	local lastError = nil
	for attempt = 1, Config.Data.MaxRetries do
		local ok, result = pcall(fn)
		if ok then
			return true, result
		end
		lastError = result
		warn(string.format("[DataManager] %s : tentative %d/%d echouee : %s",
			operationName, attempt, Config.Data.MaxRetries, tostring(result)))
		if attempt < Config.Data.MaxRetries then
			task.wait(delay)
			delay = delay * 2
		end
	end
	return false, lastError
end

-- Complete les cles manquantes du profil avec le modele (migration douce)
local function reconcile(data, template)
	for key, defaultValue in pairs(template) do
		if data[key] == nil then
			data[key] = Util.deepCopy(defaultValue)
		elseif type(defaultValue) == "table" and type(data[key]) ~= "table" then
			data[key] = Util.deepCopy(defaultValue)
		end
	end
	return data
end

local function createLeaderstats(player, data)
	local leaderstats = Instance.new("Folder")
	leaderstats.Name = "leaderstats"

	local mana = Instance.new("StringValue")
	mana.Name = "Mana"
	mana.Value = Util.formatNumber(data.Mana)
	mana.Parent = leaderstats

	local gems = Instance.new("StringValue")
	gems.Name = "Gemmes"
	gems.Value = Util.formatNumber(data.Gems)
	gems.Parent = leaderstats

	local rebirths = Instance.new("IntValue")
	rebirths.Name = "Ascensions"
	rebirths.Value = data.Rebirths
	rebirths.Parent = leaderstats

	leaderstats.Parent = player
end

----------------------------------------------------------------
-- API publique
----------------------------------------------------------------

-- Retourne le profil (table) ou nil si pas encore charge
function DataManager.GetProfile(player)
	local session = sessions[player]
	if session then
		return session.Data
	end
	return nil
end

-- La session peut-elle etre sauvegardee ? (false = session temporaire)
function DataManager.CanSave(player)
	local session = sessions[player]
	return session ~= nil and session.CanSave
end

-- Met a jour l'affichage leaderstats du joueur
function DataManager.UpdateLeaderstats(player)
	local data = DataManager.GetProfile(player)
	if not data then
		return
	end
	local leaderstats = player:FindFirstChild("leaderstats")
	if not leaderstats then
		return
	end
	local mana = leaderstats:FindFirstChild("Mana")
	if mana then
		mana.Value = Util.formatNumber(data.Mana)
	end
	local gems = leaderstats:FindFirstChild("Gemmes")
	if gems then
		gems.Value = Util.formatNumber(data.Gems)
	end
	local rebirths = leaderstats:FindFirstChild("Ascensions")
	if rebirths then
		rebirths.Value = data.Rebirths
	end
end

-- Sauvegarde le profil d'un joueur (respecte CanSave). Retourne ok.
function DataManager.SaveProfile(player)
	local session = sessions[player]
	if not session then
		return false
	end
	if not session.CanSave then
		warn("[DataManager] Session temporaire pour " .. player.Name .. " : sauvegarde refusee (protection des donnees).")
		return false
	end
	local dataCopy = Util.deepCopy(session.Data)
	local ok = withRetry("SaveProfile(" .. player.Name .. ")", function()
		return store:UpdateAsync(profileKey(player.UserId), function()
			return dataCopy
		end)
	end)
	return ok
end

-- Charge (ou cree) le profil d'un joueur qui vient d'arriver
local function loadProfile(player)
	local key = profileKey(player.UserId)
	local ok, stored = withRetry("LoadProfile(" .. player.Name .. ")", function()
		return store:GetAsync(key)
	end)

	local session
	if not ok then
		-- Echec DataStore : session temporaire, JAMAIS sauvegardee
		session = { Data = Util.deepCopy(PROFILE_TEMPLATE), CanSave = false }
		warn("[DataManager] Chargement impossible pour " .. player.Name .. " : session temporaire (aucune sauvegarde ne sera faite).")
	elseif stored == nil then
		-- Nouveau joueur
		session = { Data = Util.deepCopy(PROFILE_TEMPLATE), CanSave = true }
	else
		session = { Data = reconcile(stored, PROFILE_TEMPLATE), CanSave = true }
	end

	if not player.Parent then
		return -- le joueur est parti pendant le chargement
	end

	sessions[player] = session
	createLeaderstats(player, session.Data)

	if services and services.Economy then
		services.Economy.PushSnapshot(player)
	end
	if not session.CanSave then
		Remotes.get().Notify:FireClient(player,
			"Connexion aux sauvegardes impossible : session temporaire, ta progression ne sera PAS enregistrée.", "erreur")
	end
end

local function onPlayerRemoving(player)
	local session = sessions[player]
	if session then
		DataManager.SaveProfile(player)
		sessions[player] = nil
	end
end

-- Sauvegarde tout le monde (autosave / BindToClose)
function DataManager.SaveAll()
	for player, _ in pairs(sessions) do
		DataManager.SaveProfile(player)
	end
end

function DataManager.Init(registry)
	services = registry
	store = DataStoreService:GetDataStore(Config.Data.StoreName)

	Players.PlayerAdded:Connect(function(player)
		loadProfile(player)
	end)
	-- Joueurs deja presents (si le script demarre apres eux)
	for _, player in ipairs(Players:GetPlayers()) do
		task.spawn(loadProfile, player)
	end

	Players.PlayerRemoving:Connect(onPlayerRemoving)

	-- Autosave periodique
	task.spawn(function()
		while true do
			task.wait(Config.Data.AutosaveInterval)
			DataManager.SaveAll()
		end
	end)

	-- Sauvegarde a la fermeture du serveur
	game:BindToClose(function()
		if RunService:IsStudio() then
			-- En Studio, laisser juste le temps aux saves de partir
			DataManager.SaveAll()
			task.wait(2)
			return
		end
		local remaining = 0
		for player, _ in pairs(sessions) do
			remaining = remaining + 1
			task.spawn(function()
				DataManager.SaveProfile(player)
				remaining = remaining - 1
			end)
		end
		local deadline = os.clock() + 25
		while remaining > 0 and os.clock() < deadline do
			task.wait(0.1)
		end
	end)
end

return DataManager
