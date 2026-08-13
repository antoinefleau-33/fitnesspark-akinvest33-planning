--[[
	Config.lua (ModuleScript - ReplicatedStorage)
	TOUT l'equilibrage du jeu "Arcane Legends" est centralise ici.
	Partage entre serveur et client (le client ne s'en sert que pour l'affichage).
	Lua 5.1 pur.
]]

local Config = {}

----------------------------------------------------------------
-- GENERAL
----------------------------------------------------------------
Config.GameName = "Arcane Legends"

----------------------------------------------------------------
-- CANALISATION (gain de mana sur les cristaux)
----------------------------------------------------------------
Config.Training = {
	BaseManaPerChannel = 1,     -- gain de base avant multiplicateurs
	ChannelRange = 14,          -- distance max joueur <-> cristal (studs), validee serveur
	ChannelCooldown = 0.35,     -- anti-spam serveur (secondes)
	AutoInterval = 0.6,         -- intervalle de l'auto-canalisation (gamepass)
	GemChance = 0.05,           -- 5 % de chance de gemmes par canalisation
	GemMin = 1,
	GemMax = 3,
}

----------------------------------------------------------------
-- ZONES (id numerique croissant ; cle string dans les données persistees)
----------------------------------------------------------------
Config.Zones = {
	[1] = { Name = "Prairie Arcanique",  Cost = 0,       Multiplier = 1,   Color = {124, 200, 108}, CrystalColor = {170, 255, 140} },
	[2] = { Name = "Forêt Enchantée",    Cost = 250,     Multiplier = 5,   Color = {52, 122, 82},   CrystalColor = {90, 255, 190}  },
	[3] = { Name = "Cryptes Oubliées",   Cost = 5000,    Multiplier = 25,  Color = {110, 108, 128}, CrystalColor = {180, 160, 255} },
	[4] = { Name = "Volcan Runique",     Cost = 100000,  Multiplier = 125, Color = {130, 60, 44},   CrystalColor = {255, 120, 60}  },
	[5] = { Name = "Dimension Astrale",  Cost = 2000000, Multiplier = 625, Color = {70, 60, 140},   CrystalColor = {150, 200, 255} },
}
Config.ZoneCount = 5
Config.CrystalsPerZone = 6

-- Zone VIP (gamepass Sanctuaire VIP)
Config.VIPZone = {
	Name = "Sanctuaire VIP",
	Multiplier = 150,
	Color = {240, 200, 70},
	CrystalColor = {255, 230, 120},
	CrystalCount = 8,
}

----------------------------------------------------------------
-- DISPOSITION DE LA MAP (tout est construit par script)
----------------------------------------------------------------
Config.Map = {
	LobbySize = { X = 110, Z = 110 },            -- lobby centre en (0, 0)
	ZoneSize = { X = 90, Z = 90 },
	ZoneSpacing = 110,                            -- ecart entre centres de zones
	ZoneRowZ = -150,                              -- les 5 zones alignees au nord du lobby
	ArenaCenter = { X = 190, Z = 40 },
	ArenaSize = { X = 80, Z = 80 },
	VIPCenter = { X = -190, Z = 40 },
	VIPSize = { X = 70, Z = 70 },
	VIPHeight = 40,                               -- ile flottante
	LeaderboardPos = { X = 0, Z = 48 },           -- panneau au sud du lobby
}

----------------------------------------------------------------
-- GEMMES (monnaie secondaire)
----------------------------------------------------------------
Config.Gems = {
	DuelWin = 25, -- gemmes gagnées par victoire en duel
}

----------------------------------------------------------------
-- FAMILIERS
----------------------------------------------------------------
Config.Rarities = {
	Commun     = { Display = "Commun",     Color = {180, 180, 180}, Order = 1 },
	Rare       = { Display = "Rare",       Color = {80, 150, 255},  Order = 2 },
	Epique     = { Display = "Épique",     Color = {180, 90, 255},  Order = 3 },
	Legendaire = { Display = "Légendaire", Color = {255, 190, 60},  Order = 4 },
	Mythique   = { Display = "Mythique",   Color = {255, 70, 90},   Order = 5 },
}

