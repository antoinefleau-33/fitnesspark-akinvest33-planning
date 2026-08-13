--[[
	test_logic.lua — Tests hors-Roblox (lua5.1) des parties pures du jeu.
	Execute : lua5.1 tests/test_logic.lua   (depuis arcane-legends/)
	Couvre : Config (coherence), Util (formatNumber, weightedRoll, clamp,
	deepCopy, isInsideRect), formules economiques (rebirth, degats PvP,
	multiplicateur familiers).
]]

package.path = "./src/ReplicatedStorage/?.lua;" .. package.path

-- Stub minimal de l'API Roblox utilisee par Util (toColor3 non testee ici)
Color3 = { fromRGB = function(r, g, b) return { R = r, G = g, B = b } end }

local Config = require("Config")
local Util = require("Util")

local failures = 0
local function check(name, condition, detail)
	if condition then
		print("OK   " .. name)
	else
		failures = failures + 1
		print("FAIL " .. name .. (detail and (" -> " .. tostring(detail)) or ""))
	end
end

----------------------------------------------------------------
-- Config : coherence generale
----------------------------------------------------------------

check("5 zones definies", Config.ZoneCount == 5 and Config.Zones[5] ~= nil)
check("couts de zones spec (0/250/5k/100k/2M)",
	Config.Zones[1].Cost == 0 and Config.Zones[2].Cost == 250 and
	Config.Zones[3].Cost == 5000 and Config.Zones[4].Cost == 100000 and
	Config.Zones[5].Cost == 2000000)
check("multiplicateurs de zones spec (1/5/25/125/625)",
	Config.Zones[1].Multiplier == 1 and Config.Zones[2].Multiplier == 5 and
	Config.Zones[3].Multiplier == 25 and Config.Zones[4].Multiplier == 125 and
	Config.Zones[5].Multiplier == 625)
check("zone VIP x150", Config.VIPZone.Multiplier == 150)
check("couts des oeufs spec (50/500/5000)",
	Config.Eggs[1].Cost == 50 and Config.Eggs[2].Cost == 500 and Config.Eggs[3].Cost == 5000)

for i = 1, #Config.Eggs do
	local egg = Config.Eggs[i]
	local allValid = true
	for petId, weight in pairs(egg.Pool) do
		if not Config.Pets[petId] or weight <= 0 then
			allValid = false
		end
	end
	check("pool de l'oeuf " .. egg.Id .. " valide", allValid)
end

local multOk = true
for petId, def in pairs(Config.Pets) do
	if def.Multiplier < 1.2 or def.Multiplier > 3.5 or not Config.Rarities[def.Rarity] then
		multOk = false
	end
end
check("familiers : mult dans [1.2, 3.5] et rarete connue", multOk)
check("Seigneur du Vide x3.5 mythique",
	Config.Pets.seigneurvide.Multiplier == 3.5 and Config.Pets.seigneurvide.Rarity == "Mythique")
check("5 gamepasses avec TODO id=0",
	Config.GamePasses.DoubleMana.Id == 0 and Config.GamePasses.VoidLord.Id == 0)
check("gemmes duel = 25", Config.Gems.DuelWin == 25)
check("chance gemmes = 5 %", Config.Training.GemChance == 0.05)

----------------------------------------------------------------
-- Util.formatNumber
----------------------------------------------------------------

check("format 0", Util.formatNumber(0) == "0", Util.formatNumber(0))
check("format 999", Util.formatNumber(999) == "999", Util.formatNumber(999))
check("format 1000", Util.formatNumber(1000) == "1.0k", Util.formatNumber(1000))
check("format 250", Util.formatNumber(250) == "250", Util.formatNumber(250))
check("format 5000", Util.formatNumber(5000) == "5.0k", Util.formatNumber(5000))
check("format 100000", Util.formatNumber(100000) == "100k", Util.formatNumber(100000))
check("format 2000000", Util.formatNumber(2000000) == "2.0M", Util.formatNumber(2000000))
check("format 3.4e9", Util.formatNumber(3400000000) == "3.4Md", Util.formatNumber(3400000000))
check("format negatif", Util.formatNumber(-1500) == "-1.5k", Util.formatNumber(-1500))

----------------------------------------------------------------
-- Util.weightedRoll : distribution et bornes
----------------------------------------------------------------

