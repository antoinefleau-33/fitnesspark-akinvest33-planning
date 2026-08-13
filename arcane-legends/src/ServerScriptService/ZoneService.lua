--[[
	ZoneService.lua (ModuleScript - ServerScriptService)
	Achat des zones aux totems (ProximityPrompt) + pads de teleportation VIP.
	Les zones sont physiquement ouvertes mais le GAIN est verrouille serveur
	tant que la zone n'est pas achetee (verifie dans TrainingService).
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local MarketplaceService = game:GetService("MarketplaceService")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local ZoneService = {}

local services = nil

-- anti-rebond des pads de teleportation : [player] = os.clock()
local lastTeleport = {}

----------------------------------------------------------------
-- Achat de zone
----------------------------------------------------------------

local function tryBuyZone(player, zoneId)
	local data = services.DataManager.GetProfile(player)
	if not data then
		return
	end
	local zoneDef = Config.Zones[zoneId]
	if not zoneDef then
		return
	end
	local key = tostring(zoneId)
	if data.Zones[key] then
		Remotes.get().Notify:FireClient(player,
			"« " .. zoneDef.Name .. " » est déjà débloquée !", "info")
		return
	end
	-- Progression lineaire : il faut posseder la zone precedente
	if zoneId > 1 and not data.Zones[tostring(zoneId - 1)] then
		Remotes.get().Notify:FireClient(player,
			"Débloque d'abord « " .. Config.Zones[zoneId - 1].Name .. " » !", "erreur")
		return
	end
	if not services.Economy.SpendMana(player, zoneDef.Cost) then
		Remotes.get().Notify:FireClient(player,
			"Pas assez de mana ! Il te faut " .. Util.formatNumber(zoneDef.Cost) .. " mana.", "erreur")
		return
	end
	data.Zones[key] = true
	services.Economy.PushSnapshot(player)
	Remotes.get().Notify:FireClient(player,
		"Zone débloquée : « " .. zoneDef.Name .. " » (mana x" .. tostring(zoneDef.Multiplier) .. ") !", "succes")
end

----------------------------------------------------------------
-- Teleportation VIP
----------------------------------------------------------------

local function teleportTo(player, position)
	local rootPart = Util.getRootPart(player)
	if rootPart then
		rootPart.CFrame = CFrame.new(position + Vector3.new(0, 4, 0))
	end
end

local function onPadTouched(hit, targetPositionGetter, requiresVIP)
	local character = hit and hit.Parent
	if not character then
		return
	end
	local player = Players:GetPlayerFromCharacter(character)
	if not player then
		return
	end
	local now = os.clock()
	if lastTeleport[player] and (now - lastTeleport[player]) < 1.5 then
		return
	end
	lastTeleport[player] = now

	if requiresVIP and not services.Monetization.OwnsPass(player, "VIPZone") then
		Remotes.get().Notify:FireClient(player,
			"Le Sanctuaire VIP est réservé aux détenteurs du gamepass « Sanctuaire VIP ».", "erreur")
		local passId = Config.GamePasses.VIPZone.Id
		if passId and passId > 0 then
			pcall(function()
				MarketplaceService:PromptGamePassPurchase(player, passId)
			end)
		end
		return
	end
	teleportTo(player, targetPositionGetter())
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function ZoneService.Init(registry)
	services = registry

	-- Totems d'achat
	for zoneId, totem in pairs(services.MapHandles.Totems) do
		totem.Prompt.Triggered:Connect(function(player)
			tryBuyZone(player, zoneId)
		end)
	end

	-- Pads de teleportation VIP
	local vip = services.MapHandles.VIP
	vip.PadTo.Touched:Connect(function(hit)
		onPadTouched(hit, function()
			return Vector3.new(vip.CenterX, vip.TopY + 2, vip.CenterZ - 10)
		end, true)
	end)
	vip.PadBack.Touched:Connect(function(hit)
		onPadTouched(hit, function()
			return Vector3.new(0, 3, 20) -- spawn du lobby
		end, false)
	end)

	Players.PlayerRemoving:Connect(function(player)
		lastTeleport[player] = nil
	end)
end

return ZoneService
