--[[
	test_services.lua — Tests d'integration REELS des services serveur,
	executes hors Roblox grace a tests/roblox_stub.lua.
	Execute : lua5.1 tests/test_services.lua   (depuis arcane-legends/)

	Le vrai code de DataManager, Economy, PetService, RebirthService,
	TrainingService, ZoneService, CombatService et MonetizationService
	tourne ici sans modification.
]]

package.path = "./src/ReplicatedStorage/?.lua;./src/ServerScriptService/?.lua;./tests/?.lua;" .. package.path

local Stub = require("roblox_stub")

-- Remplace le module Remotes par des faux enregistreurs AVANT tout require
local fakeRemotesModule, R = Stub.makeFakeRemotes()
package.loaded["Remotes"] = fakeRemotesModule

local Config = require("Config")
local Util = require("Util")
local DataManager = require("DataManager")
local Economy = require("Economy")
local PetService = require("PetService")
local RebirthService = require("RebirthService")
local TrainingService = require("TrainingService")
local ZoneService = require("ZoneService")
local CombatService = require("CombatService")
local MonetizationService = require("MonetizationService")

local failures = 0
local function check(name, condition, detail)
	if condition then
		print("OK   " .. name)
	else
		failures = failures + 1
		print("FAIL " .. name .. (detail and (" -> " .. tostring(detail)) or ""))
	end
end

local function lastNotifyFor(player)
	for i = #R.Notify.fired, 1, -1 do
		if R.Notify.fired[i].player == player then
			return R.Notify.fired[i].args[1], R.Notify.fired[i].args[2]
		end
	end
	return nil
end

----------------------------------------------------------------
-- Monetization factice controlable (registre principal)
----------------------------------------------------------------

local ownedPasses = {} -- [player] = { passKey = true }
local FakeMonetization = {
	OwnsPass = function(player, passKey)
		return ownedPasses[player] ~= nil and ownedPasses[player][passKey] == true
	end,
}
local function grantPass(player, passKey)
	ownedPasses[player] = ownedPasses[player] or {}
	ownedPasses[player][passKey] = true
end

----------------------------------------------------------------
-- Fausse map (handles minimaux, memes formes que MapBuilder)
----------------------------------------------------------------

local crystalsFolder = Instance.new("Folder")
crystalsFolder.Name = "Cristaux"

local crystals = {}
local function makeCrystal(zoneId, crystalId, position)
	local crystal = Instance.new("Part")
	crystal.Name = "Cristal_" .. tostring(zoneId) .. "_" .. tostring(crystalId)
	crystal.Position = position
	crystal:SetAttribute("ZoneId", zoneId)
	crystal:SetAttribute("CrystalId", crystalId)
	local prompt = Instance.new("ProximityPrompt")
	prompt.Name = "PromptCanaliser"
	prompt.Parent = crystal
	local clicker = Instance.new("ClickDetector")
	clicker.Name = "ClicCanaliser"
	clicker.Parent = crystal
	crystal.Parent = crystalsFolder
	crystals[zoneId] = crystal
	return crystal
end

-- un cristal par zone (1..5) + un cristal VIP (zone 0)
makeCrystal(1, 1, Vector3.new(0, 5, -150))
makeCrystal(2, 1, Vector3.new(-110, 5, -150))
makeCrystal(3, 1, Vector3.new(110, 5, -150))
makeCrystal(4, 1, Vector3.new(-220, 5, -150))
makeCrystal(5, 1, Vector3.new(220, 5, -150))
makeCrystal(0, 1, Vector3.new(-190, 45, 40))

local totems = {}
for zoneId = 2, 5 do
	local prompt = Instance.new("ProximityPrompt")
	prompt.Name = "PromptAchatZone"
	totems[zoneId] = { Prompt = prompt, Orb = Instance.new("Part") }
end

local padTo = Instance.new("Part")
local padBack = Instance.new("Part")

local mapHandles = {
	CrystalsFolder = crystalsFolder,
	Totems = totems,
	Arena = { CenterX = 190, CenterZ = 40, SizeX = 80, SizeZ = 80 },
	VIP = { PadTo = padTo, PadBack = padBack, TopY = 41, CenterX = -190, CenterZ = 40 },
	Leaderboard = { Rows = Instance.new("Frame") },
}

----------------------------------------------------------------
-- Joueurs de test (crees AVANT les Init pour les hooks directs)
----------------------------------------------------------------

local alice = Stub.makePlayer("Alice", 111, Vector3.new(0, 3, -150))    -- pres du cristal zone 1
local bob = Stub.makePlayer("Bob", 222, Vector3.new(190, 3, 40))        -- dans l'arene

