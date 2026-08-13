# Arcane Legends — simulateur de sorcier Roblox

Jeu Roblox complet : canalise des cristaux magiques pour gagner de la Mana,
débloque 5 zones, collectionne des familiers, élève-toi par les Ascensions,
affronte les autres sorciers dans l'arène et grimpe au classement global.

**Tout est généré par script** : map, interface, effets — aucun asset à importer.
**Serveur 100 % autoritaire** : le client ne fait qu'afficher et demander.

---

## Installation (2 minutes)

1. Ouvre **Roblox Studio**.
2. `Fichier → Ouvrir depuis un fichier` → choisis **`ArcaneLegends.rbxlx`**.
   Les 16 scripts sont déjà en place dans l'arborescence (ReplicatedStorage,
   ServerScriptService, StarterPlayer > StarterPlayerScripts).
3. Publie le jeu : `Fichier → Publier sur Roblox` (nécessaire pour les DataStores).
4. Active l'API Studio : `Paramètres du jeu → Sécurité → "Enable Studio Access
   to API Services"` = ON (sinon pas de sauvegarde en test Studio).
5. Appuie sur **Play**. La map se construit au démarrage, l'UI apparaît.

## Configuration de la monétisation (TODO_UTILISATEUR)

Tous les IDs sont à `0` dans **`ReplicatedStorage/Config.lua`** (cherche
`TODO_UTILISATEUR`). Sur [create.roblox.com](https://create.roblox.com),
dans ta expérience :

- **5 gamepasses** (Monétisation → Passes) : x2 Mana, x2 Gemmes,
  Auto-Canalisation, Sanctuaire VIP, Seigneur du Vide
  → renseigne les IDs dans `Config.GamePasses`.
- **3 developer products** (Monétisation → Produits) : Pack de Départ,
  100 Gemmes, 1200 Gemmes → renseigne les IDs dans `Config.Products`.

Un ID laissé à 0 = fonctionnalité proprement désactivée (bouton « non
configuré »), le reste du jeu fonctionne normalement.

## Arborescence

```
arcane-legends/
├── ArcaneLegends.rbxlx          ← place prête à ouvrir dans Studio
├── build_rbxlx.py               ← régénère le .rbxlx depuis src/
├── tests/test_logic.lua         ← 44 tests hors-Roblox (lua5.1)
└── src/
    ├── ReplicatedStorage/       (ModuleScripts partagés)
    │   ├── Config.lua           ← TOUT l'équilibrage + IDs monétisation
    │   ├── Util.lua             ← utilitaires purs (format, tirage pondéré…)
    │   └── Remotes.lua          ← définition unique des remotes
    ├── ServerScriptService/
    │   ├── Main.server.lua      ← bootstrap (registre de services, zéro cycle)
    │   ├── DataManager.lua      ← DataStore : retry+backoff, autosave 120 s,
    │   │                          save déconnexion + BindToClose, session
    │   │                          temporaire jamais sauvegardée
    │   ├── Economy.lua          ← multiplicateurs + transactions + snapshot
    │   ├── MapBuilder.lua       ← map 100 % script (zones, arène, VIP, panneau)
    │   ├── TrainingService.lua  ← canalisation validée serveur + auto (pass)
    │   ├── ZoneService.lua      ← achat de zones aux totems + pads VIP
    │   ├── PetService.lua       ← œufs (tirage pondéré serveur), équipement
    │   ├── RebirthService.lua   ← ascensions + auras (paliers 1/3/6/10)
    │   ├── CombatService.lua    ← PvP arène (portée 45, cd 0,6 s, dégâts clampés)
    │   ├── MonetizationService.lua ← gamepasses + ProcessReceipt idempotent
    │   └── LeaderboardService.lua  ← top 10 OrderedDataStore, refresh 60 s
    └── StarterPlayerScripts/
        ├── InputClient.client.lua  ← PvP au clic/tap + animation cristaux
        └── UIClient.client.lua     ← toute l'UI (français), notifications
```

## Boucle de jeu

| Système | Règle |
|---|---|
| Canalisation | clic / tap / touche **E** près d'un cristal → +Mana (base 1 × zone × familiers × ascensions × gamepass) |
| Zones | Prairie (gratuite, x1) → Forêt 250 → Cryptes 5 000 → Volcan 100 000 → Dimension Astrale 2 000 000 ; gain verrouillé côté serveur tant que non achetée au totem (touche **F**) |
| Gemmes | 5 % de chance par canalisation (1-3) ; +25 par victoire en duel |
| Familiers | œufs à 50 / 500 / 5 000 gemmes, tirage pondéré **serveur**, 3 équipés max, multiplicateur = 1 + Σ(mult − 1) |
| Ascension | sacrifie TOUTE ta mana ; coût = 10 000 × 4ⁿ ; +50 % permanent par ascension ; auras aux paliers 1/3/6/10 |
| PvP | arène dédiée ; clic sur un adversaire = sort ; dégâts = clamp(5 + Mana^0.35, 5, 50) ; tout validé serveur |
| Classement | top 10 mana totale (OrderedDataStore), panneau physique, refresh 60 s |

## Développement

- **Modifier l'équilibrage** : tout est dans `src/ReplicatedStorage/Config.lua`.
- **Régénérer la place** après modification des sources :
  `python3 build_rbxlx.py` (auto-vérifie le round-trip des sources).
- **Vérifier la syntaxe** (Lua 5.1 pur, garanti compatible Luau) :
  `for f in $(find src -name '*.lua'); do luac -p $f; done`
- **Tests logiques** (hors Roblox) : `lua5.1 tests/test_logic.lua` — 44 tests.

## Garanties techniques

- Aucun module serveur ne `require` un autre module serveur : `Main.server.lua`
  câble un registre partagé → dépendance circulaire impossible.
- Distances, cooldowns, fonds, zones, portées, tirages : validés serveur.
- Clés des tables persistées en **string** (contrainte JSON DataStore).
- Échec de chargement DataStore → session temporaire **jamais** sauvegardée.
- `ProcessReceipt` idempotent (historique des `PurchaseId` dans le profil).
- Cache gamepass rafraîchi à l'achat en jeu (`PromptGamePassPurchaseFinished`).
