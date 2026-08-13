--[[
	Economy.lua (ModuleScript - ServerScriptService)
	Source unique de verite pour :
	- les multiplicateurs (familiers, ascensions, gamepasses)
	- toutes les transactions de mana et de gemmes
	- l'envoi du snapshot de profil au client (ProfileChanged)
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local Economy = {}

local services = nil

----------------------------------------------------------------
-- Multiplicateurs
----------------------------------------------------------------

-- Multiplicateur familiers : 1 + somme des (mult - 1) des familiers equipes
function Economy.GetPetMultiplier(player)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return 1
	end
	local total = 1
	for _, pet in pairs(data.Pets) do
		if pet.Equipped then
			local def = Config.Pets[pet.Id]
			if def then
				total = total + (def.Multiplier - 1)
			end
		end
	end
	return total
end

-- Multiplicateur ascensions : 1 + 0.5 par ascension
function Economy.GetRebirthMultiplier(player)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return 1
	end
	return 1 + Config.Rebirth.BonusPerRebirth * data.Rebirths
end

-- Multiplicateur gamepass mana (x2 si possede)
function Economy.GetPassManaMultiplier(player)
	if services.Monetization and services.Monetization.OwnsPass(player, "DoubleMana") then
		return 2
	end
	return 1
end

-- Multiplicateur global HORS zone (la zone depend du cristal canalise)
function Economy.GetGlobalMultiplier(player)
	return Economy.GetPetMultiplier(player)
		* Economy.GetRebirthMultiplier(player)
		* Economy.GetPassManaMultiplier(player)
end

----------------------------------------------------------------
-- Transactions
----------------------------------------------------------------

-- Ajout brut de mana (deja multipliee). Compte dans TotalMana si countTotal ~= false.
function Economy.AddMana(player, amount, countTotal)
	local data = services.DataManager.GetProfile(player)
	if not data or amount <= 0 then
		return
	end
	data.Mana = data.Mana + amount
	if countTotal ~= false then
		data.TotalMana = data.TotalMana + amount
	end
	services.DataManager.UpdateLeaderstats(player)
	Economy.PushSnapshot(player)
end

function Economy.SpendMana(player, amount)
	local data = services.DataManager.GetProfile(player)
	if not data or amount < 0 or data.Mana < amount then
		return false
	end
	data.Mana = data.Mana - amount
	services.DataManager.UpdateLeaderstats(player)
	Economy.PushSnapshot(player)
	return true
end

-- Ajout de gemmes. applyPass = true pour appliquer le gamepass x2 Gemmes
-- (gains de jeu : canalisation, duels). Les ACHATS (products) passent applyPass = false.
function Economy.AddGems(player, amount, applyPass)
	local data = services.DataManager.GetProfile(player)
	if not data or amount <= 0 then
		return
	end
	local final = amount
	if applyPass and services.Monetization and services.Monetization.OwnsPass(player, "DoubleGems") then
		final = final * 2
	end
	data.Gems = data.Gems + final
	services.DataManager.UpdateLeaderstats(player)
	Economy.PushSnapshot(player)
	return final
end

function Economy.SpendGems(player, amount)
	local data = services.DataManager.GetProfile(player)
	if not data or amount < 0 or data.Gems < amount then
		return false
	end
	data.Gems = data.Gems - amount
	services.DataManager.UpdateLeaderstats(player)
	Economy.PushSnapshot(player)
	return true
end

----------------------------------------------------------------
-- Snapshot client
----------------------------------------------------------------

-- Cout de la prochaine ascension
function Economy.GetRebirthCost(player)
	local data = services.DataManager.GetProfile(player)
	local n = data and data.Rebirths or 0
	return Config.Rebirth.BaseCost * (Config.Rebirth.CostFactor ^ n)
end

function Economy.BuildSnapshot(player)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return nil
	end

	local pets = {}
	for uid, pet in pairs(data.Pets) do
		local def = Config.Pets[pet.Id]
		if def then
			table.insert(pets, {
				Uid = uid,
				Id = pet.Id,
				Name = def.Name,
				Rarity = def.Rarity,
				Multiplier = def.Multiplier,
				Equipped = pet.Equipped == true,
			})
		end
	end
	table.sort(pets, function(a, b)
		local ra = Config.Rarities[a.Rarity] and Config.Rarities[a.Rarity].Order or 0
		local rb = Config.Rarities[b.Rarity] and Config.Rarities[b.Rarity].Order or 0
		if ra ~= rb then
			return ra > rb
		end
		return a.Uid < b.Uid
	end)

	local passes = {}
	if services.Monetization then
		for passKey, _ in pairs(Config.GamePasses) do
			passes[passKey] = services.Monetization.OwnsPass(player, passKey)
		end
	end

	return {
		Mana = data.Mana,
		Gems = data.Gems,
		Rebirths = data.Rebirths,
		TotalMana = data.TotalMana,
		Zones = data.Zones,
		Pets = pets,
		AutoTrain = data.AutoTrain == true,
		StarterPackOwned = data.StarterPackOwned == true,
		Passes = passes,
		PetMultiplier = Economy.GetPetMultiplier(player),
		RebirthMultiplier = Economy.GetRebirthMultiplier(player),
		GlobalMultiplier = Economy.GetGlobalMultiplier(player),
		RebirthCost = Economy.GetRebirthCost(player),
		CanSave = services.DataManager.CanSave(player),
		MaxEquippedPets = Config.MaxEquippedPets,
	}
end

-- Envoie l'etat complet au client (UI)
function Economy.PushSnapshot(player)
	local snapshot = Economy.BuildSnapshot(player)
	if snapshot then
		Remotes.get().ProfileChanged:FireClient(player, snapshot)
	end
end

function Economy.Init(registry)
	services = registry

	-- Le client peut demander son profil complet (ouverture d'UI, respawn...)
	Remotes.get().GetProfile.OnServerInvoke = function(player)
		return Economy.BuildSnapshot(player)
	end
end

return Economy
