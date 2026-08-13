--[[
	MapBuilder.lua (ModuleScript - ServerScriptService)
	Construit 100 % de la map par script (aucun asset a importer) :
	- lobby + spawn
	- 5 zones avec cristaux neon + totems d'achat (ProximityPrompt)
	- arene PvP fermee
	- Sanctuaire VIP flottant + pads de teleportation
	- panneau physique de classement (SurfaceGui)
	Retourne une table de "handles" que les autres services branchent.
	Lua 5.1 pur.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local CollectionService = game:GetService("CollectionService")
local Lighting = game:GetService("Lighting")
local Workspace = game:GetService("Workspace")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))

local MapBuilder = {}

local CRYSTAL_TAG = "ArcaneCrystal"

-- Materiaux d'ambiance par zone (presentation pure, pas d'equilibrage)
local ZONE_MATERIALS = {
	[1] = Enum.Material.Grass,
	[2] = Enum.Material.Grass,
	[3] = Enum.Material.Slate,
	[4] = Enum.Material.Basalt,
	[5] = Enum.Material.Glacier,
}

----------------------------------------------------------------
-- Petits constructeurs
----------------------------------------------------------------

local function newPart(props)
	local part = Instance.new("Part")
	part.Anchored = true
	part.TopSurface = Enum.SurfaceType.Smooth
	part.BottomSurface = Enum.SurfaceType.Smooth
	part.Material = props.Material or Enum.Material.SmoothPlastic
	part.Size = props.Size
	part.CFrame = props.CFrame
	if props.Color then
		part.Color = props.Color
	end
	if props.Shape then
		part.Shape = props.Shape
	end
	if props.Transparency then
		part.Transparency = props.Transparency
	end
	if props.CanCollide ~= nil then
		part.CanCollide = props.CanCollide
	end
	part.Name = props.Name or "Part"
	part.Parent = props.Parent
	return part
end

local function newBillboard(parent, text, textColor, size, offsetY)
	local billboard = Instance.new("BillboardGui")
	billboard.Name = "Etiquette"
	billboard.Size = size or UDim2.new(0, 220, 0, 60)
	billboard.StudsOffset = Vector3.new(0, offsetY or 4, 0)
	billboard.AlwaysOnTop = false
	billboard.MaxDistance = 160
	local label = Instance.new("TextLabel")
	label.Name = "Texte"
	label.Size = UDim2.new(1, 0, 1, 0)
	label.BackgroundTransparency = 1
	label.Font = Enum.Font.FredokaOne
	label.TextScaled = true
	label.Text = text
	label.TextColor3 = textColor or Color3.fromRGB(255, 255, 255)
	label.TextStrokeTransparency = 0.4
	label.Parent = billboard
	billboard.Parent = parent
	return billboard, label
end

----------------------------------------------------------------
-- Elements de jeu
----------------------------------------------------------------

-- Cristal magique canalisable. zoneId = 1..5, ou 0 pour le Sanctuaire VIP.
local function buildCrystal(parent, position, zoneId, crystalId, colorRgb)
	local color = Util.toColor3(colorRgb)

	local crystal = newPart({
		Name = "Cristal_" .. tostring(zoneId) .. "_" .. tostring(crystalId),
		Size = Vector3.new(3, 6, 3),
		CFrame = CFrame.new(position) * CFrame.Angles(math.rad(15), math.rad(crystalId * 40), math.rad(10)),
		Color = color,
		Material = Enum.Material.Neon,
		CanCollide = false,
		Parent = parent,
	})
	crystal:SetAttribute("ZoneId", zoneId)
	crystal:SetAttribute("CrystalId", crystalId)
	CollectionService:AddTag(crystal, CRYSTAL_TAG)

	local light = Instance.new("PointLight")
	light.Color = color
	light.Range = 14
	light.Brightness = 1.5
	light.Parent = crystal

	local sparkle = Instance.new("ParticleEmitter")
	sparkle.Name = "Etincelles"
	sparkle.Color = ColorSequence.new(color)
	sparkle.LightEmission = 1
	sparkle.Rate = 3
	sparkle.Lifetime = NumberRange.new(0.8, 1.4)
	sparkle.Speed = NumberRange.new(1, 2)
	sparkle.Size = NumberSequence.new(0.35)
	sparkle.Parent = crystal

	-- Socle en pierre
	newPart({
		Name = "Socle",
		Size = Vector3.new(4.5, 1, 4.5),
		CFrame = CFrame.new(position.X, position.Y - 3.5, position.Z),
		Color = Color3.fromRGB(90, 90, 100),
		Material = Enum.Material.Slate,
		Parent = parent,
	})

	-- Interaction : touche E a proximite (le clic/tap est gere par ClickDetector)
	local prompt = Instance.new("ProximityPrompt")
	prompt.Name = "PromptCanaliser"
	prompt.ActionText = "Canaliser"
	prompt.ObjectText = "Cristal magique"
	prompt.KeyboardKeyCode = Enum.KeyCode.E
	prompt.HoldDuration = 0
	prompt.MaxActivationDistance = Config.Training.ChannelRange
	prompt.RequiresLineOfSight = false
	prompt.ClickablePrompt = false -- le clic passe par le ClickDetector
	prompt.Parent = crystal

	local clicker = Instance.new("ClickDetector")
	clicker.Name = "ClicCanaliser"
	clicker.MaxActivationDistance = Config.Training.ChannelRange
	clicker.Parent = crystal

	return crystal
