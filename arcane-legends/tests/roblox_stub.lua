--[[
	roblox_stub.lua — Mini-implementation de l'API Roblox en Lua 5.1 pur,
	juste assez fidele pour executer les services serveur du jeu hors Studio
	(tests d'integration reels dans tests/test_services.lua).
	Ce fichier ne fait PAS partie du jeu : outillage de test uniquement.
]]

local Stub = {}

----------------------------------------------------------------
-- Horloge controlable (pour tester les cooldowns)
----------------------------------------------------------------

local fakeNow = 1000
function Stub.advanceClock(seconds)
	fakeNow = fakeNow + seconds
end
os.clock = function()
	return fakeNow
end

----------------------------------------------------------------
-- Signaux
----------------------------------------------------------------

local function newSignal()
	local signal = { _handlers = {} }
	function signal:Connect(handler)
		table.insert(self._handlers, handler)
		return { Disconnect = function() end }
	end
	function signal:Fire(...)
		for _, handler in ipairs(self._handlers) do
			handler(...)
		end
	end
	function signal:Wait()
		return
	end
	return signal
end
Stub.newSignal = newSignal

----------------------------------------------------------------
-- Vector3 / autres types valeur
----------------------------------------------------------------

local Vector3Meta = {}
Vector3Meta.__index = function(v, key)
	if key == "Magnitude" then
		return math.sqrt(v.X * v.X + v.Y * v.Y + v.Z * v.Z)
	end
	if key == "Unit" then
		local m = v.Magnitude
		if m == 0 then m = 1 end
		return Vector3.new(v.X / m, v.Y / m, v.Z / m)
	end
	return rawget(Vector3Meta, key)
end
Vector3Meta.__add = function(a, b) return Vector3.new(a.X + b.X, a.Y + b.Y, a.Z + b.Z) end
Vector3Meta.__sub = function(a, b) return Vector3.new(a.X - b.X, a.Y - b.Y, a.Z - b.Z) end
Vector3Meta.__mul = function(a, b)
	if type(b) == "number" then return Vector3.new(a.X * b, a.Y * b, a.Z * b) end
	return Vector3.new(a * b.X, a * b.Y, a * b.Z)
end

Vector3 = {}
function Vector3.new(x, y, z)
	return setmetatable({ X = x or 0, Y = y or 0, Z = z or 0 }, Vector3Meta)
end

Vector2 = { new = function(x, y) return { X = x or 0, Y = y or 0 } end }

local cfMeta = { __mul = function(a) return a end }
CFrame = {}
function CFrame.new(a, b, c)
	if type(a) == "table" then
		return setmetatable({ Position = a, __cf = true }, cfMeta)
	end
	return setmetatable({ Position = Vector3.new(a, b, c), __cf = true }, cfMeta)
end
function CFrame.Angles() return CFrame.new(0, 0, 0) end

Color3 = {
	fromRGB = function(r, g, b) return { R = r, G = g, B = b, __c3 = true } end,
	new = function(r, g, b) return { R = r, G = g, B = b, __c3 = true } end,
}
ColorSequence = { new = function(c) return { c } end }
NumberRange = { new = function(a, b) return { Min = a, Max = b or a } end }
NumberSequence = { new = function(v) return { v } end }
UDim = { new = function(s, o) return { Scale = s, Offset = o } end }
UDim2 = { new = function(...) return { ... } end }

-- Enum : n'importe quel Enum.X.Y rend un jeton unique et stable
local enumCache = {}
local function enumToken(path)
	if not enumCache[path] then
		enumCache[path] = setmetatable({ __enum = path }, {
			__index = function(_, key)
				return enumToken(path .. "." .. tostring(key))
			end,
			__tostring = function() return path end,
		})
	end
	return enumCache[path]
end
Enum = enumToken("Enum")

Random = {}
function Random.new(seed)
	local generator = { _seed = seed }
	function generator:NextNumber()
		return math.random()
	end
	function generator:NextInteger(minValue, maxValue)
		return math.random(minValue, maxValue)
	end
	return generator
end

----------------------------------------------------------------
-- Instances (mini-DOM)
----------------------------------------------------------------

local instanceMethods = {}

function instanceMethods:FindFirstChild(name)
	for _, child in ipairs(self._children) do
		if child.Name == name then
			return child
		end
	end
	return nil
end

function instanceMethods:WaitForChild(name)
	return self:FindFirstChild(name)
end

function instanceMethods:FindFirstChildOfClass(className)
	for _, child in ipairs(self._children) do
		if child.ClassName == className then
			return child
		end
	end
	return nil
end

function instanceMethods:GetChildren()
	local copy = {}
	for i, child in ipairs(self._children) do
		copy[i] = child
	end
	return copy
end

function instanceMethods:IsA(className)
	return self.ClassName == className
end

function instanceMethods:Destroy()
	if self._parent then
		for i, child in ipairs(self._parent._children) do
			if child == self then
				table.remove(self._parent._children, i)
				break
			end
		end
	end
	self._parent = nil
end

function instanceMethods:SetAttribute(key, value)
	self._attributes[key] = value
end

function instanceMethods:GetAttribute(key)
	return self._attributes[key]
end

function instanceMethods:GetPropertyChangedSignal()
	return newSignal()
end

local function newInstance(className)
	local inst = {
		ClassName = className,
		Name = className,
		_children = {},
		_attributes = {},
		_parent = nil,
		__isInstance = true,
	}
	-- signaux usuels
	inst.Touched = newSignal()
	inst.Triggered = newSignal()
	inst.MouseClick = newSignal()
	inst.Activated = newSignal()
	inst.Died = newSignal()
	inst.CharacterAdded = newSignal()
	inst.Completed = newSignal()

	return setmetatable(inst, {
		__index = function(t, key)
			if key == "Parent" then
				return rawget(t, "_parent")
			end
			return instanceMethods[key]
		end,
		__newindex = function(t, key, value)
			if key == "Parent" then
				local old = rawget(t, "_parent")
				if old then
					for i, child in ipairs(old._children) do
						if child == t then
							table.remove(old._children, i)
							break
						end
					end
				end
				rawset(t, "_parent", value)
				if value then
					table.insert(value._children, t)
				end
				return
			end
			rawset(t, key, value)
		end,
	})
end

Instance = { new = newInstance }
Stub.newInstance = newInstance

typeof = function(value)
	if type(value) == "table" then
		if rawget(value, "__isInstance") then
			return "Instance"
		end
	end
	return type(value)
end

warn = function(...)
	if Stub.verboseWarn then
		print("[warn]", ...)
	end
end

----------------------------------------------------------------
-- task
----------------------------------------------------------------

-- task.spawn execute la fonction dans une coroutine : les boucles infinies
-- des services (autosave, auto-canalisation...) se garent proprement au
-- premier task.wait (yield) au lieu de bloquer les tests, tandis que le
-- code lineaire (sauvegardes...) s'execute reellement.
Stub.parked = {}    -- coroutines suspendues sur task.wait
Stub.delayed = {}   -- fonctions passees a task.delay (non executees)

task = {
	wait = function(duration)
		if coroutine.running() then
			coroutine.yield()
			return duration or 0
		end
		-- thread principal : le temps factice avance pour que les boucles
		-- bornees par os.clock() se terminent
		fakeNow = fakeNow + math.max(duration or 0.03, 0.03)
		return duration or 0
	end,
	spawn = function(fn, ...)
		local thread = coroutine.create(fn)
		local ok, err = coroutine.resume(thread, ...)
		if not ok then
			print("[stub task.spawn] erreur :", err)
		end
		if coroutine.status(thread) == "suspended" then
			table.insert(Stub.parked, thread)
		end
	end,
	defer = function(fn, ...)
		task.spawn(fn, ...)
	end,
	delay = function(_, fn)
		table.insert(Stub.delayed, fn)
	end,
}

----------------------------------------------------------------
-- Services
----------------------------------------------------------------

local services = {}

local function makeServiceInstance(name)
	local service = newInstance(name)
	service.Name = name
	return service
end

-- ReplicatedStorage : WaitForChild(nom) -> marqueur de module reel
local moduleMarkers = {}
local replicatedStorage = makeServiceInstance("ReplicatedStorage")
do
	local realWait = replicatedStorage.WaitForChild
	rawset(replicatedStorage, "WaitForChild", function(self, name)
		if not moduleMarkers[name] then
			moduleMarkers[name] = { __module = name }
		end
		return moduleMarkers[name]
	end)
end

-- require : accepte les marqueurs de modules partages
local nativeRequire = require
require = function(target)
	if type(target) == "table" and target.__module then
		return nativeRequire(target.__module)
	end
	return nativeRequire(target)
end

services.ReplicatedStorage = replicatedStorage
services.Workspace = makeServiceInstance("Workspace")
services.Lighting = makeServiceInstance("Lighting")
services.ServerScriptService = makeServiceInstance("ServerScriptService")

services.RunService = {
	IsServer = function() return true end,
	IsClient = function() return false end,
	IsStudio = function() return false end,
	Heartbeat = newSignal(),
	RenderStepped = newSignal(),
}

services.CollectionService = {
	AddTag = function() end,
	GetTagged = function() return {} end,
	GetInstanceAddedSignal = function() return newSignal() end,
}

local playersService = {
	PlayerAdded = newSignal(),
	PlayerRemoving = newSignal(),
	_players = {},
}
function playersService:GetPlayers()
	local list = {}
	for _, player in ipairs(self._players) do
		table.insert(list, player)
	end
	return list
end
function playersService:GetPlayerFromCharacter(character)
	for _, player in ipairs(self._players) do
		if player.Character == character then
			return player
		end
	end
	return nil
end
function playersService:GetPlayerByUserId(userId)
	for _, player in ipairs(self._players) do
		if player.UserId == userId then
			return player
		end
	end
	return nil
end
function playersService:GetNameFromUserIdAsync(userId)
	return "Sorcier" .. tostring(userId)
end
services.Players = playersService
Stub.Players = playersService

-- DataStoreService : magasins configurables par les tests
Stub.dataStores = {}
services.DataStoreService = {
	GetDataStore = function(_, name)
		if not Stub.dataStores[name] then
			Stub.dataStores[name] = Stub.makeDataStore()
		end
		return Stub.dataStores[name]
	end,
	GetOrderedDataStore = function(_, name)
		if not Stub.dataStores[name] then
			Stub.dataStores[name] = Stub.makeDataStore()
		end
		return Stub.dataStores[name]
	end,
}

-- Magasin memoire avec pannes simulables
function Stub.makeDataStore()
	local store = { data = {}, failGets = 0, failSets = 0, getCalls = 0, setCalls = 0 }
	function store:GetAsync(key)
		store.getCalls = store.getCalls + 1
		if store.failGets > 0 then
			store.failGets = store.failGets - 1
			error("DataStore indisponible (panne simulee)")
		end
		return store.data[key]
	end
	function store:UpdateAsync(key, transform)
		store.setCalls = store.setCalls + 1
		if store.failSets > 0 then
			store.failSets = store.failSets - 1
			error("DataStore indisponible (panne simulee)")
		end
		store.data[key] = transform(store.data[key])
		return store.data[key]
	end
	function store:SetAsync(key, value)
		store.setCalls = store.setCalls + 1
		if store.failSets > 0 then
			store.failSets = store.failSets - 1
			error("DataStore indisponible (panne simulee)")
		end
		store.data[key] = value
	end
	function store:GetSortedAsync()
		local entries = {}
		for key, value in pairs(store.data) do
			table.insert(entries, { key = key, value = value })
		end
		table.sort(entries, function(a, b) return a.value > b.value end)
		return { GetCurrentPage = function() return entries end }
	end
	return store
end

services.MarketplaceService = {
	UserOwnsGamePassAsync = function() return false end,
	PromptGamePassPurchase = function() end,
	PromptProductPurchase = function() end,
	PromptGamePassPurchaseFinished = newSignal(),
}
Stub.MarketplaceService = services.MarketplaceService

services.TweenService = {
	Create = function()
		return { Play = function() end, Completed = newSignal() }
	end,
}
services.UserInputService = {
	InputBegan = newSignal(),
	TouchTapInWorld = newSignal(),
}

game = {
	GetService = function(_, name)
		local service = services[name]
		if not service then
			service = makeServiceInstance(name)
			services[name] = service
		end
		return service
	end,
	BindToClose = function(_, callback)
		Stub.bindToClose = callback
	end,
}

----------------------------------------------------------------
-- Fabriques d'objets de test
----------------------------------------------------------------

-- Cree un joueur factice (avec personnage positionne)
function Stub.makePlayer(name, userId, position)
	local player = newInstance("Player")
	player.Name = name
	player.UserId = userId
	player.Parent = nil
	rawset(player, "_parent", playersService) -- present "dans" Players

	local character = newInstance("Model")
	character.Name = name

	local rootPart = newInstance("Part")
	rootPart.Name = "HumanoidRootPart"
	rootPart.Position = position or Vector3.new(0, 3, 0)
	rootPart.CFrame = CFrame.new(rootPart.Position)
	rootPart.Parent = character

	local humanoid = newInstance("Humanoid")
	humanoid.Health = 100
	humanoid.MaxHealth = 100
	function humanoid:TakeDamage(amount)
		humanoid.Health = humanoid.Health - amount
		humanoid.lastDamage = amount
		if humanoid.Health <= 0 then
			humanoid.Died:Fire()
		end
	end
	humanoid.Parent = character

	player.Character = character
	table.insert(playersService._players, player)
	return player
end

function Stub.removePlayer(player)
	for i, current in ipairs(playersService._players) do
		if current == player then
			table.remove(playersService._players, i)
			break
		end
	end
	playersService.PlayerRemoving:Fire(player)
	rawset(player, "_parent", nil)
end

-- Faux module Remotes : chaque remote enregistre ce qui lui arrive
function Stub.makeFakeRemotes()
	local names = {
		"Notify", "ProfileChanged", "ChannelResult", "CastSpell", "ToggleAuto",
		"GetProfile", "BuyEgg", "SetPetEquipped", "DoRebirth",
	}
	local remotes = {}
	for _, name in ipairs(names) do
		remotes[name] = {
			fired = {},
			OnServerEvent = newSignal(),
			FireClient = function(self, player, ...)
				table.insert(self.fired, { player = player, args = { ... } })
			end,
			FireAllClients = function(self, ...)
				table.insert(self.fired, { args = { ... } })
			end,
		}
	end
	return { get = function() return remotes end }, remotes
end

return Stub
