--[[
	CombatService.lua (ModuleScript - ServerScriptService)
	PvP en arene : cliquer un adversaire = sort (projectile visuel).
	Degats = clamp(5 + Mana^0.35, 5, 50).
	Validations SERVEUR : les deux joueurs dans l'arene, portee 45, cooldown 0.6 s.
	Vainqueur d'un duel : +25 gemmes.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")
local Workspace = game:GetService("Workspace")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local CombatService = {}

local services = nil

local lastCast = {}     -- [player] = os.clock() du dernier sort
local lastAttacker = {} -- [victimPlayer] = { Attacker = player, Time = os.clock() }

local KILL_CREDIT_WINDOW = 10 -- secondes pendant lesquelles un coup compte pour la victoire

----------------------------------------------------------------
-- Zone d'arene
----------------------------------------------------------------

local function isInArena(position)
	local arena = services.MapHandles.Arena
	return Util.isInsideRect(position, arena.CenterX, arena.CenterZ, arena.SizeX, arena.SizeZ)
end

----------------------------------------------------------------
-- Projectile visuel (cree serveur, replique a tous)
----------------------------------------------------------------

local function spawnProjectile(fromPosition, toPosition)
	local distance = (toPosition - fromPosition).Magnitude
	if distance < 1 then
		return
	end
	local direction = (toPosition - fromPosition).Unit
	local travelTime = distance / Config.Combat.ProjectileSpeed

	local orb = Instance.new("Part")
	orb.Name = "SortArcanique"
	orb.Shape = Enum.PartType.Ball
	orb.Size = Vector3.new(1.4, 1.4, 1.4)
	orb.Color = Color3.fromRGB(170, 110, 255)
	orb.Material = Enum.Material.Neon
	orb.Anchored = true
	orb.CanCollide = false
	orb.CFrame = CFrame.new(fromPosition)

	local light = Instance.new("PointLight")
	light.Color = orb.Color
	light.Range = 10
	light.Parent = orb

	local trail = Instance.new("ParticleEmitter")
	trail.Color = ColorSequence.new(orb.Color)
	trail.LightEmission = 1
	trail.Rate = 40
	trail.Lifetime = NumberRange.new(0.2, 0.4)
	trail.Speed = NumberRange.new(0, 0.5)
	trail.Size = NumberSequence.new(0.4)
	trail.Parent = orb

	orb.Parent = Workspace

	task.spawn(function()
		local steps = math.max(2, math.floor(travelTime / 0.03))
		for i = 1, steps do
			orb.CFrame = CFrame.new(fromPosition + direction * (distance * i / steps))
			task.wait(travelTime / steps)
		end
		-- Petit eclat d'impact
		orb.Size = Vector3.new(3, 3, 3)
		orb.Transparency = 0.4
		trail.Enabled = false
		task.wait(0.12)
		orb:Destroy()
	end)
end

----------------------------------------------------------------
-- Lancer de sort
----------------------------------------------------------------

local function onCastSpell(attacker, target)
	-- Le client ne fait que DEMANDER : tout est verifie ici.
	if typeof(target) ~= "Instance" or not target:IsA("Player") then
		return
	end
	if target == attacker or target.Parent == nil then
		return
	end

	local data = services.DataManager.GetProfile(attacker)
	if not data then
		return
	end

	-- Cooldown serveur
	local now = os.clock()
	if lastCast[attacker] and (now - lastCast[attacker]) < Config.Combat.Cooldown then
		return
	end

	local attackerRoot = Util.getRootPart(attacker)
	local targetRoot = Util.getRootPart(target)
	if not attackerRoot or not targetRoot then
		return
	end

	-- Les DEUX joueurs doivent etre dans l'arene
	if not isInArena(attackerRoot.Position) or not isInArena(targetRoot.Position) then
		return
	end

	-- Portee validee serveur
	local distance = (attackerRoot.Position - targetRoot.Position).Magnitude
	if distance > Config.Combat.Range then
		return
	end

	local targetHumanoid = Util.getAliveHumanoid(target)
	if not targetHumanoid then
		return
	end

	lastCast[attacker] = now

	-- Degats bases sur la mana ACTUELLE de l'attaquant
	local damage = Util.clamp(
		Config.Combat.MinDamage + data.Mana ^ Config.Combat.ManaExponent,
		Config.Combat.MinDamage,
		Config.Combat.MaxDamage)

	lastAttacker[target] = { Attacker = attacker, Time = now }
	spawnProjectile(
		attackerRoot.Position + Vector3.new(0, 1, 0),
		targetRoot.Position)
	targetHumanoid:TakeDamage(damage)
end

----------------------------------------------------------------
-- Victoire de duel
----------------------------------------------------------------

local function onCharacterDied(victim)
	local record = lastAttacker[victim]
	lastAttacker[victim] = nil
	if not record then
		return
	end
	if (os.clock() - record.Time) > KILL_CREDIT_WINDOW then
		return
	end
	local winner = record.Attacker
	if not winner or winner.Parent == nil then
		return
	end
	local gained = services.Economy.AddGems(winner, Config.Gems.DuelWin, true)
	Remotes.get().Notify:FireClient(winner,
		"Duel remporté contre " .. victim.Name .. " ! +" .. tostring(gained or Config.Gems.DuelWin) .. " gemmes.", "gemme")
	Remotes.get().Notify:FireClient(victim,
		"Duel perdu contre " .. winner.Name .. "... Reviens plus fort !", "info")
end

local function hookCharacter(player)
	player.CharacterAdded:Connect(function(character)
		local humanoid = character:WaitForChild("Humanoid", 10)
		if humanoid then
			humanoid.Died:Connect(function()
				onCharacterDied(player)
			end)
		end
	end)
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function CombatService.Init(registry)
	services = registry

	Remotes.get().CastSpell.OnServerEvent:Connect(onCastSpell)

	Players.PlayerAdded:Connect(hookCharacter)
	for _, player in ipairs(Players:GetPlayers()) do
		hookCharacter(player)
		if player.Character then
			local humanoid = player.Character:FindFirstChildOfClass("Humanoid")
			if humanoid then
				humanoid.Died:Connect(function()
					onCharacterDied(player)
				end)
			end
		end
	end

	Players.PlayerRemoving:Connect(function(player)
		lastCast[player] = nil
		lastAttacker[player] = nil
	end)
end

return CombatService