----------------------------------------------------------------
-- Initialisation (miroir de Main.server.lua)
----------------------------------------------------------------

local services = {
	DataManager = DataManager,
	Economy = Economy,
	TrainingService = TrainingService,
	ZoneService = ZoneService,
	PetService = PetService,
	RebirthService = RebirthService,
	CombatService = CombatService,
	Monetization = FakeMonetization,
	MapHandles = mapHandles,
}

Economy.Init(services)
DataManager.Init(services)
PetService.Init(services)
RebirthService.Init(services)
TrainingService.Init(services)
ZoneService.Init(services)
CombatService.Init(services)

-- Le vrai MonetizationService est teste a part (ProcessReceipt), avec le
-- meme DataManager/Economy/PetService
local monetizationRegistry = {
	DataManager = DataManager,
	Economy = Economy,
	PetService = PetService,
	Monetization = FakeMonetization,
}
MonetizationService.Init(monetizationRegistry)
local processReceipt = Stub.MarketplaceService.ProcessReceipt
check("ProcessReceipt installe sur MarketplaceService", type(processReceipt) == "function")

local profileStore = Stub.dataStores[Config.Data.StoreName]
check("DataStore du profil cree", profileStore ~= nil)

----------------------------------------------------------------
-- 1. Chargement de profil : nouveau joueur
----------------------------------------------------------------

Stub.Players.PlayerAdded:Fire(alice)
local aliceData = DataManager.GetProfile(alice)
check("nouveau profil charge", aliceData ~= nil)
check("nouveau profil : mana 0, zone 1 possedee",
	aliceData.Mana == 0 and aliceData.Zones["1"] == true)