end

-- Anneau de cristaux autour d'un centre
local function buildCrystalRing(parent, centerX, topY, centerZ, count, radius, zoneId, colorRgb)
	local crystals = {}
	for i = 1, count do
		local angle = (i / count) * math.pi * 2
		local x = centerX + math.cos(angle) * radius
		local z = centerZ + math.sin(angle) * radius
		local crystal = buildCrystal(parent, Vector3.new(x, topY + 4, z), zoneId, i, colorRgb)
		table.insert(crystals, crystal)
	end
	return crystals
end

-- Totem d'achat de zone (ProximityPrompt), place a l'entree de la zone
local function buildTotem(parent, position, zoneId, zoneDef)
	local model = Instance.new("Model")
	model.Name = "Totem_Zone_" .. tostring(zoneId)

	local pillar = newPart({
		Name = "Pilier",
		Size = Vector3.new(3, 8, 3),
		CFrame = CFrame.new(position.X, position.Y + 4, position.Z),
		Color = Color3.fromRGB(60, 55, 75),
		Material = Enum.Material.Slate,
		Parent = model,
	})

	local orb = newPart({
		Name = "Orbe",
		Shape = Enum.PartType.Ball,
		Size = Vector3.new(3.4, 3.4, 3.4),
		CFrame = CFrame.new(position.X, position.Y + 10, position.Z),
		Color = Util.toColor3(zoneDef.CrystalColor),
		Material = Enum.Material.Neon,
		CanCollide = false,
		Parent = model,
	})
	local light = Instance.new("PointLight")
	light.Color = orb.Color
	light.Range = 12
	light.Parent = orb

	newBillboard(orb,
		zoneDef.Name .. "\n" .. Util.formatNumber(zoneDef.Cost) .. " Mana",
		Util.toColor3(zoneDef.CrystalColor),
		UDim2.new(0, 260, 0, 80), 3.5)

	local prompt = Instance.new("ProximityPrompt")
	prompt.Name = "PromptAchatZone"
	prompt.ActionText = "Débloquer (" .. Util.formatNumber(zoneDef.Cost) .. " Mana)"
	prompt.ObjectText = zoneDef.Name
	prompt.KeyboardKeyCode = Enum.KeyCode.F
	prompt.HoldDuration = 0.4
	prompt.MaxActivationDistance = 12
	prompt.RequiresLineOfSight = false
	prompt.Parent = pillar

	model:SetAttribute("ZoneId", zoneId)
	model.Parent = parent
	return { Model = model, Prompt = prompt, Orb = orb }
end

-- Pad de teleportation circulaire
local function buildPad(parent, position, labelText, color, name)
	local pad = newPart({
		Name = name,
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(1, 8, 8),
		CFrame = CFrame.new(position) * CFrame.Angles(0, 0, math.rad(90)),
		Color = color,
		Material = Enum.Material.Neon,
		Parent = parent,
	})
	newBillboard(pad, labelText, color, UDim2.new(0, 220, 0, 50), 5)
	return pad
end

----------------------------------------------------------------
-- Grandes structures
----------------------------------------------------------------

