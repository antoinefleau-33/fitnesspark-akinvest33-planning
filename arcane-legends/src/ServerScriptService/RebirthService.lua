--[[
	RebirthService.lua (ModuleScript - ServerScriptService)
	Ascensions (rebirth) : sacrifier TOUTE sa mana pour un bonus permanent
	de +50 % par ascension. Cout = 10 000 x 4^n. Auras de particules
	aux paliers 1 / 3 / 6 / 10.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local RebirthService = {}

local services = nil

-- Style d'aura par palier (index = rang du palier atteint)
local AURA_STYLES = {
	{ Color = Color3.fromRGB(140, 200, 255), Rate = 8,  Size = 0.35, Speed = 2 },  -- palier 1 : brume bleutee
	{ Color = Color3.fromRGB(190, 120, 255), Rate = 14, Size = 0.5,  Speed = 3 },  -- palier 3 : volutes violettes
	{ Color = Color3.fromRGB(255, 200, 90),  Rate = 22, Size = 0.65, Speed = 4 },  -- palier 6 : flammes dorees
	{ Color = Color3.fromRGB(255, 90, 120),  Rate = 32, Size = 0.8,  Speed = 5 },  -- palier 10 : tempete ecarlate
}

----------------------------------------------------------------
-- Auras
----------------------------------------------------------------

-- Rang du palier atteint (0 = aucun) pour un nombre d'ascensions donne
local function auraTierRank(rebirths)
	local rank = 0
	for i = 1, #Config.Rebirth.AuraTiers do
		if rebirths >= Config.Rebirth.AuraTiers[i] then
			rank = i
		end
	end
	return rank
end

-- (Re)applique l'aura du joueur sur son personnage
function RebirthService.ApplyAura(player)
	local character = player.Character
	if not character then
		return
	end
	local rootPart = character:FindFirstChild("HumanoidRootPart")
	if not rootPart then
		return
	end

	local existing = rootPart:FindFirstChild("AuraAscension")
	if existing then
		existing:Destroy()
	end

	local data = services.DataManager.GetProfile(player)
	if not data then
		return
	end
	local rank = auraTierRank(data.Rebirths)
	if rank == 0 then
		return
	end
	local style = AURA_STYLES[rank] or AURA_STYLES[#AURA_STYLES]

	local emitter = Instance.new("ParticleEmitter")
	emitter.Name = "AuraAscension"
	emitter.Color = ColorSequence.new(style.Color)
	emitter.LightEmission = 1
	emitter.Rate = style.Rate
	emitter.Lifetime = NumberRange.new(0.8, 1.6)
	emitter.Speed = NumberRange.new(style.Speed * 0.5, style.Speed)
	emitter.Size = NumberSequence.new(style.Size)
	emitter.SpreadAngle = Vector2.new(180, 180)
	emitter.Parent = rootPart
end

----------------------------------------------------------------
-- Ascension
----------------------------------------------------------------

local function doRebirth(player)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return false, "Profil en cours de chargement..."
	end
	local cost = services.Economy.GetRebirthCost(player)
	if data.Mana < cost then
		return false, "Il te faut " .. Util.formatNumber(cost) .. " mana pour t'élever."
	end

	-- Sacrifice de TOUTE la mana (pas seulement le cout)
	data.Mana = 0
	data.Rebirths = data.Rebirths + 1

	services.DataManager.UpdateLeaderstats(player)
	services.Economy.PushSnapshot(player)
	RebirthService.ApplyAura(player)

	local bonus = math.floor(Config.Rebirth.BonusPerRebirth * data.Rebirths * 100)
	Remotes.get().Notify:FireClient(player,
		"Ascension " .. tostring(data.Rebirths) .. " accomplie ! Bonus permanent : +" .. tostring(bonus) .. " % de mana.", "succes")
	return true, "Ascension réussie !"
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function RebirthService.Init(registry)
	services = registry

	Remotes.get().DoRebirth.OnServerInvoke = doRebirth

	Players.PlayerAdded:Connect(function(player)
		player.CharacterAdded:Connect(function()
			task.wait(0.3)
			RebirthService.ApplyAura(player)
		end)
	end)
	for _, player in ipairs(Players:GetPlayers()) do
		player.CharacterAdded:Connect(function()
			task.wait(0.3)
			RebirthService.ApplyAura(player)
		end)
		if player.Character then
			RebirthService.ApplyAura(player)
		end
	end
end

return RebirthService