-- Definitions des familiers (id string -> stats)
Config.Pets = {
	-- Oeuf Novice
	lueur        = { Name = "Lueur",              Rarity = "Commun",     Multiplier = 1.2 },
	feufollet    = { Name = "Feu Follet",         Rarity = "Commun",     Multiplier = 1.25 },
	salamandre   = { Name = "Salamandre Bleue",   Rarity = "Rare",       Multiplier = 1.4 },
	golem        = { Name = "Golem Runique",      Rarity = "Epique",     Multiplier = 1.7 },
	phenix       = { Name = "Phénix Mineur",      Rarity = "Legendaire", Multiplier = 2.2 },
	-- Oeuf Mystique
	sylvestre    = { Name = "Esprit Sylvestre",   Rarity = "Commun",     Multiplier = 1.3 },
	gardien      = { Name = "Gardien de Cristal", Rarity = "Rare",       Multiplier = 1.5 },
	chimere      = { Name = "Chimère d'Onyx",     Rarity = "Epique",     Multiplier = 1.9 },
	dragon       = { Name = "Dragon d'Améthyste", Rarity = "Legendaire", Multiplier = 2.6 },
	-- Oeuf Celeste
	djinn        = { Name = "Djinn des Sables",   Rarity = "Rare",       Multiplier = 1.7 },
	licorne      = { Name = "Licorne Astrale",    Rarity = "Epique",     Multiplier = 2.2 },
	titan        = { Name = "Titan Stellaire",    Rarity = "Legendaire", Multiplier = 3.2 },
	-- Exclusifs monetisation
	apprentineant = { Name = "Apprenti du Néant", Rarity = "Epique",     Multiplier = 1.6 },  -- Pack de Depart
	seigneurvide  = { Name = "Seigneur du Vide",  Rarity = "Mythique",   Multiplier = 3.5 },  -- Gamepass
}

-- Oeufs achetables en gemmes (tirage pondere COTE SERVEUR)
Config.Eggs = {
	[1] = {
		Id = "novice", Name = "Œuf Novice", Cost = 50,
		Pool = { lueur = 50, feufollet = 30, salamandre = 15, golem = 4, phenix = 1 },
	},
	[2] = {
		Id = "mystique", Name = "Œuf Mystique", Cost = 500,
		Pool = { sylvestre = 45, gardien = 35, chimere = 15, dragon = 5 },
	},
	[3] = {
		Id = "celeste", Name = "Œuf Céleste", Cost = 5000,
		Pool = { djinn = 50, licorne = 35, titan = 15 },
	},
}

Config.MaxEquippedPets = 3

----------------------------------------------------------------
-- ASCENSIONS (rebirth)
----------------------------------------------------------------
Config.Rebirth = {
	BaseCost = 10000,      -- cout = BaseCost * CostFactor ^ nbAscensions
	CostFactor = 4,
	BonusPerRebirth = 0.5, -- +50 % de mana par ascension (permanent)
	AuraTiers = { 1, 3, 6, 10 }, -- paliers d'auras de particules
}

----------------------------------------------------------------
-- PVP (arene)
----------------------------------------------------------------
Config.Combat = {
	Range = 45,          -- portee max du sort (studs), validee serveur
	Cooldown = 0.6,      -- cooldown par joueur (secondes), valide serveur
	MinDamage = 5,
	MaxDamage = 50,
	ManaExponent = 0.35, -- degats = clamp(5 + mana^0.35, 5, 50)
	ProjectileSpeed = 90,
}

----------------------------------------------------------------
-- MONETISATION (IDs a creer sur create.roblox.com puis a renseigner ici)
----------------------------------------------------------------
Config.GamePasses = {
	DoubleMana  = { Id = 0, Name = "x2 Mana",            Desc = "Double toute la mana canalisée." },        -- TODO_UTILISATEUR : ID du gamepass
	DoubleGems  = { Id = 0, Name = "x2 Gemmes",          Desc = "Double toutes les gemmes gagnées." },      -- TODO_UTILISATEUR : ID du gamepass
	AutoTrain   = { Id = 0, Name = "Auto-Canalisation",  Desc = "Canalise automatiquement les cristaux proches." }, -- TODO_UTILISATEUR : ID du gamepass
	VIPZone     = { Id = 0, Name = "Sanctuaire VIP",     Desc = "Accès au Sanctuaire VIP et ses cristaux x150." },  -- TODO_UTILISATEUR : ID du gamepass
	VoidLord    = { Id = 0, Name = "Seigneur du Vide",   Desc = "Familier mythique exclusif x3.5." },       -- TODO_UTILISATEUR : ID du gamepass
}

Config.Products = {
	StarterPack = { Id = 0, Name = "Pack de Départ", Desc = "UNIQUE : 5000 mana + 200 gemmes + familier exclusif.", Mana = 5000, Gems = 200, Pet = "apprentineant", Unique = true }, -- TODO_UTILISATEUR : ID du developer product
	Gems100     = { Id = 0, Name = "100 Gemmes",     Desc = "Un petit sac de gemmes.",  Gems = 100 },  -- TODO_UTILISATEUR : ID du developer product
	Gems1200    = { Id = 0, Name = "1200 Gemmes",    Desc = "Un coffre de gemmes.",     Gems = 1200 }, -- TODO_UTILISATEUR : ID du developer product
}

----------------------------------------------------------------
-- DONNEES (DataStore)
----------------------------------------------------------------
Config.Data = {
	StoreName = "ArcaneLegends_Profil_v1",
	OrderedStoreName = "ArcaneLegends_ManaTotale_v1",
	AutosaveInterval = 120, -- secondes
	MaxRetries = 3,
	RetryBaseDelay = 2,     -- backoff exponentiel : 2 s, 4 s, 8 s
}

----------------------------------------------------------------
-- CLASSEMENT
----------------------------------------------------------------
Config.Leaderboard = {
	RefreshInterval = 60, -- secondes
	TopN = 10,
}

return Config