check("session sauvegardable", DataManager.CanSave(alice) == true)
check("leaderstats crees", alice:FindFirstChild("leaderstats") ~= nil)
check("snapshot pousse au client", #R.ProfileChanged.fired > 0)

----------------------------------------------------------------
-- 2. Chargement : reconcile d'un profil incomplet
----------------------------------------------------------------

profileStore.data["Joueur_333"] = { Mana = 5, TotalMana = 5 } -- profil ancien/partiel
local carol = Stub.makePlayer("Carol", 333, Vector3.new(0, 3, 0))
Stub.Players.PlayerAdded:Fire(carol)
local carolData = DataManager.GetProfile(carol)
check("reconcile : mana conservee", carolData.Mana == 5)
check("reconcile : cles manquantes ajoutees",
	carolData.Zones ~= nil and carolData.Zones["1"] == true and
	carolData.Purchases ~= nil and carolData.PetCounter == 0)

----------------------------------------------------------------
-- 3. Chargement en panne : session temporaire JAMAIS sauvegardee
----------------------------------------------------------------

profileStore.failGets = 10 -- plus que MaxRetries : le chargement echoue
local dave = Stub.makePlayer("Dave", 444, Vector3.new(0, 3, 0))
Stub.Players.PlayerAdded:Fire(dave)
profileStore.failGets = 0
local daveData = DataManager.GetProfile(dave)
check("session temporaire : profil de secours charge", daveData ~= nil)
check("session temporaire : CanSave = false", DataManager.CanSave(dave) == false)
local message = lastNotifyFor(dave)
check("session temporaire : joueur prevenu",
	message ~= nil and string.find(message, "temporaire") ~= nil, message)
local setCallsBefore = profileStore.setCalls
DataManager.SaveProfile(dave)
check("session temporaire : sauvegarde REFUSEE", profileStore.setCalls == setCallsBefore)

----------------------------------------------------------------
-- 4. Canalisation (TrainingService)
----------------------------------------------------------------

local channelOk = TrainingService.Channel(alice, crystals[1])
check("canalisation zone 1 acceptee", channelOk == true)
check("gain zone 1 = 1 mana", aliceData.Mana == 1, aliceData.Mana)
check("retour visuel envoye", #R.ChannelResult.fired > 0)

local blocked = TrainingService.Channel(alice, crystals[1])
check("cooldown serveur bloque le spam", blocked == false)
Stub.advanceClock(1)
TrainingService.Channel(alice, crystals[1])
check("apres cooldown : nouveau gain", aliceData.Mana == 2, aliceData.Mana)

-- pres du cristal de la zone 2 (non achetee) : c'est bien le verrou de zone
-- qui doit refuser, pas la distance
alice.Character:FindFirstChild("HumanoidRootPart").Position = Vector3.new(-110, 3, -150)
Stub.advanceClock(1)
local lockedGain = TrainingService.Channel(alice, crystals[2])
check("zone non achetee : gain refuse", lockedGain == false)
local lockedMessage = lastNotifyFor(alice)
check("zone non achetee : notification", string.find(lockedMessage or "", "verrouill") ~= nil)
alice.Character:FindFirstChild("HumanoidRootPart").Position = Vector3.new(0, 3, -150)

Stub.advanceClock(1)
local farAway = TrainingService.Channel(alice, crystals[5]) -- a ~220 studs
check("distance excessive : refuse (anti-triche)", farAway == false)

Stub.advanceClock(1)
local vipDenied = TrainingService.Channel(alice, crystals[0])
check("cristal VIP sans gamepass : refuse", vipDenied == false)

----------------------------------------------------------------
-- 5. Achat de zones (ZoneService, via totems)
----------------------------------------------------------------

aliceData.Mana = 300
totems[2].Prompt.Triggered:Fire(alice)
check("zone 2 achetee (cle string)", aliceData.Zones["2"] == true)
check("cout deduit : 300 - 250 = 50", aliceData.Mana == 50, aliceData.Mana)

totems[4].Prompt.Triggered:Fire(alice)
check("progression lineaire : zone 4 refusee sans zone 3", aliceData.Zones["4"] == nil)

aliceData.Mana = 100
totems[3].Prompt.Triggered:Fire(alice)
check("fonds insuffisants : zone 3 refusee", aliceData.Zones["3"] == nil and aliceData.Mana == 100)

-- gain multiplie apres achat
Stub.advanceClock(1)
alice.Character:FindFirstChild("HumanoidRootPart").Position = Vector3.new(-110, 3, -150)
aliceData.Mana = 0
TrainingService.Channel(alice, crystals[2])
check("gain zone 2 = x5", aliceData.Mana == 5, aliceData.Mana)

----------------------------------------------------------------
-- 6. Familiers (PetService)
----------------------------------------------------------------

local buyEgg = R.BuyEgg.OnServerInvoke
local ok, result = buyEgg(alice, 1)
check("oeuf sans gemmes : refuse", ok == false)

aliceData.Gems = 5000
local okBuy, pet = buyEgg(alice, 1)
check("achat d'oeuf accepte", okBuy == true and type(pet) == "table")
check("gemmes deduites (5000 - 50)", aliceData.Gems == 4950, aliceData.Gems)
check("familier au pool de l'oeuf 1", Config.Eggs[1].Pool[pet.Id] ~= nil, pet.Id)
check("uid string (contrainte JSON)", type(pet.Uid) == "string")
check("premier familier auto-equipe", aliceData.Pets[pet.Uid].Equipped == true)

local badEgg = select(1, buyEgg(alice, 99))
check("oeuf inexistant : refuse", badEgg == false)
local badType = select(1, buyEgg(alice, "novice"))
check("index non numerique : refuse", badType == false)

-- limite d'equipement : 3 max
buyEgg(alice, 1)
buyEgg(alice, 1)
buyEgg(alice, 1) -- 4 familiers, 3 equipes
local equipped = 0
local unequippedUid = nil
for uid, entry in pairs(aliceData.Pets) do
	if entry.Equipped then
		equipped = equipped + 1
	else
		unequippedUid = uid
	end
end
check("maximum 3 equipes", equipped == 3 and unequippedUid ~= nil)

local setEquipped = R.SetPetEquipped.OnServerInvoke
local okEquip = select(1, setEquipped(alice, unequippedUid, true))
check("equiper un 4e familier : refuse", okEquip == false)
local okUnequip = select(1, setEquipped(alice, unequippedUid, false))
check("retirer un familier deja retire : accepte (idempotent)", okUnequip == true)

-- multiplicateur familiers = 1 + somme(mult - 1)
local expectedMult = 1
for _, entry in pairs(aliceData.Pets) do
	if entry.Equipped then
		expectedMult = expectedMult + (Config.Pets[entry.Id].Multiplier - 1)
	end
end
check("Economy.GetPetMultiplier conforme",
	math.abs(Economy.GetPetMultiplier(alice) - expectedMult) < 1e-9)

----------------------------------------------------------------
-- 7. Ascension (RebirthService)
----------------------------------------------------------------

local doRebirth = R.DoRebirth.OnServerInvoke
aliceData.Mana = 9999
local okPoor = select(1, doRebirth(alice))
check("ascension sans fonds : refusee", okPoor == false and aliceData.Rebirths == 0)

aliceData.Mana = 250000 -- bien plus que le cout (10 000)
local okRebirth = select(1, doRebirth(alice))
check("ascension acceptee", okRebirth == true)
check("TOUTE la mana sacrifiee", aliceData.Mana == 0, aliceData.Mana)
check("compteur d'ascensions = 1", aliceData.Rebirths == 1)
check("multiplicateur ascension = 1.5", math.abs(Economy.GetRebirthMultiplier(alice) - 1.5) < 1e-9)
check("cout suivant = 40k", Economy.GetRebirthCost(alice) == 40000)
check("aura appliquee au palier 1",
	alice.Character:FindFirstChild("HumanoidRootPart"):FindFirstChild("AuraAscension") ~= nil)

----------------------------------------------------------------
-- 8. PvP (CombatService)
----------------------------------------------------------------

local castSpell = R.CastSpell.OnServerEvent
local bobHumanoid = bob.Character:FindFirstChildOfClass("Humanoid")
Stub.Players.PlayerAdded:Fire(bob)
local bobData = DataManager.GetProfile(bob)

-- Alice hors arene -> refus
Stub.advanceClock(5)
castSpell:Fire(alice, bob)
check("sort refuse : attaquant hors arene", bobHumanoid.Health == 100)

-- Alice entre dans l'arene
alice.Character:FindFirstChild("HumanoidRootPart").Position = Vector3.new(200, 3, 45)
local aliceRoot = alice.Character:FindFirstChild("HumanoidRootPart")
aliceData.Mana = 1000
castSpell:Fire(alice, bob)
local expectedDamage = Util.clamp(5 + 1000 ^ 0.35, 5, 50)
check("degats = clamp(5 + mana^0.35, 5, 50)",
	math.abs((bobHumanoid.lastDamage or 0) - expectedDamage) < 1e-9,
	tostring(bobHumanoid.lastDamage))

castSpell:Fire(alice, bob)
check("cooldown PvP 0,6 s respecte", math.abs(bobHumanoid.Health - (100 - expectedDamage)) < 1e-9)

-- auto-attaque interdite
Stub.advanceClock(1)
castSpell:Fire(alice, alice)
local aliceHumanoid = alice.Character:FindFirstChildOfClass("Humanoid")
check("auto-attaque impossible", aliceHumanoid.Health == 100)

-- victoire : Bob tombe, Alice gagne 25 gemmes
local gemsBefore = aliceData.Gems
Stub.advanceClock(1)
bobHumanoid.Health = 1
castSpell:Fire(alice, bob)
check("vainqueur credite de 25 gemmes", aliceData.Gems == gemsBefore + Config.Gems.DuelWin,
	tostring(aliceData.Gems - gemsBefore))

-- portee : au-dela de 45 studs dans l'arene
Stub.advanceClock(1)
bobHumanoid.Health = 100
aliceRoot.Position = Vector3.new(155, 3, 5)
bob.Character:FindFirstChild("HumanoidRootPart").Position = Vector3.new(225, 3, 75) -- ~99 studs
castSpell:Fire(alice, bob)
check("sort refuse au-dela de 45 studs", bobHumanoid.Health == 100)

----------------------------------------------------------------
-- 9. Auto-canalisation (gamepass)
----------------------------------------------------------------

R.ToggleAuto.OnServerEvent:Fire(alice, true)
check("auto sans gamepass : refuse", aliceData.AutoTrain == false)
grantPass(alice, "AutoTrain")
R.ToggleAuto.OnServerEvent:Fire(alice, true)
check("auto avec gamepass : active", aliceData.AutoTrain == true)

-- gamepass x2 mana
grantPass(alice, "DoubleMana")
check("multiplicateur pass x2 actif", Economy.GetPassManaMultiplier(alice) == 2)

-- cristal VIP avec gamepass
grantPass(alice, "VIPZone")
aliceRoot.Position = Vector3.new(-190, 43, 40)
aliceData.Mana = 0
Stub.advanceClock(1)
TrainingService.Channel(alice, crystals[0])
-- meme ordre de calcul que TrainingService (l'associativite flottante compte
-- aux frontieres exactes de .5) : base * zone * multiplicateur global
local vipExpected = math.floor(1 * Config.VIPZone.Multiplier
	* Economy.GetGlobalMultiplier(alice) + 0.5)
check("multiplicateur global = familiers x 1.5 x 2",
	math.abs(Economy.GetGlobalMultiplier(alice)
		- Economy.GetPetMultiplier(alice) * 1.5 * 2) < 1e-9)
check("gain VIP = 150 x familiers x ascension x pass",
	aliceData.Mana == vipExpected, tostring(aliceData.Mana) .. " vs " .. tostring(vipExpected))

----------------------------------------------------------------
-- 10. ProcessReceipt idempotent (MonetizationService reel)
----------------------------------------------------------------

Config.Products.Gems100.Id = 91001
Config.Products.StarterPack.Id = 91002

local gemsBeforeBuy = aliceData.Gems
local decision = processReceipt({ PlayerId = 111, ProductId = 91001, PurchaseId = "ACHAT-1" })
check("achat 100 gemmes accorde", tostring(decision) == tostring(Enum.ProductPurchaseDecision.PurchaseGranted))
check("100 gemmes creditees SANS pass x2 (achat)", aliceData.Gems == gemsBeforeBuy + 100)

decision = processReceipt({ PlayerId = 111, ProductId = 91001, PurchaseId = "ACHAT-1" })
check("meme PurchaseId rejoue : accorde sans double credit",
	tostring(decision) == tostring(Enum.ProductPurchaseDecision.PurchaseGranted)
	and aliceData.Gems == gemsBeforeBuy + 100)
check("PurchaseId historise en cle string", aliceData.Purchases["ACHAT-1"] == true)

local manaBefore = aliceData.Mana
local totalManaBefore = aliceData.TotalMana
local petCountBefore = Util.dictCount(aliceData.Pets)
decision = processReceipt({ PlayerId = 111, ProductId = 91002, PurchaseId = "ACHAT-2" })
check("pack de depart accorde",
	tostring(decision) == tostring(Enum.ProductPurchaseDecision.PurchaseGranted))
check("pack : +5000 mana, +200 gemmes, +1 familier exclusif",
	aliceData.Mana == manaBefore + 5000
	and aliceData.Gems == gemsBeforeBuy + 300
	and Util.dictCount(aliceData.Pets) == petCountBefore + 1
	and aliceData.StarterPackOwned == true)
check("mana ACHETEE exclue du classement (TotalMana inchangee)",
	aliceData.TotalMana == totalManaBefore, aliceData.TotalMana - totalManaBefore)

decision = processReceipt({ PlayerId = 111, ProductId = 91002, PurchaseId = "ACHAT-3" })
check("pack duplique : mana/gemmes credites mais PAS de 2e familier",
	Util.dictCount(aliceData.Pets) == petCountBefore + 1)

decision = processReceipt({ PlayerId = 999999, ProductId = 91001, PurchaseId = "ACHAT-4" })
check("joueur absent : NotProcessedYet",
	tostring(decision) == tostring(Enum.ProductPurchaseDecision.NotProcessedYet))

decision = processReceipt({ PlayerId = 444, ProductId = 91001, PurchaseId = "ACHAT-5" })
check("session temporaire : NotProcessedYet (achat non consomme)",
	tostring(decision) == tostring(Enum.ProductPurchaseDecision.NotProcessedYet))

Config.Products.Gems100.Id = 0
Config.Products.StarterPack.Id = 0

----------------------------------------------------------------
-- 11. Sauvegarde / rechargement (round-trip complet)
----------------------------------------------------------------

aliceData.Mana = 12345
local savedPets = Util.dictCount(aliceData.Pets)
Stub.removePlayer(alice) -- PlayerRemoving -> sauvegarde
local storedProfile = profileStore.data["Joueur_111"]
check("profil ecrit a la deconnexion", storedProfile ~= nil and storedProfile.Mana == 12345)
check("profil en memoire libere", DataManager.GetProfile(alice) == nil)

local alice2 = Stub.makePlayer("Alice", 111, Vector3.new(0, 3, 0))
Stub.Players.PlayerAdded:Fire(alice2)
local reloaded = DataManager.GetProfile(alice2)
check("rechargement : mana persistee", reloaded ~= nil and reloaded.Mana == 12345)
check("rechargement : zones (cles string) persistees", reloaded.Zones["2"] == true)
check("rechargement : familiers persistes", Util.dictCount(reloaded.Pets) == savedPets)
check("rechargement : achats persistes", reloaded.Purchases["ACHAT-1"] == true)
check("rechargement : ascensions persistees", reloaded.Rebirths == 1)

-- sauvegarde avec panne DataStore : retry puis abandon propre (pas de crash)
profileStore.failSets = 10
local savedOk = DataManager.SaveProfile(alice2)
profileStore.failSets = 0
check("panne de sauvegarde : echec propre sans crash", savedOk == false)

-- BindToClose sauvegarde tout le monde
local callsBefore = profileStore.setCalls
Stub.bindToClose()
check("BindToClose declenche des sauvegardes", profileStore.setCalls > callsBefore)

----------------------------------------------------------------

print("")
if failures == 0 then
	print("TOUS LES TESTS D'INTEGRATION PASSENT")
	os.exit(0)
else
	print(failures .. " TEST(S) EN ECHEC")
	os.exit(1)
end
