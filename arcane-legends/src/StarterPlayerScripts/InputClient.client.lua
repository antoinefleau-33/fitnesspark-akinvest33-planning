--[[
	InputClient.client.lua (LocalScript - StarterPlayer > StarterPlayerScripts)
	Entrees du joueur :
	- PvP : clic / tap sur un adversaire dans l'arene -> demande CastSpell
	  (le serveur valide tout : arene, portee, cooldown)
	- Animation locale des cristaux (rotation + flottement), zero cout reseau
	Les cristaux eux-memes se canalisent via ClickDetector / ProximityPrompt
	geres directement par le serveur.
	Lua 5.1 pur (compatible Luau).
]]

local Players = game:GetService("Players")
local UserInputService = game:GetService("UserInputService")
local RunService = game:GetService("RunService")
local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Workspace = game:GetService("Workspace")

local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local localPlayer = Players.LocalPlayer
local camera = Workspace.CurrentCamera

local CRYSTAL_TAG = "ArcaneCrystal"

----------------------------------------------------------------
-- PvP : viser un adversaire au clic / tap
----------------------------------------------------------------

local function findPlayerFromInstance(instance)
	local node = instance
	while node and node ~= Workspace do
		local player = Players:GetPlayerFromCharacter(node)
		if player then
			return player
		end
		node = node.Parent
	end
	return nil
end

local function tryCastAt(screenX, screenY)
	local ray = camera:ViewportPointToRay(screenX, screenY)
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	local exclude = {}
	if localPlayer.Character then
		table.insert(exclude, localPlayer.Character)
	end
	params.FilterDescendantsInstances = exclude

	local result = Workspace:Raycast(ray.Origin, ray.Direction * 300, params)
	if not result or not result.Instance then
		return
	end
	local target = findPlayerFromInstance(result.Instance)
	if target and target ~= localPlayer then
		-- Le client ne fait que DEMANDER : toutes les verifications sont serveur
		Remotes.get().CastSpell:FireServer(target)
	end
end

UserInputService.InputBegan:Connect(function(input, gameProcessed)
	if gameProcessed then
		return
	end
	if input.UserInputType == Enum.UserInputType.MouseButton1 then
		tryCastAt(input.Position.X, input.Position.Y)
	end
end)

UserInputService.TouchTapInWorld:Connect(function(position, gameProcessed)
	if gameProcessed then
		return
	end
	tryCastAt(position.X, position.Y)
end)

----------------------------------------------------------------
-- Animation locale des cristaux (rotation + flottement doux)
----------------------------------------------------------------

local animated = {} -- { { Part = part, Base = CFrame, Phase = number } }

local function watchCrystal(part)
	table.insert(animated, {
		Part = part,
		Base = part.CFrame,
		Phase = math.random() * math.pi * 2,
	})
end

for _, part in ipairs(CollectionService:GetTagged(CRYSTAL_TAG)) do
	watchCrystal(part)
end
CollectionService:GetInstanceAddedSignal(CRYSTAL_TAG):Connect(watchCrystal)

local elapsed = 0
RunService.RenderStepped:Connect(function(deltaTime)
	elapsed = elapsed + deltaTime
	for i = 1, #animated do
		local entry = animated[i]
		local part = entry.Part
		if part.Parent then
			local bob = math.sin(elapsed * 1.4 + entry.Phase) * 0.8
			part.CFrame = entry.Base
				* CFrame.new(0, bob, 0)
				* CFrame.Angles(0, elapsed * 0.8 + entry.Phase, 0)
		end
	end
end)
