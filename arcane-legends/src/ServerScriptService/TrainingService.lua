--[[
	TrainingService.lua (ModuleScript - ServerScriptService)
	Canalisation des cristaux (clic / tap / touche E) + auto-canalisation (gamepass).
	100 % valide serveur : distance, cooldown, zone possedee, acces VIP.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local TrainingService = {}

local services = nil
local rng = Random.new()

-- [player] = horodatage de la derniere canalisation (os.clock)
local lastChannel = {}
-- [player] = horodatage de la derniere notif "zone verrouillee" (anti-spam)
local lastLockedNotify = {}
local crystals = {} -- liste des cristaux construits par MapBuilder

----------------------------------------------------------------
-- Canalisation
----------------------------------------------------------------

-- Verifie que le joueur a le droit de canaliser ce cristal. Retourne ok, zoneMultiplier.
local function validateChannel(player, crystal)
	if not crystal or not crystal.Parent then
		return false
	end
	local data = services.DataManager.GetProfile(player)
	if not data then
		return false -- profil pas encore charge
	end

	local rootPart = Util.getRootPart(player)
	if not rootPart then
		return false
	end

	-- Distance validee serveur (anti-triche)
	local distance = (rootPart.Position - crystal.Position).Magnitude
	if distance > Config.Training.ChannelRange + 6 then -- +6 : tolerance latence/taille perso
		return false
	end

	-- Cooldown serveur
	local now = os.clock()
	local last = lastChannel[player]
	if last and (now - last) < Config.Training.ChannelCooldown then
		return false
	end

	local zoneId = crystal:GetAttribute("ZoneId")
	if zoneId == 0 then
		-- Sanctuaire VIP : reserve au gamepass
		if not services.Monetization.OwnsPass(player, "VIPZone") then
			return false
		end
		return true, Config.VIPZone.Multiplier
	end

	local zoneDef = Config.Zones[zoneId]
	if not zoneDef then
		return false
	end
	-- Zone physiquement ouverte mais gain verrouille tant que non achetee
	if not data.Zones[tostring(zoneId)] then
		if not lastLockedNotify[player] or (now - lastLockedNotify[player]) > 2 then
			lastLockedNotify[player] = now
			Remotes.get().Notify:FireClient(player,
				"Zone verrouillée ! Débloque « " .. zoneDef.Name .. " » à son totem d'entrée.", "erreur")
		end
		return false
	end
	return true, zoneDef.Multiplier
end

-- Effectue une canalisation si valide. Retourne true si le gain a eu lieu.
function TrainingService.Channel(player, crystal)
	local ok, zoneMultiplier = validateChannel(player, crystal)
	if not ok then
		return false
	end

	lastChannel[player] = os.clock()

	local gain = Config.Training.BaseManaPerChannel
		* zoneMultiplier
		* services.Economy.GetGlobalMultiplier(player)
	gain = math.floor(gain + 0.5)
	if gain < 1 then
		gain = 1
	end
	services.Economy.AddMana(player, gain)

	-- 5 % de chance de gemmes (1 a 3)
	local gems = 0
	if rng:NextNumber() < Config.Training.GemChance then
		gems = rng:NextInteger(Config.Training.GemMin, Config.Training.GemMax)
		gems = services.Economy.AddGems(player, gems, true) or gems
	end

	-- Retour visuel au client (texte flottant au-dessus du cristal)
	Remotes.get().ChannelResult:FireClient(player, crystal.Position, gain, gems)
	return true
end

----------------------------------------------------------------
-- Auto-canalisation (gamepass Auto-Canalisation)
----------------------------------------------------------------

local function findNearestCrystal(rootPart)
	local best = nil
	local bestDistance = Config.Training.ChannelRange
	for i = 1, #crystals do
		local crystal = crystals[i]
		if crystal.Parent then
			local distance = (rootPart.Position - crystal.Position).Magnitude
			if distance <= bestDistance then
				bestDistance = distance
				best = crystal
			end
		end
	end
	return best
end

local function autoLoop()
	while true do
		task.wait(Config.Training.AutoInterval)
		for _, player in ipairs(Players:GetPlayers()) do
			local data = services.DataManager.GetProfile(player)
			if data and data.AutoTrain and services.Monetization.OwnsPass(player, "AutoTrain") then
				local rootPart = Util.getRootPart(player)
				if rootPart then
					local crystal = findNearestCrystal(rootPart)
					if crystal then
						TrainingService.Channel(player, crystal)
					end
				end
			end
		end
	end
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function TrainingService.Init(registry)
	services = registry

	-- Branche tous les cristaux construits par MapBuilder
	local folder = services.MapHandles.CrystalsFolder
	for _, crystal in ipairs(folder:GetChildren()) do
		if crystal:GetAttribute("CrystalId") then
			table.insert(crystals, crystal)
			local prompt = crystal:FindFirstChild("PromptCanaliser")
			if prompt then
				prompt.Triggered:Connect(function(player)
					TrainingService.Channel(player, crystal)
				end)
			end
			local clicker = crystal:FindFirstChild("ClicCanaliser")
			if clicker then
				clicker.MouseClick:Connect(function(player)
					TrainingService.Channel(player, crystal)
				end)
			end
		end
	end

	-- Toggle de l'auto-canalisation demande par le client
	Remotes.get().ToggleAuto.OnServerEvent:Connect(function(player, enabled)
		local data = services.DataManager.GetProfile(player)
		if not data then
			return
		end
		if not services.Monetization.OwnsPass(player, "AutoTrain") then
			Remotes.get().Notify:FireClient(player,
				"L'auto-canalisation nécessite le gamepass « Auto-Canalisation ».", "erreur")
			return
		end
		data.AutoTrain = enabled == true
		services.Economy.PushSnapshot(player)
	end)

	-- Nettoyage des cooldowns
	Players.PlayerRemoving:Connect(function(player)
		lastChannel[player] = nil
		lastLockedNotify[player] = nil
	end)

	task.spawn(autoLoop)
end

return TrainingService