local function buildLobby(root)
	local map = Config.Map
	newPart({
		Name = "SolLobby",
		Size = Vector3.new(map.LobbySize.X, 2, map.LobbySize.Z),
		CFrame = CFrame.new(0, 0, 0),
		Color = Color3.fromRGB(196, 186, 230),
		Material = Enum.Material.Marble,
		Parent = root,
	})

	local spawn = Instance.new("SpawnLocation")
	spawn.Name = "SpawnLobby"
	spawn.Size = Vector3.new(10, 1, 10)
	spawn.CFrame = CFrame.new(0, 1.5, 20)
	spawn.Anchored = true
	spawn.Neutral = true
	spawn.Duration = 0
	spawn.Color = Color3.fromRGB(150, 120, 220)
	spawn.Material = Enum.Material.Neon
	spawn.TopSurface = Enum.SurfaceType.Smooth
	spawn.Parent = root

	-- Fontaine centrale decorative
	local fountain = newPart({
		Name = "FontaineArcane",
		Shape = Enum.PartType.Ball,
		Size = Vector3.new(6, 6, 6),
		CFrame = CFrame.new(0, 6, -10),
		Color = Color3.fromRGB(120, 170, 255),
		Material = Enum.Material.ForceField,
		CanCollide = false,
		Parent = root,
	})
	local emitter = Instance.new("ParticleEmitter")
	emitter.Color = ColorSequence.new(Color3.fromRGB(150, 190, 255))
	emitter.LightEmission = 1
	emitter.Rate = 8
	emitter.Lifetime = NumberRange.new(1, 2)
	emitter.Speed = NumberRange.new(2, 4)
	emitter.Size = NumberSequence.new(0.5)
	emitter.Parent = fountain
	newPart({
		Name = "SocleFontaine",
		Size = Vector3.new(8, 3, 8),
		CFrame = CFrame.new(0, 2.5, -10),
		Color = Color3.fromRGB(160, 150, 200),
		Material = Enum.Material.Marble,
		Parent = root,
	})
	newBillboard(fountain, "Arcane Legends", Color3.fromRGB(200, 180, 255), UDim2.new(0, 300, 0, 70), 6)
end

local function buildZones(root)
	local map = Config.Map
	local handles = { Totems = {}, ZoneTops = {} }
	local crystalsFolder = Instance.new("Folder")
	crystalsFolder.Name = "Cristaux"
	crystalsFolder.Parent = root

	for zoneId = 1, Config.ZoneCount do
		local def = Config.Zones[zoneId]
		local centerX = (zoneId - 3) * map.ZoneSpacing
		local centerZ = map.ZoneRowZ
		local topY = 1

		local zoneFolder = Instance.new("Folder")
		zoneFolder.Name = "Zone_" .. tostring(zoneId)
		zoneFolder.Parent = root

		newPart({
			Name = "Sol",
			Size = Vector3.new(map.ZoneSize.X, 2, map.ZoneSize.Z),
			CFrame = CFrame.new(centerX, 0, centerZ),
			Color = Util.toColor3(def.Color),
			Material = ZONE_MATERIALS[zoneId] or Enum.Material.Grass,
			Parent = zoneFolder,
		})

		-- Panneau du nom au centre de la zone
		local marker = newPart({
			Name = "Marqueur",
			Size = Vector3.new(1, 1, 1),
			CFrame = CFrame.new(centerX, topY + 14, centerZ),
			Transparency = 1,
			CanCollide = false,
			Parent = zoneFolder,
		})
		newBillboard(marker, def.Name .. "  (x" .. tostring(def.Multiplier) .. ")",
			Util.toColor3(def.CrystalColor), UDim2.new(0, 300, 0, 60), 0)

		buildCrystalRing(crystalsFolder, centerX, topY, centerZ,
			Config.CrystalsPerZone, 28, zoneId, def.CrystalColor)

		-- Totem d'achat a l'entree (cote lobby), sauf pour la zone gratuite
		if def.Cost > 0 then
			local totemPos = Vector3.new(centerX, topY, centerZ + map.ZoneSize.Z / 2 - 6)
			handles.Totems[zoneId] = buildTotem(zoneFolder, totemPos, zoneId, def)
		end

		handles.ZoneTops[zoneId] = topY
	end

	handles.CrystalsFolder = crystalsFolder
	return handles
end

