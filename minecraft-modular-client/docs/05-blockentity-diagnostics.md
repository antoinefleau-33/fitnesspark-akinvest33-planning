# 5. Diagnostic de rendu des BlockEntity

Module `be-diagnostics` : mesurer le coût de chargement et de rendu des BlockEntity, et localiser
les concentrations qui font chuter le framerate.

## Choix du filtre

La demande initiale portait sur `chest`, `shulker_box` et `ender_chest`. Ce n'est pas le bon
ensemble pour la question posée, et l'écart mérite d'être explicite.

Le coût de **rendu** d'une BlockEntity est déterminé par la présence d'un `BlockEntityRenderer`.
Les trois conteneurs en ont un — ils sont même parmi les plus chers, avec modèle animé et
manipulation de `PoseStack` à chaque frame — mais panneaux, bannières, lits, cloches, conduits,
crânes et pots décorés aussi. Un filtre limité aux trois conteneurs sous-estime donc le coût réel
de rendu, souvent d'un facteur 2 ou plus.

Le coût de **tick**, lui, est dominé par les hoppers, très loin devant tout le reste — et un hopper
n'a pas de renderer. Il est absent du filtre demandé.

D'où les filtres fournis :

| Filtre | Prédicat | Question à laquelle il répond |
|---|---|---|
| `WITH_RENDERER` (défaut) | `hasRenderer()` | combien coûte le rendu des BE ? |
| `TICKING` | `ticking()` | combien coûte leur mise à jour ? |
| `RENDERER_OUT_OF_RANGE` | `hasRenderer() && !inViewDistance()` | le culling fonctionne-t-il ? |
| `ALL` | — | inventaire complet |
| `ofTypes(...)` | ensemble explicite | isoler un type suspect |

`BeFilter.ofTypes("minecraft:chest", "minecraft:shulker_box", "minecraft:ender_chest")` reste
disponible : isoler un type précis est un besoin légitime quand on soupçonne un BER particulier.

Sur le monde synthétique du démonstrateur, l'écart est mesuré : 2298 BE avec renderer contre 1310
pour le filtre conteneurs seul.

## Politique d'occlusion

Trois modes, dans `WorldRenderer.DepthMode` :

- `OCCLUDED` — test de profondeur normal.
- `OCCLUDED_DIMMED` — **le mode recommandé**. Deux passes : la partie cachée en `GL_GREATER`
  atténuée à 28 %, la partie visible en `GL_LEQUAL` à pleine intensité. Il porte strictement plus
  d'information que les deux autres, puisqu'il permet de distinguer d'un coup d'œil ce qui est
  réellement visible de ce qui est occlus — exactement la question qu'on se pose en déboguant du
  culling. Un aplat sans test de profondeur, à l'inverse, perd cette distinction.
- `THROUGH_WALLS` — test désactivé. **Dégradé automatiquement en `OCCLUDED_DIMMED` hors solo.**

La règle de dégradation vit dans `DepthMode.resolve(requested, singleplayer)`, en un seul point,
testable sans contexte graphique. Le module de diagnostic ne la connaît pas et ne peut donc pas
l'oublier. `GameBridge.isSingleplayer()` est évalué à chaque frame et vaut vrai uniquement si le
serveur intégré tourne **et** n'est pas publié — un monde ouvert au LAN en cours de partie fait
donc basculer le mode immédiatement.

## OpenGL : ce que fait chaque bloc

### Rendu instancié

12 arêtes d'un cube unitaire (24 sommets, `GL_LINES`) envoyées une fois ; chaque boîte est une
instance de 10 floats (origine, taille, couleur) avec `glVertexAttribDivisor(loc, 1)`. 5 000 boîtes
= 1 draw call. En mode immédiat, ce serait 5 000 changements d'état — le diagnostic coûterait plus
cher que ce qu'il mesure.

### Coordonnées relatives à la caméra

```java
target.addRelative((float) (minX - camX), ...);   // soustraction en double, PUIS conversion
```

Un `float` a 24 bits de mantisse : à x = 1 000 000, deux valeurs représentables consécutives sont
distantes de ~0,06 bloc. En coordonnées absolues, les boîtes tremblent visiblement loin du spawn,
et le tremblement varie avec l'angle de vue. La soustraction doit se faire **en double**, avant la
conversion. Minecraft applique la même correction (`poseStack.translate(-camX, -camY, -camZ)`).

### Les deux passes de profondeur

```java
glDepthFunc(GL_GREATER); glDepthMask(false);   // passe 1 : ce qui est CACHÉ, atténué
glDepthFunc(GL_LEQUAL);  glDepthMask(true);    // passe 2 : ce qui est VISIBLE
```