math.randomseed(42)
local counts = {}
local pool = Config.Eggs[1].Pool
for i = 1, 20000 do
	local id = Util.weightedRoll(pool)
	counts[id] = (counts[id] or 0) + 1
end
local totalWeight = 0
for _, w in pairs(pool) do totalWeight = totalWeight + w end
local distribOk = true
for petId, weight in pairs(pool) do
	local expected = 20000 * weight / totalWeight
	local got = counts[petId] or 0
	-- tolerance large (5 sigma approx) : on verifie l'ordre de grandeur
	if got < expected * 0.6 - 20 or got > expected * 1.4 + 20 then
		distribOk = false
		print("     distribution " .. petId .. " : attendu ~" .. math.floor(expected) .. ", obtenu " .. got)
	end
end
check("weightedRoll suit les poids (20k tirages)", distribOk)
check("weightedRoll pool vide -> nil", Util.weightedRoll({}) == nil)
check("weightedRoll poids unique", Util.weightedRoll({ solo = 10 }) == "solo")

----------------------------------------------------------------
-- Util.clamp / deepCopy / dictCount / isInsideRect
----------------------------------------------------------------

check("clamp bas", Util.clamp(-5, 0, 10) == 0)
check("clamp haut", Util.clamp(50, 0, 10) == 10)
check("clamp milieu", Util.clamp(7, 0, 10) == 7)

local original = { a = 1, nested = { b = { c = 2 } } }
local copy = Util.deepCopy(original)
copy.nested.b.c = 99
check("deepCopy independant", original.nested.b.c == 2)
check("dictCount", Util.dictCount({ x = 1, y = 2, z = 3 }) == 3)

check("isInsideRect dedans", Util.isInsideRect({ X = 190, Z = 40 }, 190, 40, 80, 80))
check("isInsideRect bord", Util.isInsideRect({ X = 230, Z = 40 }, 190, 40, 80, 80))
check("isInsideRect dehors", not Util.isInsideRect({ X = 231, Z = 40 }, 190, 40, 80, 80))

----------------------------------------------------------------
-- Formules economiques (reproduites depuis les services)
----------------------------------------------------------------

-- Cout d'ascension : 10 000 x 4^n
local function rebirthCost(n)
	return Config.Rebirth.BaseCost * (Config.Rebirth.CostFactor ^ n)
end
check("cout ascension 0 = 10k", rebirthCost(0) == 10000)
check("cout ascension 1 = 40k", rebirthCost(1) == 40000)
check("cout ascension 3 = 640k", rebirthCost(3) == 640000)

-- Degats PvP : clamp(5 + mana^0.35, 5, 50)
local function damage(mana)
	return Util.clamp(Config.Combat.MinDamage + mana ^ Config.Combat.ManaExponent,
		Config.Combat.MinDamage, Config.Combat.MaxDamage)
end
check("degats mana 0 = 5", damage(0) == 5)
check("degats croissants", damage(1000) > damage(100))
check("degats plafonnes a 50", damage(1e15) == 50)
check("degats mana 100k ~ 5+56 -> 50", damage(100000) == 50)
check("degats mana 1000 ~ 16.2", math.abs(damage(1000) - (5 + 1000 ^ 0.35)) < 1e-9)

-- Multiplicateur familiers : 1 + somme des (mult - 1)
local function petMult(mults)
	local total = 1
	for i = 1, #mults do
		total = total + (mults[i] - 1)
	end
	return total
end
check("mult familiers vide = 1", petMult({}) == 1)
check("mult 3 legendaires x3.2 = 7.6", math.abs(petMult({ 3.2, 3.2, 3.2 }) - 7.6) < 1e-9)
check("mult x1.2 seul = 1.2", math.abs(petMult({ 1.2 }) - 1.2) < 1e-9)

-- Gain de canalisation type : base 1 * zone * pets * rebirth * pass
-- zone 5 (x625) * familiers 1+2.5+2.2+2.2 (=7.9) * 10 ascensions (x6) * pass (x2)
local gain = 1 * Config.Zones[5].Multiplier * petMult({ 3.5, 3.2, 3.2 }) * (1 + 0.5 * 10) * 2
check("gain endgame coherent (= 59 250)", math.floor(gain + 0.5) == 59250, gain)

----------------------------------------------------------------

print("")
if failures == 0 then
	print("TOUS LES TESTS PASSENT")
	os.exit(0)
else
	print(failures .. " TEST(S) EN ECHEC")
	os.exit(1)
end