local function buildArena(root)
	local map = Config.Map
	local cx = map.ArenaCenter.X
	local cz = map.ArenaCenter.Z
	local sx = map.ArenaSize.X
	local sz = map.ArenaSize.Z

	local folder = Instance.new("Folder")
	folder.Name = "Arene"
	folder.Parent = root

	newPart({
		Name = "SolArene",
		Size = Vector3.new(sx, 2, sz),
		CFrame = CFrame.new(cx, 0, cz),
		Color = Color3.fromRGB(70, 45, 60),
		Material = Enum.Material.Cobblestone,
		Parent = folder,
	})

	local wallHeight = 12
	local wallThickness = 2
	-- Mur nord et mur sud (pleins)
	newPart({
		Name = "MurNord",
		Size = Vector3.new(sx + wallThickness * 2, wallHeight, wallThickness),
		CFrame = CFrame.new(cx, wallHeight / 2 + 1, cz - sz / 2 - wallThickness / 2),
		Color = Color3.fromRGB(55, 35, 50), Material = Enum.Material.Slate, Parent = folder,
	})
	newPart({
		Name = "MurSud",
		Size = Vector3.new(sx + wallThickness * 2, wallHeight, wallThickness),
		CFrame = CFrame.new(cx, wallHeight / 2 + 1, cz + sz / 2 + wallThickness / 2),
		Color = Color3.fromRGB(55, 35, 50), Material = Enum.Material.Slate, Parent = folder,
	})
	-- Mur est (plein)
	newPart({
		Name = "MurEst",
		Size = Vector3.new(wallThickness, wallHeight, sz),
		CFrame = CFrame.new(cx + sx / 2 + wallThickness / 2, wallHeight / 2 + 1, cz),
		Color = Color3.fromRGB(55, 35, 50), Material = Enum.Material.Slate, Parent = folder,
	})
	-- Mur ouest avec ouverture centrale de 14 studs (entree cote lobby)
	local gap = 14
	local segment = (sz - gap) / 2
	newPart({
		Name = "MurOuestA",
		Size = Vector3.new(wallThickness, wallHeight, segment),
		CFrame = CFrame.new(cx - sx / 2 - wallThickness / 2, wallHeight / 2 + 1, cz - gap / 2 - segment / 2),
		Color = Color3.fromRGB(55, 35, 50), Material = Enum.Material.Slate, Parent = folder,
	})
	newPart({
		Name = "MurOuestB",
		Size = Vector3.new(wallThickness, wallHeight, segment),
		CFrame = CFrame.new(cx - sx / 2 - wallThickness / 2, wallHeight / 2 + 1, cz + gap / 2 + segment / 2),
		Color = Color3.fromRGB(55, 35, 50), Material = Enum.Material.Slate, Parent = folder,
	})

	local sign = newPart({
		Name = "MarqueurArene",
		Size = Vector3.new(1, 1, 1),
		CFrame = CFrame.new(cx, 18, cz),
		Transparency = 1,
		CanCollide = false,
		Parent = folder,
	})
	newBillboard(sign, "Arène des Duels\nClique sur un adversaire pour lancer un sort !",
		Color3.fromRGB(255, 100, 120), UDim2.new(0, 320, 0, 80), 0)

	return { CenterX = cx, CenterZ = cz, SizeX = sx, SizeZ = sz }
end

local function buildVIP(root)
	local map = Config.Map
	local cx = map.VIPCenter.X
	local cz = map.VIPCenter.Z
	local topY = map.VIPHeight + 1

	local folder = Instance.new("Folder")
	folder.Name = "SanctuaireVIP"
	folder.Parent = root

	newPart({
		Name = "IleVIP",
		Size = Vector3.new(map.VIPSize.X, 2, map.VIPSize.Z),
		CFrame = CFrame.new(cx, map.VIPHeight, cz),
		Color = Util.toColor3(Config.VIPZone.Color),
		Material = Enum.Material.Marble,
		Parent = folder,
	})
	-- Bordure lumineuse
	newPart({
		Name = "BordureVIP",
		Size = Vector3.new(map.VIPSize.X + 2, 1, map.VIPSize.Z + 2),
		CFrame = CFrame.new(cx, map.VIPHeight - 1, cz),
		Color = Color3.fromRGB(255, 220, 120),
		Material = Enum.Material.Neon,
		Parent = folder,
	})

	local marker = newPart({
		Name = "MarqueurVIP",
		Size = Vector3.new(1, 1, 1),
		CFrame = CFrame.new(cx, topY + 16, cz),
		Transparency = 1,
		CanCollide = false,
		Parent = folder,
	})
	newBillboard(marker, Config.VIPZone.Name .. "  (x" .. tostring(Config.VIPZone.Multiplier) .. ")",
		Util.toColor3(Config.VIPZone.CrystalColor), UDim2.new(0, 300, 0, 60), 0)

	-- Cristaux VIP (zoneId 0)
	local crystalsFolder = root:FindFirstChild("Cristaux")
	buildCrystalRing(crystalsFolder or folder, cx, topY, cz,
		Config.VIPZone.CrystalCount, 22, 0, Config.VIPZone.CrystalColor)

	-- Pads de teleportation
	local padTo = buildPad(root, Vector3.new(45, 1.5, 45),
		"Sanctuaire VIP", Color3.fromRGB(255, 210, 90), "PadVersVIP")
	local padBack = buildPad(folder, Vector3.new(cx, topY + 0.5, cz + map.VIPSize.Z / 2 - 8),
		"Retour au lobby", Color3.fromRGB(180, 160, 255), "PadRetourLobby")

	return { PadTo = padTo, PadBack = padBack, TopY = topY, CenterX = cx, CenterZ = cz }
