--[[
	Remotes.lua (ModuleScript - ReplicatedStorage)
	Point UNIQUE de definition des RemoteEvents / RemoteFunctions.
	Le serveur les cree, le client les attend : aucune desynchronisation possible.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local FOLDER_NAME = "ArcaneRemotes"

-- nom -> classe
local DEFINITIONS = {
	-- Serveur -> Client
	Notify         = "RemoteEvent",   -- (message: string, kind: string)
	ProfileChanged = "RemoteEvent",   -- (snapshot: table)
	ChannelResult  = "RemoteEvent",   -- (crystalPosition: Vector3, mana: number, gems: number)

	-- Client -> Serveur
	CastSpell      = "RemoteEvent",   -- (targetPlayer: Player)
	ToggleAuto     = "RemoteEvent",   -- (enabled: boolean)

	-- Client -> Serveur (avec reponse)
	GetProfile     = "RemoteFunction", -- () -> snapshot
	BuyEgg         = "RemoteFunction", -- (eggIndex: number) -> ok, petInfoOuMessage
	SetPetEquipped = "RemoteFunction", -- (petUid: string, equipped: boolean) -> ok, message
	DoRebirth      = "RemoteFunction", -- () -> ok, message
}

local Remotes = {}
local cache = nil

local function buildServer()
	local folder = ReplicatedStorage:FindFirstChild(FOLDER_NAME)
	if not folder then
		folder = Instance.new("Folder")
		folder.Name = FOLDER_NAME
		folder.Parent = ReplicatedStorage
	end
	local result = {}
	for name, className in pairs(DEFINITIONS) do
		local remote = folder:FindFirstChild(name)
		if not remote then
			remote = Instance.new(className)
			remote.Name = name
			remote.Parent = folder
		end
		result[name] = remote
	end
	return result
end

local function buildClient()
	local folder = ReplicatedStorage:WaitForChild(FOLDER_NAME)
	local result = {}
	for name, _ in pairs(DEFINITIONS) do
		result[name] = folder:WaitForChild(name)
	end
	return result
end

-- Retourne la table { nom -> instance remote } (creee au premier appel)
function Remotes.get()
	if cache then
		return cache
	end
	if RunService:IsServer() then
		cache = buildServer()
	else
		cache = buildClient()
	end
	return cache
end

return Remotes
