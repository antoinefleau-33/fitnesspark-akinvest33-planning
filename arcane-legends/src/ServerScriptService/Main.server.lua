--[[
	Main.server.lua (Script - ServerScriptService)
	Point d'entree serveur unique d'Arcane Legends.
	Construit la map puis initialise tous les services avec un registre
	partage : AUCUN module serveur ne require un autre module serveur,
	ce qui rend toute dependance circulaire impossible.
	Lua 5.1 pur.
]]

local ServerScriptService = game:GetService("ServerScriptService")

local DataManager = require(ServerScriptService:WaitForChild("DataManager"))
local Economy = require(ServerScriptService:WaitForChild("Economy"))
local MapBuilder = require(ServerScriptService:WaitForChild("MapBuilder"))
local TrainingService = require(ServerScriptService:WaitForChild("TrainingService"))
local ZoneService = require(ServerScriptService:WaitForChild("ZoneService"))
local PetService = require(ServerScriptService:WaitForChild("PetService"))
local RebirthService = require(ServerScriptService:WaitForChild("RebirthService"))
local CombatService = require(ServerScriptService:WaitForChild("CombatService"))
local MonetizationService = require(ServerScriptService:WaitForChild("MonetizationService"))
local LeaderboardService = require(ServerScriptService:WaitForChild("LeaderboardService"))

-- 1) La map d'abord : les services branchent leurs interactions dessus
local mapHandles = MapBuilder.Build()

-- 2) Registre partage
local services = {
	DataManager = DataManager,
	Economy = Economy,
	TrainingService = TrainingService,
	ZoneService = ZoneService,
	PetService = PetService,
	RebirthService = RebirthService,
	CombatService = CombatService,
	Monetization = MonetizationService,
	LeaderboardService = LeaderboardService,
	MapHandles = mapHandles,
}

-- 3) Initialisation (le registre est complet avant le premier Init)
MonetizationService.Init(services)
Economy.Init(services)
DataManager.Init(services)
PetService.Init(services)
RebirthService.Init(services)
TrainingService.Init(services)
ZoneService.Init(services)
CombatService.Init(services)
LeaderboardService.Init(services)

print("[Arcane Legends] Serveur initialise : que la magie commence !")