end

local function buildLeaderboardBoard(root)
	local map = Config.Map
	local pos = Vector3.new(map.LeaderboardPos.X, 9, map.LeaderboardPos.Z)

	local board = newPart({
		Name = "PanneauClassement",
		Size = Vector3.new(18, 14, 1.5),
		CFrame = CFrame.new(pos),
		Color = Color3.fromRGB(35, 30, 55),
		Material = Enum.Material.Slate,
		Parent = root,
	})
	newPart({
		Name = "PiedPanneau",
		Size = Vector3.new(18, 2, 4),
		CFrame = CFrame.new(pos.X, 1.5, pos.Z),
		Color = Color3.fromRGB(60, 50, 90),
		Material = Enum.Material.Slate,
		Parent = root,
	})

	local gui = Instance.new("SurfaceGui")
	gui.Name = "GuiClassement"
	gui.Face = Enum.NormalId.Front
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = 40
	gui.Parent = board

	local title = Instance.new("TextLabel")
	title.Name = "Titre"
	title.Size = UDim2.new(1, 0, 0.14, 0)
	title.BackgroundColor3 = Color3.fromRGB(90, 70, 160)
	title.BorderSizePixel = 0
	title.Font = Enum.Font.FredokaOne
	title.TextScaled = true
	title.TextColor3 = Color3.fromRGB(255, 255, 255)
	title.Text = "Top 10 — Mana totale"
	title.Parent = gui

	local rows = Instance.new("Frame")
	rows.Name = "Lignes"
	rows.Position = UDim2.new(0, 0, 0.14, 0)
	rows.Size = UDim2.new(1, 0, 0.86, 0)
	rows.BackgroundColor3 = Color3.fromRGB(25, 22, 40)
	rows.BorderSizePixel = 0
	rows.Parent = gui

	local layout = Instance.new("UIListLayout")
	layout.FillDirection = Enum.FillDirection.Vertical
	layout.SortOrder = Enum.SortOrder.LayoutOrder
	layout.Padding = UDim.new(0, 2)
	layout.Parent = rows

	-- Le panneau fait face au lobby (sud -> nord)
	board.CFrame = CFrame.new(pos) * CFrame.Angles(0, math.rad(180), 0)

	return { Board = board, Gui = gui, Rows = rows }
end

local function setupLighting()
	Lighting.ClockTime = 17.5
	Lighting.Brightness = 2
	Lighting.Ambient = Color3.fromRGB(90, 85, 120)
	Lighting.OutdoorAmbient = Color3.fromRGB(120, 110, 150)
	local atmosphere = Lighting:FindFirstChildOfClass("Atmosphere")
	if not atmosphere then
		atmosphere = Instance.new("Atmosphere")
		atmosphere.Parent = Lighting
	end
	atmosphere.Density = 0.35
	atmosphere.Color = Color3.fromRGB(180, 170, 220)
	atmosphere.Haze = 1.5
end

----------------------------------------------------------------
-- API
----------------------------------------------------------------

MapBuilder.CrystalTag = CRYSTAL_TAG

function MapBuilder.Build()
	local root = Instance.new("Folder")
	root.Name = "ArcaneMap"

	-- Grand sol de base sous tout le monde exterieur
	newPart({
		Name = "SolMonde",
		Size = Vector3.new(760, 4, 640),
		CFrame = CFrame.new(0, -2.5, -60),
		Color = Color3.fromRGB(88, 118, 92),
		Material = Enum.Material.Grass,
		Parent = root,
	})

	buildLobby(root)
	local zoneHandles = buildZones(root)
	local arenaRegion = buildArena(root)
	local vipHandles = buildVIP(root)
	local boardHandles = buildLeaderboardBoard(root)
	setupLighting()

	root.Parent = Workspace

	return {
		Root = root,
		CrystalsFolder = zoneHandles.CrystalsFolder,
		Totems = zoneHandles.Totems,
		Arena = arenaRegion,
		VIP = vipHandles,
		Leaderboard = boardHandles,
	}
end

return MapBuilder