`glDepthMask(false)` sur la première passe est indispensable : sans lui, les lignes situées
derrière le décor écrivent dans le depth buffer et masquent la géométrie dessinée ensuite.

### Ne jamais appeler `glEnable`/`glDisable` directement

C'est le piège le plus coûteux, et il ne se manifeste pas là où on le provoque.

Depuis 1.17, Blaze3D maintient un **cache logiciel de l'état GL** (`RenderSystem`) pour éviter les
appels pilote redondants. Un `GL11.glDisable(GL_DEPTH_TEST)` brut change l'état réel sans mettre le
cache à jour : Blaze3D croit encore le test actif, ne le réactive donc pas quand il en a besoin, et
le symptôme apparaît plusieurs frames plus tard dans du code sans rapport — HUD qui disparaît,
entités visibles à travers les murs, particules mal triées. Le même problème existe sur 1.8.9 avec
`GlStateManager`.

La règle : `RenderSystem.disableDepthTest()` / `enableDepthTest()`, qui font l'appel **et**
synchronisent le cache. Comme le shell ne peut pas dépendre de Minecraft, l'accès passe par
`DebugWorldRenderer.GlStateBridge`, que l'adaptateur de version implémente — ce qui rend au passage
le même code valable de 1.8.9 à 1.20.1 malgré le changement complet d'API de rendu.

### VAO dédié

`BoxRenderer` crée son propre VAO. Minecraft lie les siens ; modifier l'état de vertex attribs sans
VAO à soi corromprait son rendu à la frame suivante.

### Restauration dans un `finally`

```java
try { /* draw */ } finally { state.setDepthTest(true); glDepthFunc(GL_LEQUAL); ... }
```

Si un draw call lève, l'état doit malgré tout être restauré. Restaurer uniquement en chemin nominal
laisse le jeu avec le test de profondeur désactivé dès la première erreur — l'écran devient
incompréhensible et la cause est invisible dans la stacktrace.

### Épaisseur de trait

`glLineWidth` au-delà de 1.0 n'est pas garanti en profil core : la spec autorise une plage `[1,1]`,
que les pilotes AMD et Intel respectent souvent là où NVIDIA accepte davantage. Un trait épais qui
ne l'est que sur une carte sur trois n'est pas exploitable. Pour une épaisseur fiable, il faut
extruder des quads face caméra dans le vertex shader.

## Ce que l'outil mesure

- **Histogramme par type**, avec part ayant un renderer et part ticking.
- **Chunks saturés** : contour des colonnes dépassant 24 BE retenues. C'est la vue qui répond à
  « quel chunk fait chuter le framerate » — une ferme à hoppers concentre des centaines de BE dans
  deux chunks, et aucune moyenne globale ne le révèle. Le démonstrateur les retrouve correctement
  (chunks `(-5, 8)` à 165 et `(3, -2)` à 159).
- **BE avec renderer hors distance** : une valeur élevée signale un `getViewDistance` mal
  implémenté dans un mod tiers.
- **Son propre coût**, affiché en permanence et passé en rouge au-delà de 1 ms. Un diagnostic de
  performance qui ne s'inclut pas dans la mesure ment sur ce qu'il observe.

Mesure sur le monde synthétique (4 000 BE) : **308 µs par frame**, soit 1,9 % d'une frame à 60 fps.
Le chiffre est pris après 200 itérations d'échauffement — la première passe, dominée par la
compilation JIT, donne 8 ms, soit 25 fois trop.

## Points de coût à surveiller en production

L'implémentation de référence dans l'adaptateur alloue un `BlockEntitySnapshot` par BE et par
frame. Sur un monde à 20 000 BE, c'est ~1,2 Mo/frame, soit 70 Mo/s d'ordures : assez pour provoquer
des pauses GC que l'outil attribuerait ensuite au jeu. Deux corrections possibles :

1. réutiliser une instance mutable passée au visiteur ;
2. ne recollecter qu'une frame sur quatre en conservant l'agrégat entre-temps.

Le plafond `MAX_BOXES_PER_FRAME` (4 000) tronque l'**affichage** mais jamais le **comptage** —
sinon le diagnostic sous-estimerait précisément les scènes qui posent problème. Le HUD signale la
troncature.

## Raccourcis par défaut

| Raccourci | Action |
|---|---|
| `Ctrl+Maj+B` | activer / désactiver |
| `Ctrl+Maj+C` | filtre suivant |
| `Ctrl+Maj+D` | mode d'occlusion suivant |

Rappel : les chords sont enregistrés sur des **scancodes**, donc ces libellés correspondent aux
positions physiques QWERTY. Sur AZERTY, l'écran des contrôles affichera automatiquement les
lettres correspondantes (voir `docs/04-keybindings.md`).
