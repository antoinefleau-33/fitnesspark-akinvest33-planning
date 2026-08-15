# Mod Fabric — POC Client

Deux fonctionnalités dans un seul mod :

- **Musique** : incrustation Spotify dans le jeu, alimentée par le lanceur
- **Diagnostic BlockEntity** : boîtes de débogage et statistiques de rendu

## État : ce qui est vérifié et ce qui ne l'est pas

Je n'ai ici ni le jar de Minecraft, ni Fabric Loom, ni les mappings de 26.2. **Je n'ai pas pu
compiler la partie qui touche au jeu.** Le code a donc été écrit pour que le maximum soit
vérifiable sans Minecraft.

| Fichier | Dépend de Minecraft | État |
|---|---|---|
| `musichud/MiniJson.java` | non | **compilé et testé** |
| `musichud/MusicState.java` | non | **compilé et testé** |
| `musichud/SpotifyBridge.java` | non | **compilé et testé contre le vrai serveur du lanceur** |
| `bediag/BeSnapshot.java` | non | **compilé et testé** |
| `bediag/BeFilter.java` | non | **compilé et testé** |
| `bediag/BeStats.java` | non | **compilé et testé** |
| `bediag/DepthMode.java` | non | **compilé et testé** |
| `bediag/BeCollector.java` | oui | non compilé |
| `bediag/DiagRenderer.java` | oui | non compilé |
| `musichud/MusicHud.java` | oui | non compilé |
| `DiagnosticsHud.java` | oui | non compilé |
| `ClientMod.java` | oui | non compilé |

Ce découpage est délibéré : quand Mojang change une signature, la casse est confinée aux cinq
fichiers de la seconde moitié.

### Résultats des tests

Pont Spotify, contre le serveur Python réellement lancé :

```
connecte   : true          titre : One More Time      artiste : Daft Punk
position   : 1:35 / 5:20   avancement : 30%
playlists  : Musique pour jouer (142), Chill Lofi (87)
commandes  : aucune erreur
jeton faux : hors ligne, "jeton invalide"
```

Diagnostic, sur un monde synthétique de 4000 BlockEntity :

```
avec renderer      2466      3 conteneurs seuls   1485
-> le filtre conteneurs rate 40 % du cout de rendu reel
cout : 271 us/frame (1.6 % d'une frame a 60 fps)
chunks satures : (3,-2) -> 187,  (-5,8) -> 177
THROUGH_WALLS -> solo: THROUGH_WALLS   multijoueur: OCCLUDED_DIMMED
```

## Compilation

```bash
cd mod
./gradlew build
```

Le jar arrive dans `build/libs/`. Copie-le dans le dossier `mods` de ton installation, ou passe
par le lanceur (*Mods → Ajouter un mod*).

**À vérifier avant** : les valeurs de `gradle.properties`. Les numéros de yarn et de Fabric API
changent à chaque build ; ceux que j'ai mis sont des exemples. Les valeurs exactes sont sur
[fabricmc.net/develop](https://fabricmc.net/develop).

**Attends-toi à corriger des erreurs de compilation** dans les cinq fichiers dépendant de
Minecraft. Les points les plus susceptibles d'avoir bougé en 26.2 :

- `HudRenderCallback` — remplacé par `HudElementRegistry` dans les versions récentes
- `WorldRenderEvents.AFTER_TRANSLUCENT` — vérifier que l'étape existe toujours
- `Tessellator.begin(...)` et `BufferRenderer.drawWithGlobalProgram` — l'API de rendu a été
  refondue autour des *render pipelines*
- `renderer.isInRenderDistance(...)` — nom de méthode variable selon les mappings

Ces erreurs sont mécaniques : le compilateur t'indique la ligne, et la logique derrière est déjà
testée.

## Commandes

| Touche | Effet |
|---|---|
| `M` | afficher/masquer l'incrustation musique |
| `P` | lecture / pause |
| `.` | morceau suivant |
| `B` | activer le diagnostic BlockEntity |
| `N` | filtre suivant |
| `,` | mode d'occlusion suivant |

## Comment la musique circule

```
Spotify  ←→  Lanceur (Python)  ←─ HTTP 127.0.0.1 ─→  Mod (Java)  →  incrustation
```

Le mod ne parle jamais à Spotify directement : il n'a ni les jetons d'authentification, ni la
possibilité d'ouvrir un navigateur. Le lanceur démarre le serveur local au lancement du jeu et
dépose `.spotify-bridge.json` dans le dossier de jeu — port et jeton, régénérés à chaque fois.

Le serveur écoute sur `127.0.0.1` uniquement et exige ce jeton. Sans lui, n'importe quelle page
web ouverte dans le navigateur pourrait piloter la musique depuis du JavaScript.

## Diagnostic BlockEntity — deux choix à connaître

**Le filtre par défaut suit la présence d'un renderer, pas le type de bloc.** Le coût de rendu
d'une BlockEntity tient à son `BlockEntityRenderer`. Coffres, shulkers et ender chests en ont un —
ils sont même parmi les plus chers — mais panneaux, bannières, lits et conduits aussi. Mesuré :
2466 BlockEntity avec renderer contre 1485 pour les trois conteneurs, soit **40 % du coût de rendu
invisible** avec le filtre le plus étroit. Et pour le coût de *tick*, ce sont les hoppers qui
dominent, absents de ce filtre. `BeFilter.ofTypes(...)` reste disponible pour isoler un type précis.

**Le mode traversant est limité au solo strict**, vérifié à chaque frame : serveur intégré actif,
non publié au LAN, aucune connexion distante. Hors de là il dégrade en double passe atténuée, qui
distingue le visible du caché — plus informatif qu'un aplat pour déboguer du culling. La règle vit
dans `DepthMode.resolve()`, en un seul point testable.

## Le piège OpenGL à ne pas reproduire

`glDisable(GL_DEPTH_TEST)` en appel brut est le défaut le plus coûteux de ce genre de mod, et il
ne se manifeste pas là où on le provoque. Blaze3D maintient un cache logiciel de l'état GL : un
appel brut change l'état réel sans mettre le cache à jour, Blaze3D croit le test toujours actif et
ne le réactive jamais. Le symptôme apparaît plusieurs frames plus tard, ailleurs — HUD disparu,
particules mal triées.

Tout passe donc par `RenderSystem.*`, qui fait l'appel **et** synchronise le cache. Et la
restauration est dans un `finally` : si un appel de dessin lève, l'état doit revenir à la normale
quand même.
