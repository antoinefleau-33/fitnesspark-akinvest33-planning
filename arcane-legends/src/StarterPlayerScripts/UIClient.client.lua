--[[
	UIClient.client.lua (LocalScript - StarterPlayer > StarterPlayerScripts)
	Interface joueur 100 % generee par script, en francais :
	- barre de stats (mana, gemmes, ascensions, multiplicateur)
	- boutique (gamepasses + developer products)
	- familiers (oeufs, inventaire, equipement)
	- ascension
	- bouton auto-canalisation
	- notifications animees + textes flottants de gain
	Le client AFFICHE et DEMANDE : toutes les regles sont validees serveur.
	Lua 5.1 pur (compatible Luau).
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")
local MarketplaceService = game:GetService("MarketplaceService")
local Workspace = game:GetService("Workspace")

local Config = require(ReplicatedStorage:WaitForChild("Config"))
local Util = require(ReplicatedStorage:WaitForChild("Util"))
local Remotes = require(ReplicatedStorage:WaitForChild("Remotes"))

local localPlayer = Players.LocalPlayer
local playerGui = localPlayer:WaitForChild("PlayerGui")

local remotes = Remotes.get()

-- Dernier snapshot de profil recu du serveur
local snapshot = nil

----------------------------------------------------------------
-- Petits constructeurs d'UI
----------------------------------------------------------------

local COLOR_PANEL = Color3.fromRGB(32, 27, 55)
local COLOR_PANEL_LIGHT = Color3.fromRGB(48, 40, 80)
local COLOR_ACCENT = Color3.fromRGB(150, 110, 255)
local COLOR_TEXT = Color3.fromRGB(240, 236, 255)

local function addCorner(instance, radius)
	local corner = Instance.new("UICorner")
	corner.CornerRadius = UDim.new(0, radius or 10)
	corner.Parent = instance
end

local function mkFrame(parent, props)
	local frame = Instance.new("Frame")
	frame.BackgroundColor3 = props.Color or COLOR_PANEL
	frame.BorderSizePixel = 0
	frame.Size = props.Size
	if props.Position then
		frame.Position = props.Position
	end
	if props.Anchor then
		frame.AnchorPoint = props.Anchor
	end
	if props.Transparency then
		frame.BackgroundTransparency = props.Transparency
	end
	frame.Name = props.Name or "Frame"
	frame.Parent = parent
	if not props.Square then
		addCorner(frame, props.Radius)
	end
	return frame
end

local function mkText(parent, props)
	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = props.Size
	if props.Position then
		label.Position = props.Position
	end
	if props.Anchor then
		label.AnchorPoint = props.Anchor
	end
	label.Font = props.Font or Enum.Font.FredokaOne
	label.Text = props.Text or ""
	label.TextColor3 = props.TextColor or COLOR_TEXT
	label.TextScaled = props.Scaled ~= false
	if props.XAlign then
		label.TextXAlignment = props.XAlign
	end
	label.Name = props.Name or "Texte"
	label.Parent = parent
	return label
end

local function mkButton(parent, props)
	local button = Instance.new("TextButton")
	button.BackgroundColor3 = props.Color or COLOR_ACCENT
	button.BorderSizePixel = 0
	button.Size = props.Size
	if props.Position then
		button.Position = props.Position
	end
	if props.Anchor then
		button.AnchorPoint = props.Anchor
	end
	button.Font = Enum.Font.FredokaOne
	button.Text = props.Text or ""
	button.TextColor3 = props.TextColor or Color3.fromRGB(255, 255, 255)
	button.TextScaled = true
	button.AutoButtonColor = true
	button.Name = props.Name or "Bouton"
	button.Parent = parent
	addCorner(button, props.Radius)
	if props.OnClick then
		button.Activated:Connect(props.OnClick)
	end
	return button
end

local function rarityColor(rarityKey)
	local rarity = Config.Rarities[rarityKey]
	if rarity then
		return Util.toColor3(rarity.Color)
	end
	return COLOR_TEXT
end

local function rarityDisplay(rarityKey)
	local rarity = Config.Rarities[rarityKey]
	if rarity and rarity.Display then
		return rarity.Display
	end
	return rarityKey
end

----------------------------------------------------------------
-- Racine
----------------------------------------------------------------

local screenGui = Instance.new("ScreenGui")
screenGui.Name = "ArcaneUI"
screenGui.ResetOnSpawn = false
screenGui.IgnoreGuiInset = false
screenGui.Parent = playerGui

----------------------------------------------------------------
-- Notifications animees (pile en haut a droite)
----------------------------------------------------------------

local notifContainer = Instance.new("Frame")
notifContainer.Name = "Notifications"
notifContainer.BackgroundTransparency = 1
notifContainer.AnchorPoint = Vector2.new(1, 0)
notifContainer.Position = UDim2.new(1, -10, 0, 70)
notifContainer.Size = UDim2.new(0, 320, 1, -90)
notifContainer.Parent = screenGui

local notifLayout = Instance.new("UIListLayout")
notifLayout.FillDirection = Enum.FillDirection.Vertical
notifLayout.HorizontalAlignment = Enum.HorizontalAlignment.Right
notifLayout.SortOrder = Enum.SortOrder.LayoutOrder
notifLayout.Padding = UDim.new(0, 6)
notifLayout.Parent = notifContainer

local NOTIF_COLORS = {
	info = Color3.fromRGB(70, 80, 140),
	succes = Color3.fromRGB(60, 140, 90),
	erreur = Color3.fromRGB(160, 60, 70),
	gemme = Color3.fromRGB(150, 70, 160),
}

local notifOrder = 0

local function showNotification(message, kind)
	notifOrder = notifOrder + 1
	local frame = mkFrame(notifContainer, {
		Name = "Notif",
		Size = UDim2.new(1, 0, 0, 54),
		Color = NOTIF_COLORS[kind] or NOTIF_COLORS.info,
	})
	frame.LayoutOrder = notifOrder
	frame.BackgroundTransparency = 1

	local label = mkText(frame, {
		Size = UDim2.new(1, -16, 1, -10),
		Position = UDim2.new(0, 8, 0, 5),
		Text = message,
		Font = Enum.Font.GothamBold,
	})
	label.TextTransparency = 1
	label.TextWrapped = true

	TweenService:Create(frame, TweenInfo.new(0.25), { BackgroundTransparency = 0.12 }):Play()
	TweenService:Create(label, TweenInfo.new(0.25), { TextTransparency = 0 }):Play()

	task.delay(4, function()
		local fadeOut = TweenService:Create(frame, TweenInfo.new(0.4), { BackgroundTransparency = 1 })
		TweenService:Create(label, TweenInfo.new(0.4), { TextTransparency = 1 }):Play()
		fadeOut:Play()
		fadeOut.Completed:Wait()
		frame:Destroy()
	end)
end

remotes.Notify.OnClientEvent:Connect(showNotification)

----------------------------------------------------------------
-- Textes flottants de gain (au-dessus du cristal canalise)
----------------------------------------------------------------

remotes.ChannelResult.OnClientEvent:Connect(function(position, mana, gems)
	local anchor = Instance.new("Part")
	anchor.Anchored = true
	anchor.CanCollide = false
	anchor.Transparency = 1
	anchor.Size = Vector3.new(0.2, 0.2, 0.2)
	anchor.CFrame = CFrame.new(position + Vector3.new(0, 4, 0))
	anchor.Parent = Workspace

	local billboard = Instance.new("BillboardGui")
	billboard.Size = UDim2.new(0, 160, 0, 60)
	billboard.StudsOffset = Vector3.new(0, 0, 0)
	billboard.AlwaysOnTop = true
	billboard.Parent = anchor

	local text = "+" .. Util.formatNumber(mana) .. " mana"
	if gems and gems > 0 then
		text = text .. "\n+" .. tostring(gems) .. " gemmes !"
	end
	local label = mkText(billboard, {
		Size = UDim2.new(1, 0, 1, 0),
		Text = text,
		TextColor = (gems and gems > 0) and Color3.fromRGB(255, 150, 255) or Color3.fromRGB(140, 220, 255),
	})
	label.TextStrokeTransparency = 0.5

	TweenService:Create(billboard, TweenInfo.new(0.9, Enum.EasingStyle.Quad, Enum.EasingDirection.Out),
		{ StudsOffset = Vector3.new(0, 4, 0) }):Play()
	TweenService:Create(label, TweenInfo.new(0.9), { TextTransparency = 1, TextStrokeTransparency = 1 }):Play()
	task.delay(1, function()
		anchor:Destroy()
	end)
end)

----------------------------------------------------------------
-- Barre de stats (haut de l'ecran)
----------------------------------------------------------------

local statsBar = mkFrame(screenGui, {
	Name = "BarreStats",
	Size = UDim2.new(0, 620, 0, 52),
	Position = UDim2.new(0.5, 0, 0, 8),
	Anchor = Vector2.new(0.5, 0),
	Color = COLOR_PANEL,
	Transparency = 0.15,
})

local statLabels = {}
local STAT_DEFS = {
	{ Key = "Mana", Titre = "Mana", Color = Color3.fromRGB(120, 200, 255) },
	{ Key = "Gems", Titre = "Gemmes", Color = Color3.fromRGB(255, 130, 240) },
	{ Key = "Rebirths", Titre = "Ascensions", Color = Color3.fromRGB(200, 160, 255) },
	{ Key = "Multi", Titre = "Multi", Color = Color3.fromRGB(255, 210, 110) },
}

for index = 1, #STAT_DEFS do
	local def = STAT_DEFS[index]
	local cell = mkFrame(statsBar, {
		Name = "Stat" .. def.Key,
		Size = UDim2.new(0.25, -10, 1, -10),
		Position = UDim2.new(0.25 * (index - 1), 5, 0, 5),
		Color = COLOR_PANEL_LIGHT,
	})
	mkText(cell, {
		Size = UDim2.new(1, -8, 0.42, 0),
		Position = UDim2.new(0, 4, 0, 0),
		Text = def.Titre,
		TextColor = def.Color,
	})
	statLabels[def.Key] = mkText(cell, {
		Size = UDim2.new(1, -8, 0.55, 0),
		Position = UDim2.new(0, 4, 0.44, 0),
		Text = "...",
		Font = Enum.Font.GothamBold,
	})
end

local function refreshStats()
	if not snapshot then
		return
	end
	statLabels.Mana.Text = Util.formatNumber(snapshot.Mana)
	statLabels.Gems.Text = Util.formatNumber(snapshot.Gems)
	statLabels.Rebirths.Text = tostring(snapshot.Rebirths)
	statLabels.Multi.Text = string.format("x%.2f", snapshot.GlobalMultiplier or 1)
end

----------------------------------------------------------------
-- Fenetres (une seule ouverte a la fois)
----------------------------------------------------------------

local windows = {}

local function closeAllWindows()
	for _, window in pairs(windows) do
		window.Visible = false
	end
end

local function mkWindow(name, titleText)
	local window = mkFrame(screenGui, {
		Name = "Fenetre" .. name,
		Size = UDim2.new(0, 560, 0, 420),
		Position = UDim2.new(0.5, 0, 0.5, 0),
		Anchor = Vector2.new(0.5, 0.5),
		Color = COLOR_PANEL,
	})
	window.Visible = false

	mkText(window, {
		Size = UDim2.new(1, -60, 0, 40),
		Position = UDim2.new(0, 14, 0, 6),
		Text = titleText,
		XAlign = Enum.TextXAlignment.Left,
	})
	mkButton(window, {
		Size = UDim2.new(0, 36, 0, 36),
		Position = UDim2.new(1, -44, 0, 8),
		Text = "X",
		Color = Color3.fromRGB(160, 60, 70),
		OnClick = function()
			window.Visible = false
		end,
	})

	local content = Instance.new("ScrollingFrame")
	content.Name = "Contenu"
	content.Position = UDim2.new(0, 10, 0, 52)
	content.Size = UDim2.new(1, -20, 1, -62)
	content.BackgroundTransparency = 1
	content.BorderSizePixel = 0
	content.ScrollBarThickness = 6
	content.CanvasSize = UDim2.new(0, 0, 0, 0)
	content.AutomaticCanvasSize = Enum.AutomaticSize.Y
	content.Parent = window

	local layout = Instance.new("UIListLayout")
	layout.SortOrder = Enum.SortOrder.LayoutOrder
	layout.Padding = UDim.new(0, 8)
	layout.Parent = content

	windows[name] = window
	return window, content
end

local function toggleWindow(name)
	local window = windows[name]
	local wasVisible = window.Visible
	closeAllWindows()
	window.Visible = not wasVisible
end

----------------------------------------------------------------
-- Fenetre BOUTIQUE
----------------------------------------------------------------

local shopWindow, shopContent = mkWindow("Boutique", "Boutique Arcanique")
local shopRefreshers = {}

local function mkShopRow(parent, order, titre, desc, buttonText, buttonColor, onClick)
	local row = mkFrame(parent, {
		Name = "Ligne",
		Size = UDim2.new(1, -8, 0, 74),
		Color = COLOR_PANEL_LIGHT,
	})
	row.LayoutOrder = order
	mkText(row, {
		Size = UDim2.new(0.62, -10, 0, 30),
		Position = UDim2.new(0, 10, 0, 6),
		Text = titre,
		XAlign = Enum.TextXAlignment.Left,
	})
	local descLabel = mkText(row, {
		Size = UDim2.new(0.62, -10, 0, 32),
		Position = UDim2.new(0, 10, 0, 38),
		Text = desc,
		Font = Enum.Font.Gotham,
		TextColor = Color3.fromRGB(190, 185, 215),
	})
	descLabel.TextWrapped = true
	local button = mkButton(row, {
		Size = UDim2.new(0.3, 0, 0, 40),
		Position = UDim2.new(0.66, 0, 0.5, -20),
		Text = buttonText,
		Color = buttonColor or COLOR_ACCENT,
		OnClick = onClick,
	})
	return row, button
end

do
	local order = 0
	mkText(shopContent, {
		Size = UDim2.new(1, 0, 0, 26),
		Text = "— Gamepasses —",
		TextColor = Color3.fromRGB(255, 210, 110),
	}).LayoutOrder = 0

	local PASS_ORDER = { "DoubleMana", "DoubleGems", "AutoTrain", "VIPZone", "VoidLord" }
	for i = 1, #PASS_ORDER do
		local passKey = PASS_ORDER[i]
		local def = Config.GamePasses[passKey]
		order = order + 1
		local _, button = mkShopRow(shopContent, order, def.Name, def.Desc, "Acheter", nil, function()
			if def.Id > 0 then
				MarketplaceService:PromptGamePassPurchase(localPlayer, def.Id)
			else
				showNotification("Ce gamepass n'est pas encore configuré (ID manquant).", "erreur")
			end
		end)
		table.insert(shopRefreshers, function()
			if snapshot and snapshot.Passes and snapshot.Passes[passKey] then
				button.Text = "Possédé"
				button.BackgroundColor3 = Color3.fromRGB(70, 120, 80)
			else
				button.Text = "Acheter"
				button.BackgroundColor3 = COLOR_ACCENT
			end
		end)
	end

	order = order + 1
	mkText(shopContent, {
		Size = UDim2.new(1, 0, 0, 26),
		Text = "— Packs & Gemmes —",
		TextColor = Color3.fromRGB(255, 210, 110),
	}).LayoutOrder = order

	local PRODUCT_ORDER = { "StarterPack", "Gems100", "Gems1200" }
	for i = 1, #PRODUCT_ORDER do
		local productKey = PRODUCT_ORDER[i]
		local def = Config.Products[productKey]
		order = order + 1
		local row, button = mkShopRow(shopContent, order, def.Name, def.Desc, "Acheter",
			Color3.fromRGB(90, 140, 220), function()
				if def.Id > 0 then
					MarketplaceService:PromptProductPurchase(localPlayer, def.Id)
				else
					showNotification("Ce produit n'est pas encore configuré (ID manquant).", "erreur")
				end
			end)
		if productKey == "StarterPack" then
			table.insert(shopRefreshers, function()
				local owned = snapshot and snapshot.StarterPackOwned
				row.Visible = not owned
			end)
		end
	end
end

local function refreshShop()
	for i = 1, #shopRefreshers do
		shopRefreshers[i]()
	end
end

----------------------------------------------------------------
-- Fenetre FAMILIERS
----------------------------------------------------------------

local petsWindow, petsContent = mkWindow("Familiers", "Familiers & Œufs")

local eggsSection = mkFrame(petsContent, {
	Name = "Oeufs",
	Size = UDim2.new(1, -8, 0, 120),
	Color = COLOR_PANEL_LIGHT,
})
eggsSection.LayoutOrder = 1

for i = 1, #Config.Eggs do
	local egg = Config.Eggs[i]
	local cell = mkFrame(eggsSection, {
		Name = "Oeuf" .. tostring(i),
		Size = UDim2.new(1 / #Config.Eggs, -10, 1, -10),
		Position = UDim2.new((i - 1) / #Config.Eggs, 5, 0, 5),
		Color = COLOR_PANEL,
	})
	mkText(cell, {
		Size = UDim2.new(1, -8, 0, 28),
		Position = UDim2.new(0, 4, 0, 4),
		Text = egg.Name,
	})
	mkText(cell, {
		Size = UDim2.new(1, -8, 0, 22),
		Position = UDim2.new(0, 4, 0, 34),
		Text = Util.formatNumber(egg.Cost) .. " gemmes",
		Font = Enum.Font.Gotham,
		TextColor = Color3.fromRGB(255, 130, 240),
	})
	mkButton(cell, {
		Size = UDim2.new(1, -16, 0, 34),
		Position = UDim2.new(0, 8, 1, -42),
		Text = "Ouvrir",
		OnClick = function()
			local invoked, ok, result = pcall(function()
				return remotes.BuyEgg:InvokeServer(i)
			end)
			if not invoked then
				showNotification("Le serveur n'a pas répondu, réessaie.", "erreur")
				return
			end
			if ok and type(result) == "table" then
				showNotification("✨ " .. result.Name .. " (" .. rarityDisplay(result.Rarity) ..
					", x" .. tostring(result.Multiplier) .. ") rejoint tes familiers !", "succes")
			else
				showNotification(tostring(result), "erreur")
			end
		end,
	})
end

local inventorySection = Instance.new("Frame")
inventorySection.Name = "Inventaire"
inventorySection.BackgroundTransparency = 1
inventorySection.Size = UDim2.new(1, -8, 0, 40)
inventorySection.AutomaticSize = Enum.AutomaticSize.Y
inventorySection.LayoutOrder = 2
inventorySection.Parent = petsContent

local inventoryLayout = Instance.new("UIListLayout")
inventoryLayout.SortOrder = Enum.SortOrder.LayoutOrder
inventoryLayout.Padding = UDim.new(0, 6)
inventoryLayout.Parent = inventorySection

local function refreshPets()
	if not snapshot then
		return
	end
	for _, child in ipairs(inventorySection:GetChildren()) do
		if child:IsA("Frame") then
			child:Destroy()
		end
	end

	local equippedCount = 0
	for i = 1, #snapshot.Pets do
		if snapshot.Pets[i].Equipped then
			equippedCount = equippedCount + 1
		end
	end

	local header = mkFrame(inventorySection, {
		Name = "Entete",
		Size = UDim2.new(1, 0, 0, 30),
		Color = COLOR_PANEL_LIGHT,
	})
	header.LayoutOrder = 0
	mkText(header, {
		Size = UDim2.new(1, -12, 1, 0),
		Position = UDim2.new(0, 6, 0, 0),
		Text = "Inventaire — " .. tostring(equippedCount) .. "/" ..
			tostring(snapshot.MaxEquippedPets or Config.MaxEquippedPets) .. " équipés",
		XAlign = Enum.TextXAlignment.Left,
	})

	if #snapshot.Pets == 0 then
		local empty = mkFrame(inventorySection, {
			Name = "Vide",
			Size = UDim2.new(1, 0, 0, 40),
			Color = COLOR_PANEL_LIGHT,
		})
		empty.LayoutOrder = 1
		mkText(empty, {
			Size = UDim2.new(1, -12, 1, 0),
			Position = UDim2.new(0, 6, 0, 0),
			Text = "Aucun familier : ouvre un œuf pour commencer !",
			Font = Enum.Font.Gotham,
			TextColor = Color3.fromRGB(190, 185, 215),
		})
	end

	for i = 1, #snapshot.Pets do
		local pet = snapshot.Pets[i]
		local row = mkFrame(inventorySection, {
			Name = "Pet" .. pet.Uid,
			Size = UDim2.new(1, 0, 0, 52),
			Color = COLOR_PANEL_LIGHT,
		})
		row.LayoutOrder = i
		mkText(row, {
			Size = UDim2.new(0.42, -10, 0, 26),
			Position = UDim2.new(0, 10, 0, 4),
			Text = pet.Name,
			XAlign = Enum.TextXAlignment.Left,
			TextColor = rarityColor(pet.Rarity),
		})
		mkText(row, {
			Size = UDim2.new(0.42, -10, 0, 20),
			Position = UDim2.new(0, 10, 0, 30),
			Text = rarityDisplay(pet.Rarity) .. "  •  mana x" .. tostring(pet.Multiplier),
			Font = Enum.Font.Gotham,
			TextColor = Color3.fromRGB(190, 185, 215),
			XAlign = Enum.TextXAlignment.Left,
		})
		mkButton(row, {
			Size = UDim2.new(0.26, 0, 0, 36),
			Position = UDim2.new(0.71, 0, 0.5, -18),
			Text = pet.Equipped and "Retirer" or "Équiper",
			Color = pet.Equipped and Color3.fromRGB(150, 90, 70) or Color3.fromRGB(70, 130, 90),
			OnClick = function()
				local invoked, ok, message = pcall(function()
					return remotes.SetPetEquipped:InvokeServer(pet.Uid, not pet.Equipped)
				end)
				if invoked and not ok then
					showNotification(tostring(message), "erreur")
				end
			end,
		})
	end
end

----------------------------------------------------------------
-- Fenetre ASCENSION
----------------------------------------------------------------

local rebirthWindow, rebirthContent = mkWindow("Ascension", "Ascension Arcanique")

local rebirthInfo = mkFrame(rebirthContent, {
	Name = "Infos",
	Size = UDim2.new(1, -8, 0, 220),
	Color = COLOR_PANEL_LIGHT,
})
rebirthInfo.LayoutOrder = 1

local rebirthTitle = mkText(rebirthInfo, {
	Size = UDim2.new(1, -20, 0, 40),
	Position = UDim2.new(0, 10, 0, 8),
	Text = "Sacrifie TOUTE ta mana pour un pouvoir permanent.",
})
rebirthTitle.TextWrapped = true

local rebirthDetails = mkText(rebirthInfo, {
	Size = UDim2.new(1, -20, 0, 120),
	Position = UDim2.new(0, 10, 0, 56),
	Text = "...",
	Font = Enum.Font.Gotham,
	TextColor = Color3.fromRGB(200, 195, 225),
})
rebirthDetails.TextWrapped = true

local rebirthButton = mkButton(rebirthContent, {
	Size = UDim2.new(1, -8, 0, 52),
	Text = "S'élever",
	Color = Color3.fromRGB(170, 110, 60),
	OnClick = function()
		local invoked, ok, message = pcall(function()
			return remotes.DoRebirth:InvokeServer()
		end)
		if invoked and not ok then
			showNotification(tostring(message), "erreur")
		end
	end,
})
rebirthButton.LayoutOrder = 2

local function refreshRebirth()
	if not snapshot then
		return
	end
	local bonusNow = math.floor((snapshot.RebirthMultiplier - 1) * 100 + 0.5)
	local bonusNext = math.floor(Config.Rebirth.BonusPerRebirth * 100)
	rebirthDetails.Text = "Ascensions accomplies : " .. tostring(snapshot.Rebirths)
		.. "\nBonus permanent actuel : +" .. tostring(bonusNow) .. " % de mana"
		.. "\n\nProchaine ascension :"
		.. "\n• Coût : " .. Util.formatNumber(snapshot.RebirthCost) .. " mana (TOUTE ta mana est sacrifiée)"
		.. "\n• Récompense : +" .. tostring(bonusNext) .. " % de mana permanent"
		.. "\n• Auras aux paliers 1, 3, 6 et 10"
	local canAfford = snapshot.Mana >= snapshot.RebirthCost
	rebirthButton.Text = canAfford and "S'élever !"
		or ("S'élever (" .. Util.formatNumber(snapshot.RebirthCost) .. " mana requis)")
	rebirthButton.BackgroundColor3 = canAfford
		and Color3.fromRGB(210, 140, 60)
		or Color3.fromRGB(100, 80, 70)
end

----------------------------------------------------------------
-- Colonne de boutons (gauche)
----------------------------------------------------------------

local buttonColumn = Instance.new("Frame")
buttonColumn.Name = "ColonneBoutons"
buttonColumn.BackgroundTransparency = 1
buttonColumn.Position = UDim2.new(0, 10, 0.5, -130)
buttonColumn.Size = UDim2.new(0, 130, 0, 260)
buttonColumn.Parent = screenGui

local columnLayout = Instance.new("UIListLayout")
columnLayout.SortOrder = Enum.SortOrder.LayoutOrder
columnLayout.Padding = UDim.new(0, 8)
columnLayout.Parent = buttonColumn

mkButton(buttonColumn, {
	Size = UDim2.new(1, 0, 0, 54),
	Text = "Boutique",
	Color = Color3.fromRGB(90, 140, 220),
	OnClick = function()
		toggleWindow("Boutique")
	end,
}).LayoutOrder = 1

mkButton(buttonColumn, {
	Size = UDim2.new(1, 0, 0, 54),
	Text = "Familiers",
	Color = Color3.fromRGB(70, 130, 90),
	OnClick = function()
		toggleWindow("Familiers")
	end,
}).LayoutOrder = 2

mkButton(buttonColumn, {
	Size = UDim2.new(1, 0, 0, 54),
	Text = "Ascension",
	Color = Color3.fromRGB(170, 110, 60),
	OnClick = function()
		toggleWindow("Ascension")
	end,
}).LayoutOrder = 3

local autoButton = mkButton(buttonColumn, {
	Size = UDim2.new(1, 0, 0, 54),
	Text = "Auto : OFF",
	Color = Color3.fromRGB(90, 85, 120),
	OnClick = function()
		if not snapshot then
			return
		end
		if snapshot.Passes and snapshot.Passes.AutoTrain then
			remotes.ToggleAuto:FireServer(not snapshot.AutoTrain)
		else
			local def = Config.GamePasses.AutoTrain
			if def.Id > 0 then
				MarketplaceService:PromptGamePassPurchase(localPlayer, def.Id)
			else
				showNotification("L'auto-canalisation nécessite le gamepass « Auto-Canalisation » (ID non configuré).", "erreur")
			end
		end
	end,
})
autoButton.LayoutOrder = 4

local function refreshAutoButton()
	if not snapshot then
		return
	end
	if snapshot.AutoTrain then
		autoButton.Text = "Auto : ON"
		autoButton.BackgroundColor3 = Color3.fromRGB(70, 160, 100)
	else
		autoButton.Text = "Auto : OFF"
		autoButton.BackgroundColor3 = Color3.fromRGB(90, 85, 120)
	end
end

----------------------------------------------------------------
-- Synchronisation avec le serveur
----------------------------------------------------------------

local function applySnapshot(newSnapshot)
	snapshot = newSnapshot
	refreshStats()
	refreshShop()
	refreshRebirth()
	refreshAutoButton()
	-- L'inventaire des familiers est reconstruit uniquement si visible
	-- (le snapshot arrive a chaque gain de mana)
	if petsWindow.Visible then
		refreshPets()
	end
end

-- Reconstruit l'inventaire a l'ouverture de la fenetre Familiers
petsWindow:GetPropertyChangedSignal("Visible"):Connect(function()
	if petsWindow.Visible then
		refreshPets()
	end
end)

remotes.ProfileChanged.OnClientEvent:Connect(applySnapshot)

-- Demande initiale (au cas ou le premier push serveur soit passe avant nous)
task.spawn(function()
	local invoked, initial = pcall(function()
		return remotes.GetProfile:InvokeServer()
	end)
	if invoked and initial and not snapshot then
		applySnapshot(initial)
	end
end)
