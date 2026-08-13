# Plan de session — nuit du 2026-08-13

Objectif : jeu Roblox complet « Arcane Legends » (simulateur de sorcier), 100 % généré par script, serveur autoritaire, livré en fichiers .lua organisés + place .rbxlx prêt à ouvrir.

Branche de travail : `claude/arcane-legends-roblox-1nyxnf` (branche désignée par la session — remplace la convention `nuit/AAAA-MM-JJ`).

## Tâches (par priorité)

1. [x] Cadre de session (.session-nuit/, outillage luac 5.1)
2. [ ] `ReplicatedStorage` : Config.lua (équilibrage centralisé), Util.lua, Remotes.lua
3. [ ] `ServerScriptService` : DataManager (DataStore robuste), Economy
4. [ ] MapBuilder (lobby, 5 zones + cristaux + totems, arène, zone VIP, panneau classement)
5. [ ] TrainingService (canalisation + auto), ZoneService (achat zones)
6. [ ] PetService (œufs, tirage pondéré serveur, équipement), RebirthService (ascensions + auras)
7. [ ] CombatService (PvP arène validé serveur), MonetizationService (gamepasses, products, ProcessReceipt idempotent)
8. [ ] LeaderboardService (OrderedDataStore top 10, panneau physique, refresh 60 s)
9. [ ] Main.server.lua (bootstrap sans dépendance circulaire)
10. [ ] LocalScripts : InputClient (clic/tap/E + PvP), UIClient (UI 100 % script, en français)
11. [ ] Générateur build_rbxlx.py + place ArcaneLegends.rbxlx
12. [ ] Validation : luac -p sur chaque fichier + revue manuelle (remotes cohérents, nil-safety, pas de cycle)
13. [ ] Docs : README d'installation, DECISIONS.md, RAPPORT.md
14. [ ] Commits atomiques + push sur la branche désignée

## Contraintes clés
- Lua 5.1 pur (pas de `+=`, pas de `continue`, pas d'annotations de type) → vérifiable par `luac -p`.
- Serveur 100 % autoritaire ; client = affichage + demandes.
- Clés de tables persistées en string (JSON DataStore).
- Session temporaire jamais sauvegardée en cas d'échec de chargement.
- Tous les IDs de monétisation à 0 avec TODO_UTILISATEUR.
- Aucune référence sport/fitness/musculation.
