--[[
	PetService.lua (ModuleScript - ServerScriptService)
	Familiers : achat d'oeufs en gemmes (tirage pondere COTE SERVEUR),
	inventaire, equipement (3 max), visuels attaches au personnage.
	Multiplicateur total calcule dans Economy : 1 + somme des (mult - 1).
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local PetService = {}

local services = nil
local rng = Random.new()

-- Decalages des 3 emplacements de familiers autour du joueur
local SLOT_OFFSETS = {
	CFrame.new(-2.4, 1.5, 1.6),
	CFrame.new(2.4, 1.5, 1.6),
	CFrame.new(0, 2.2, 2.6),
}

----------------------------------------------------------------
-- Visuels des familiers equipes
----------------------------------------------------------------

local function clearPetVisuals(character)
	local existing = character:FindFirstChild("FamiliersVisuels")
	if existing then
		existing:Destroy()
	end
end

-- Reconstruit les orbes-familiers attaches au personnage
function PetService.RefreshPetVisuals(player)
	local character = player.Character
	if not character then
		return
	end
	local rootPart = character:FindFirstChild("HumanoidRootPart")
	if not rootPart then
		return
	end
	clearPetVisuals(character)

	local data = services.DataManager.GetProfile(player)
	if not data then
		return
	end

	local folder = Instance.new("Folder")
	folder.Name = "FamiliersVisuels"

	local slot = 0
	for _, pet in pairs(data.Pets) do
		if pet.Equipped and slot < Config.MaxEquippedPets then
			local def = Config.Pets[pet.Id]
			if def then
				slot = slot + 1
				local rarity = Config.Rarities[def.Rarity]
				local color = rarity and Util.toColor3(rarity.Color) or Color3.fromRGB(255, 255, 255)

				local orb = Instance.new("Part")
				orb.Name = "Familier_" .. pet.Id
				orb.Shape = Enum.PartType.Ball
				orb.Size = Vector3.new(1.6, 1.6, 1.6)
				orb.Color = color
				orb.Material = Enum.Material.Neon
				orb.CanCollide = false
				orb.Massless = true
				orb.CFrame = rootPart.CFrame * SLOT_OFFSETS[slot]

				local weld = Instance.new("WeldConstraint")
				weld.Part0 = rootPart
				weld.Part1 = orb
				weld.Parent = orb

				local emitter = Instance.new("ParticleEmitter")
				emitter.Color = ColorSequence.new(color)
				emitter.LightEmission = 1
				emitter.Rate = 2
				emitter.Lifetime = NumberRange.new(0.5, 1)
				emitter.Speed = NumberRange.new(0.5, 1)
				emitter.Size = NumberSequence.new(0.2)
				emitter.Parent = orb

				local billboard = Instance.new("BillboardGui")
				billboard.Size = UDim2.new(0, 120, 0, 30)
				billboard.StudsOffset = Vector3.new(0, 1.4, 0)
				billboard.MaxDistance = 60
				local label = Instance.new("TextLabel")
				label.Size = UDim2.new(1, 0, 1, 0)
				label.BackgroundTransparency = 1
				label.Font = Enum.Font.FredokaOne
				label.TextScaled = true
				label.Text = def.Name
				label.TextColor3 = color
				label.TextStrokeTransparency = 0.5
				label.Parent = billboard
				billboard.Parent = orb

				orb.Parent = folder
			end
		end
	end

	folder.Parent = character
end

----------------------------------------------------------------
-- Inventaire
----------------------------------------------------------------

-- Ajoute un familier au profil. Retourne uid, ou nil si profil absent.
function PetService.GrantPet(player, petId, equipIfSpace)
	local data = services.DataManager.GetProfile(player)
	if not data or not Config.Pets[petId] then
		return nil
	end
	data.PetCounter = data.PetCounter + 1
	local uid = tostring(data.PetCounter) -- cle STRING (contrainte JSON DataStore)

	local equipped = false
	if equipIfSpace then
		local equippedCount = 0
		for _, pet in pairs(data.Pets) do
			if pet.Equipped then
				equippedCount = equippedCount + 1
			end
		end
		equipped = equippedCount < Config.MaxEquippedPets
	end

	data.Pets[uid] = { Id = petId, Equipped = equipped }
	services.Economy.PushSnapshot(player)
	if equipped then
		PetService.RefreshPetVisuals(player)
	end
	return uid
end

local function buyEgg(player, eggIndex)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return false, "Profil en cours de chargement..."
	end
	if type(eggIndex) ~= "number" then
		return false, "Requête invalide."
	end
	local egg = Config.Eggs[eggIndex]
	if not egg then
		return false, "Cet œuf n'existe pas."
	end
	if not services.Economy.SpendGems(player, egg.Cost) then
		return false, "Pas assez de gemmes (" .. Util.formatNumber(egg.Cost) .. " nécessaires)."
	end

	-- Tirage pondere COTE SERVEUR (le client ne fait que demander)
	local petId = Util.weightedRoll(egg.Pool, rng)
	if not petId then
		-- Pool mal configure : rembourse par securite
		services.Economy.AddGems(player, egg.Cost, false)
		return false, "Erreur de tirage, gemmes remboursées."
	end

	local uid = PetService.GrantPet(player, petId, true)
	local def = Config.Pets[petId]
	return true, {
		Uid = uid,
		Id = petId,
		Name = def.Name,
		Rarity = def.Rarity,
		Multiplier = def.Multiplier,
	}
end

local function setPetEquipped(player, petUid, equipped)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return false, "Profil en cours de chargement..."
	end
	if type(petUid) ~= "string" then
		return false, "Requête invalide."
	end
	local pet = data.Pets[petUid]
	if not pet then
		return false, "Familier introuvable."
	end
	equipped = equipped == true

	if equipped and not pet.Equipped then
		local equippedCount = 0
		for _, other in pairs(data.Pets) do
			if other.Equipped then
				equippedCount = equippedCount + 1
			end
		end
		if equippedCount >= Config.MaxEquippedPets then
			return false, "Maximum " .. tostring(Config.MaxEquippedPets) .. " familiers équipés."
		end
	end

	pet.Equipped = equipped
	services.Economy.PushSnapshot(player)
	PetService.RefreshPetVisuals(player)
	return true, equipped and "Familier équipé !" or "Familier retiré."
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function PetService.Init(registry)
	services = registry

	Remotes.get().BuyEgg.OnServerInvoke = buyEgg
	Remotes.get().SetPetEquipped.OnServerInvoke = setPetEquipped

	-- Reattache les visuels a chaque respawn
	Players.PlayerAdded:Connect(function(player)
		player.CharacterAdded:Connect(function()
			task.wait(0.3) -- laisse le personnage s'assembler
			PetService.RefreshPetVisuals(player)
		end)
	end)
	for _, player in ipairs(Players:GetPlayers()) do
		player.CharacterAdded:Connect(function()
			task.wait(0.3)
			PetService.RefreshPetVisuals(player)
		end)
		if player.Character then
			PetService.RefreshPetVisuals(player)
		end
	end
end

return PetService
