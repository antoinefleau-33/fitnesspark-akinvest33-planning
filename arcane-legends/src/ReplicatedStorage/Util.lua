--[[
	Util.lua (ModuleScript - ReplicatedStorage)
	Fonctions utilitaires partagees serveur/client. Lua 5.1 pur.
]]

local Util = {}

-- Formate un nombre en notation abregee francaise (1.2k, 3.4M, ...)
function Util.formatNumber(n)
	n = tonumber(n) or 0
	local neg = n < 0
	n = math.abs(n)
	local suffixes = { { 1e12, "T" }, { 1e9, "Md" }, { 1e6, "M" }, { 1e3, "k" } }
	local result
	if n < 1000 then
		result = tostring(math.floor(n + 0.5))
	else
		result = tostring(n)
		for i = 1, #suffixes do
			local threshold = suffixes[i][1]
			local suffix = suffixes[i][2]
			if n >= threshold then
				local value = n / threshold
				if value >= 100 then
					result = string.format("%d%s", math.floor(value), suffix)
				else
					result = string.format("%.1f%s", math.floor(value * 10) / 10, suffix)
				end
				break
			end
		end
	end
	if neg then
		return "-" .. result
	end
	return result
end

-- Copie profonde d'une table (sans metatables, suffisant pour les profils)
function Util.deepCopy(original)
	if type(original) ~= "table" then
		return original
	end
	local copy = {}
	for key, value in pairs(original) do
		copy[key] = Util.deepCopy(value)
	end
	return copy
end

-- Tirage pondere : pool = { cle = poids }, rng = Random.new() cote serveur.
-- Retourne la cle tiree (ou nil si pool vide).
function Util.weightedRoll(pool, rng)
	local total = 0
	for _, weight in pairs(pool) do
		total = total + weight
	end
	if total <= 0 then
		return nil
	end
	local roll
	if rng then
		roll = rng:NextNumber() * total
	else
		roll = math.random() * total
	end
	local cumulative = 0
	local lastKey = nil
	for key, weight in pairs(pool) do
		cumulative = cumulative + weight
		lastKey = key
		if roll <= cumulative then
			return key
		end
	end
	return lastKey -- garde-fou arrondi flottant
end

-- clamp compatible Lua 5.1 (math.clamp est du Luau)
function Util.clamp(value, minValue, maxValue)
	if value < minValue then
		return minValue
	end
	if value > maxValue then
		return maxValue
	end
	return value
end

-- Convertit {r, g, b} (Config) en Color3
function Util.toColor3(rgb)
	return Color3.fromRGB(rgb[1], rgb[2], rgb[3])
end

-- Compte les elements d'un dictionnaire
function Util.dictCount(dict)
	local count = 0
	for _ in pairs(dict) do
		count = count + 1
	end
	return count
end

-- Retourne la HumanoidRootPart d'un joueur, ou nil (nil-safety centralisee)
function Util.getRootPart(player)
	if not player then
		return nil
	end
	local character = player.Character
	if not character then
		return nil
	end
	return character:FindFirstChild("HumanoidRootPart")
end

-- Retourne l'Humanoid vivant d'un joueur, ou nil
function Util.getAliveHumanoid(player)
	if not player then
		return nil
	end
	local character = player.Character
	if not character then
		return nil
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid and humanoid.Health > 0 then
		return humanoid
	end
	return nil
end

-- Teste si une position est dans un rectangle horizontal centre en (cx, cz)
function Util.isInsideRect(position, cx, cz, sizeX, sizeZ)
	local dx = math.abs(position.X - cx)
	local dz = math.abs(position.Z - cz)
	return dx <= sizeX / 2 and dz <= sizeZ / 2
end

return Util
