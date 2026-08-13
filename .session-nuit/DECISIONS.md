# Décisions prises en autonomie — nuit du 2026-08-13

## D1 — Branche de travail
- Contexte : le skill nuit-autonome recommande `nuit/AAAA-MM-JJ`, mais la session impose la branche `claude/arcane-legends-roblox-1nyxnf`.
- Choix : branche désignée `claude/arcane-legends-roblox-1nyxnf`. Réversible (simple branche).

## D2 — Emplacement du projet
- Contexte : le repo contient un `index.html` sans rapport avec le jeu.
- Choix : tout le jeu dans un dossier `arcane-legends/` à la racine, `index.html` intact. Réversible.

## D3 — `.session-nuit/` committé
- Contexte : conteneur éphémère ; tout ce qui n'est pas pushé est perdu. Le prompt exige journal des décisions + rapport de session en livrables.
- Choix : committer `.session-nuit/` (pas de .gitignore dessus). Réversible.

## D4 — Architecture anti-cycle : registre de services
- Contexte : exigence « pas de dépendance circulaire » avec 10 modules serveur qui s'appellent mutuellement (Economy ↔ Monetization notamment).
- Options : requires croisés ordonnés / injection par registre.
- Choix : aucun module serveur ne require un autre module serveur ; `Main.server.lua` construit un registre `services` passé à chaque `Init(registry)`. Cycle structurellement impossible.
- Réversibilité : facile (pattern localisé dans les Init).

## D5 — Canalisation via ClickDetector + ProximityPrompt (pas de remote dédié)
- Contexte : « clic / tap / touche E à proximité ».
- Choix : chaque cristal porte un ClickDetector (clic + tap mobile) et un ProximityPrompt (touche E), tous deux traités DIRECTEMENT côté serveur, puis re-validés (distance, cooldown, zone). Moins de surface d'attaque qu'un RemoteEvent ouvert.
- Le remote `CastSpell` (PvP) et `ToggleAuto` restent nécessaires côté client.

## D6 — Contenu des familiers et des œufs (non spécifié en détail)
- Contexte : le prompt impose 3 œufs (50/500/5000 gemmes), raretés Commun→Légendaire, mult x1.2→x3.2, + 2 exclusifs monétisation.
- Choix : 12 familiers répartis dans 3 œufs avec poids (ex. œuf Novice : Commun 50/30, Rare 15, Épique 4, Légendaire 1) ; noms fantasy français ; pack de départ = « Apprenti du Néant » (Épique x1.6) ; gamepass = « Seigneur du Vide » (Mythique x3.5).
- Réversibilité : totale (tout dans Config.lua).

## D7 — Progression linéaire des zones
- Contexte : non spécifié si une zone exige la précédente.
- Choix : achat séquentiel (zone n exige zone n−1), standard du genre simulateur et cohérent avec les coûts exponentiels. Une ligne à supprimer dans ZoneService pour revenir en achat libre.

## D8 — Ascension : les zones sont CONSERVÉES
- Contexte : « sacrifier TOUTE sa mana » — le prompt ne mentionne que la mana.
- Choix : lecture littérale, seule la mana est remise à 0 ; zones, familiers et gemmes conservés. Plus doux, et conforme au texte.

## D9 — Mana achetée (Pack de Départ) comptée dans la mana totale
- Contexte : le classement est sur « la mana totale gagnée » ; jeu assumé pay-to-win (accélération).
- Choix : les 5 000 mana du pack comptent dans TotalMana. Cohérent avec l'esprit P2W du brief.

## D10 — Pack de Départ acheté deux fois (cas limite ProcessReceipt)
- Contexte : produit « UNIQUE », mais un achat dupliqué reste techniquement possible si l'utilisateur force l'achat hors UI.
- Choix : re-créditer mana + gemmes (l'acheteur n'est pas lésé) mais jamais de second familier exclusif ; l'UI masque le pack une fois possédé. Documenté en commentaire dans MonetizationService.

## D11 — Session temporaire et achats
- Contexte : que faire d'un developer product acheté pendant une session dont le profil n'a pas pu se charger ?
- Choix : `ProcessReceipt` renvoie `NotProcessedYet` si la session ne peut pas sauvegarder → Roblox re-présentera le reçu plus tard. Aucun achat consommé sans persistance possible.

## D12 — Score de classement : sessions temporaires exclues
- Contexte : une session temporaire pourrait publier un TotalMana faussé (repart de 0).
- Choix : LeaderboardService ne publie jamais le score d'une session non sauvegardable.

## D13 — Visuels : familiers-orbes soudés, projectile serveur, cristaux animés client
- Familiers équipés = orbes néon soudés au personnage (zéro coût par frame, répliqué naturellement).
- Projectile PvP créé côté serveur (anchored, interpolé ~0,5 s) : visible par tous sans remote supplémentaire.
- Flottement/rotation des cristaux animé CÔTÉ CLIENT via tag CollectionService « ArcaneCrystal » : zéro trafic réseau.

## D14 — Leaderstats en StringValue formatées
- Contexte : mana endgame > millions, illisible en IntValue brut.
- Choix : leaderstats `Mana` et `Gemmes` en StringValue formatées (1.2M), `Ascensions` en IntValue.

## D15 — Outillage : lua5.1 installé, tests hors-Roblox
- lua5.1/luac installés via apt (gratuit, open source) pour `luac -p` sur les 16 fichiers.
- Config.lua et Util.lua étant du Lua pur, un banc de 44 tests réels (`tests/test_logic.lua`) s'exécute hors Roblox : formules d'équilibrage, tirage pondéré (20 000 tirages), formatage, clamp, rectangles d'arène.

## D16 — Tolérance de distance de canalisation
- Contexte : la validation serveur stricte à 14 studs rejetterait des clics légitimes (latence, taille du personnage).
- Choix : portée d'interaction 14 studs (ClickDetector/Prompt) mais validation serveur à 14+6 studs. Le serveur reste la seule autorité, la marge absorbe la latence.
