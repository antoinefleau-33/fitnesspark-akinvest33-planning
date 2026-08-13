--[[
	LeaderboardService.lua (ModuleScript - ServerScriptService)
	Classement global persistant top 10 sur la mana totale gagnee
	(OrderedDataStore), affiche sur le panneau physique construit par
	MapBuilder, rafraichi toutes les 60 s.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local DataStoreService = game:GetService("DataStoreService")
local Players = game:GetService("Players")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))

local LeaderboardService = {}

local services = nil
local orderedStore = nil

-- Cache des pseudos : [userId number] = string
local nameCache = {}

local ROW_COLORS = {
	Color3.fromRGB(255, 215, 90),  -- or
	Color3.fromRGB(200, 200, 210), -- argent
	Color3.fromRGB(205, 130, 70),  -- bronze
}

----------------------------------------------------------------
-- Ecriture des scores
----------------------------------------------------------------

local function publishScores()
	for _, player in ipairs(Players:GetPlayers()) do
		local data = services.DataManager.GetProfile(player)
		-- On ne publie jamais le score d'une session temporaire
		if data and services.DataManager.CanSave(player) then
			local total = math.floor(data.TotalMana)
			if total > 0 then
				pcall(function()
					orderedStore:SetAsync(tostring(player.UserId), total)
				end)
			end
		end
	end
end

----------------------------------------------------------------
-- Lecture + affichage
----------------------------------------------------------------

local function resolveName(userId)
	if nameCache[userId] then
		return nameCache[userId]
	end
	-- Joueur en ligne : gratuit
	local online = Players:GetPlayerByUserId(userId)
	if online then
		nameCache[userId] = online.Name
		return online.Name
	end
	local ok, name = pcall(function()
		return Players:GetNameFromUserIdAsync(userId)
	end)
	if ok and name then
		nameCache[userId] = name
		return name
	end
	return "Sorcier #" .. tostring(userId)
end

local function renderRows(entries)
	local rows = services.MapHandles.Leaderboard.Rows
	-- Purge des anciennes lignes (on garde le UIListLayout)
	for _, child in ipairs(rows:GetChildren()) do
		if child:IsA("Frame") then
			child:Destroy()
		end
	end

	for rank = 1, #entries do
		local entry = entries[rank]

		local row = Instance.new("Frame")
		row.Name = "Ligne" .. tostring(rank)
		row.Size = UDim2.new(1, 0, 0, 44)
		row.BackgroundColor3 = (rank % 2 == 0)
			and Color3.fromRGB(35, 30, 55)
			or Color3.fromRGB(45, 38, 70)
		row.BorderSizePixel = 0
		row.LayoutOrder = rank

		local rankLabel = Instance.new("TextLabel")
		rankLabel.Size = UDim2.new(0.14, 0, 1, 0)
		rankLabel.BackgroundTransparency = 1
		rankLabel.Font = Enum.Font.FredokaOne
		rankLabel.TextScaled = true
		rankLabel.Text = "#" .. tostring(rank)
		rankLabel.TextColor3 = ROW_COLORS[rank] or Color3.fromRGB(150, 145, 180)
		rankLabel.Parent = row

		local nameLabel = Instance.new("TextLabel")
		nameLabel.Position = UDim2.new(0.14, 0, 0, 0)
		nameLabel.Size = UDim2.new(0.5, 0, 1, 0)
		nameLabel.BackgroundTransparency = 1
		nameLabel.Font = Enum.Font.GothamBold
		nameLabel.TextScaled = true
		nameLabel.TextXAlignment = Enum.TextXAlignment.Left
		nameLabel.Text = entry.Name
		nameLabel.TextColor3 = Color3.fromRGB(235, 230, 255)
		nameLabel.Parent = row

		local scoreLabel = Instance.new("TextLabel")
		scoreLabel.Position = UDim2.new(0.64, 0, 0, 0)
		scoreLabel.Size = UDim2.new(0.34, 0, 1, 0)
		scoreLabel.BackgroundTransparency = 1
		scoreLabel.Font = Enum.Font.FredokaOne
		scoreLabel.TextScaled = true
		scoreLabel.TextXAlignment = Enum.TextXAlignment.Right
		scoreLabel.Text = Util.formatNumber(entry.Score) .. " mana"
		scoreLabel.TextColor3 = Color3.fromRGB(140, 220, 255)
		scoreLabel.Parent = row

		row.Parent = rows
	end
end

local function refreshBoard()
	local ok, pages = pcall(function()
		return orderedStore:GetSortedAsync(false, Config.Leaderboard.TopN)
	end)
	if not ok or not pages then
		warn("[Leaderboard] Lecture du classement impossible (nouvel essai dans " ..
			tostring(Config.Leaderboard.RefreshInterval) .. " s).")
		return
	end

	local entries = {}
	local page = pages:GetCurrentPage()
	for _, item in ipairs(page) do
		local userId = tonumber(item.key)
		if userId then
			table.insert(entries, {
				Name = resolveName(userId),
				Score = item.value,
			})
		end
	end
	renderRows(entries)
end

----------------------------------------------------------------
-- Init
----------------------------------------------------------------

function LeaderboardService.Init(registry)
	services = registry
	orderedStore = DataStoreService:GetOrderedDataStore(Config.Data.OrderedStoreName)

	task.spawn(function()
		task.wait(5) -- laisse les profils se charger au demarrage du serveur
		while true do
			publishScores()
			refreshBoard()
			task.wait(Config.Leaderboard.RefreshInterval)
		end
	end)
end

return LeaderboardService
