# PoC — client Minecraft modulaire

Squelette de client custom en Java 21 : gestionnaire de modules à chaud, bus d'événements scopé, UI
NanoVG animée, gestionnaire de keybinds sans collisions, et l'ossature d'un sélecteur de version
in-game.

Le code compile et la démo tourne (`gradle runDemo`, vérifié). Les commentaires du code sont en
anglais — convention Java — la doc est en français.

---

## Démarrer

```bash
cd minecraft-client-poc
gradle runDemo     # parcours headless : modules, keybinds, conflits, swap de version simulé
gradle runUi       # ouvre le menu principal NanoVG (nécessite un écran + GPU GL 3.2)
```

`runDemo` ne demande ni GPU ni installation de Minecraft. Sortie attendue (extrait) :

```
== 1. modules ==================================================
  rejected broken: missing dependency 'does-not-exist'
  core-hud 1.0.0 [ENABLED]
  waypoints 1.0.0 [ENABLED]

== 3. deliberate conflict, then resolution =====================
  conflict G -> [core-hud:toggle-hud, waypoints:toggle-list]
  press G        -> consumed=true  (highest priority consumed it; the other never fired)
  rebindExclusive kept waypoints on G and unbound [core-hud:toggle-hud = NONE ...]

== 4. disabling a module removes its handlers and binds ========
  disabled: [waypoints, core-hud]  (waypoints depends on it, so it went first)
  handlers left: 0, binds left: 0
```

---

## 1. Architecture modulaire

```
dev.poc.client.module
├── ModuleDescriptor      manifeste module.properties (id, main, depends, api-version)
├── Module                onLoad → onEnable → onDisable → onUnload
├── ModuleContext         la SEULE capacité donnée au module ; tout ce qui passe par lui est scopé
├── ModuleClassLoader     un loader par module, ordre de délégation explicite
├── DependencyResolver    tri topologique, cycles et deps manquantes rapportés (jamais silencieux)
└── ModuleManager         discover → resolve → load → enable/disable/unload
```

Trois décisions portent tout le reste.

**a) Le module ne s'enregistre jamais lui-même.** Il reçoit un `ModuleContext` et passe par lui pour
s'abonner aux événements et poser des keybinds. Désactiver un module devient donc un balayage côté
hôte (`eventBus.unregisterAll(id)` + `keybinds.unregisterAll(id)`) au lieu d'une promesse que l'auteur
du mod doit tenir. C'est la différence entre « on peut désactiver un mod » et « on peut désactiver un
mod *bien écrit* ».

**b) L'ordre de délégation du ClassLoader est explicite** — et c'est là que 90 % des bugs de
plateforme à mods se logent :

1. *parent d'abord* pour le JDK, LWJGL et `dev.poc.client.api.*` → sans ça, le `Module` du mod n'est
   pas le `Module` de l'hôte, et le cast échoue avec le fameux
   `ClassCastException: Module cannot be cast to Module` ;
2. *soi-même* ensuite, pour qu'un module puisse embarquer sa propre version d'une lib ;
3. *dépendances déclarées* (via `findLocal`, qui ne re-délègue pas → pas de `StackOverflowError` sur
   un cycle) ;
4. *parent* en dernier recours.

**c) Le déchargement est honnête sur ses limites.** `close()` sur un `URLClassLoader` libère le
handle du jar (donc on peut remplacer le fichier sous Windows), mais la mémoire n'est rendue que si
plus rien ne référence ses classes. Les enregistrements scopés couvrent les fuites habituelles ; un
module qui gare une référence statique dans une classe partagée épingle quand même son loader. C'est
exactement pourquoi Forge a abandonné le rechargement de mods à chaud. Ici on garde la porte ouverte
et on rapporte, plutôt que de prétendre que le problème n'existe pas.

### Écrire un module

`module.properties` à la racine du jar :

```properties
id           = waypoints
name         = Waypoints
version      = 1.2.0
main         = com.example.waypoints.WaypointsModule
api-version  = 1
depends      = core-hud
soft-depends = shader-api
```

```java
public final class WaypointsModule implements Module {
    @Override public void onEnable(ModuleContext ctx) {
        ctx.subscribe(this);                       // dropped automatically on disable
        ctx.bindKey("toggle-list", "Waypoints",
                KeyChord.of(Keys.codeOf("B")),
                KeyContext.IN_GAME,
                KeybindManager.Activation.PRESS,
                h -> ctx.log("toggled"));
    }

    @Subscribe(priority = Subscribe.MONITOR)
    public void onTick(ClientEvents.Tick tick) { /* … */ }
}
```

---

## 2. Interface

```
dev.poc.client.ui
├── Theme            tokens couleur + métriques ; un thème utilisateur = un record échangé
├── Easings          courbes d'interpolation
├── Animated         valeur animée : ressort (interruptible) OU tween (durée fixe)
├── NanoVgRenderer   rect arrondis, dégradés, ombres douces, texte
├── Widget           contrat minimal
├── AnimatedButton   hover / press / entrée, trois animateurs indépendants
├── MainMenuScreen   fond animé, entrée décalée, pilule de statut + progression du swap
└── ClientWindow     le shell : GLFW, contexte GL, routage des entrées
```

**NanoVG plutôt que du GL brut.** Une UI de client, c'est des rectangles arrondis, des dégradés, des
ombres et du texte. Le faire en GL direct veut dire écrire un rasteriseur de chemins et un moteur de
texte — un projet en soi. NanoVG est une lib C avec binding LWJGL, dessine dans le contexte GL3
existant, et coûte une poignée de draw calls par frame.

**Pas de Vulkan.** La fenêtre et le contexte sont partagés avec le jeu, et Minecraft est une
application GL. Introduire Vulkan implique soit une seconde fenêtre, soit de l'interop, pour une UI
qui n'est absolument pas GPU-bound. Le seul cas où ça se justifie est un client qui remplace aussi le
renderer du jeu (cf. VulkanMod), ce qui est un autre projet.

**Ressort vs tween** — la distinction qui fait la différence perçue. Tout ce que l'utilisateur peut
interrompre (hover, sélection, drag) doit être un ressort : re-cibler en plein vol est continu, donc
sortir la souris d'un bouton à mi-animation repart de la valeur réelle. Un tween redémarre avec une
durée neuve et saccade visiblement. Les tweens sont réservés aux mouvements scriptés (entrée
décalée, transition d'écran) où la durée exacte est le sujet.

Détail d'implémentation qui compte : le ressort s'intègre en sous-pas fixes de 1/240 s. Intégrer avec
le delta brut rend la raideur dépendante du framerate, et une frame longue (un swap de version, un
build de chunk) suffit à faire diverger l'intégrateur.

**Trois animateurs par bouton**, pas un seul état : hover et press se chevauchent (un clic pendant
que le hover s'installe encore), et l'entrée est un one-shot qui ne doit être relancé par aucun des
deux. Les fusionner en une seule valeur de progression est la raison habituelle des boutons qui
« sautent » quand on clique vite.

---

## 3. Sélecteur de version in-game

→ **[docs/ARCHITECTURE-version-switching.md](docs/ARCHITECTURE-version-switching.md)** (schémas,
contraintes JNI/LWJGL, stratégie de mappings, layout du store).

Résumé : le shell possède la fenêtre GLFW et ne meurt jamais ; chaque version vit dans un
ClassLoader jetable (ou un process enfant) ; les modules compilent contre une façade
`dev.poc.client.api` et un adapter par version fait la traduction vers les noms obfusqués. Le
coordinateur découpe le swap en une phase préparatoire off-thread (téléchargement, remap,
construction du loader — pendant que le jeu rend encore) et une phase courte sur le thread de rendu
(`detach` / `attach`), avec rollback vers l'ancienne version si l'attache échoue.

Le point à connaître avant de commencer : **1.8.9 tourne sur LWJGL 2, 1.20.1 sur LWJGL 3**, et JNI
interdit de charger la même lib native dans deux ClassLoaders. Le saut entre ces deux versions dans
un même process demande de porter les anciennes versions sur LWJGL 3 au préalable — cf. LWJGL3ify.
Sinon, un process JVM par version, ce que font les clients commerciaux.

Code : [`VersionSwitchCoordinator`](src/main/java/dev/poc/client/version/VersionSwitchCoordinator.java),
[`IsolatedVersionRuntime`](src/main/java/dev/poc/client/version/IsolatedVersionRuntime.java),
[`AssetStore`](src/main/java/dev/poc/client/version/AssetStore.java),
[`RenderThreadExecutor`](src/main/java/dev/poc/client/version/RenderThreadExecutor.java).

---

## 4. Non fourni

Le module d'ESP/wireframe de conteneurs à travers la géométrie n'est pas dans ce PoC, et le
paramètre `donut-smp-base-detection-v1` n'existe nulle part dans le code. Un rendu de coffres
traversant l'occlusion sur un serveur multijoueur, c'est du X-ray : l'emballage « diagnostic de
rendu » ne change ni ce que fait le code ni sur qui il le fait.

Le reste du cahier des charges est traité intégralement, et le point 5 ci-dessous inclut le bug
`key.anything` demandé.

---

## 5. Gestionnaire de keybindings

```
dev.poc.client.keybind
├── Keys             codes GLFW + noms ; les boutons souris partagent l'espace (offset 1000)
├── KeyChord         touche + masque de modificateurs, parse/format « CTRL+SHIFT+G »
├── KeyContext       ANY / IN_GAME / IN_SCREEN / IN_TEXT_INPUT
├── KeybindHandle    identité = owner:localId ; priorité, passthrough, état pressed
└── KeybindManager   registre, dispatch, conflits, persistance
```

### Le bug `key.anything`, concrètement

Vanilla indexe les `KeyBinding` par leur **description** dans un `Map<String, KeyBinding>` statique
(`KEY_BIND_MAP`), et `options.txt` écrit ses lignes sous cette même chaîne. Deux mods qui utilisent
une description générique — `key.anything` étant l'exemple folklorique, copié-collé de tuto en tuto —
s'écrasent : la seconde inscription remplace la première dans la map, `options.txt` ne porte qu'une
ligne pour les deux, et `isKeyDown()` du perdant renvoie `false` **pour toujours**, sans la moindre
erreur nulle part. Les versions anciennes ajoutent un second index statique par code de touche, donc
deux binds sur la même touche physique en perdent un aussi.

### Les quatre décisions qui rendent ce bug irreprésentable

1. **L'identité est `owner:localId`**, attribuée par l'hôte à partir de l'id du module. Un module ne
   peut pas choisir un id qui entre en collision avec un autre module ; un doublon *interne* lève une
   exception à l'enregistrement au lieu d'échouer silencieusement à l'exécution.
2. **Le nom affiché est décoratif.** Deux binds peuvent s'appeler « Toggle ». Rien n'indexe sur le
   label.
3. **L'état `pressed` vit sur le handle**, alimenté par les callbacks GLFW bruts. Aucun index statique
   partagé à écraser. Partager un chord devient une *configuration supportée*, résolue par contexte
   puis priorité, et non un accident qui désactive quelqu'un en silence.
4. **La persistance est indexée par id**, et les bindings d'ids non chargés sont **conservés** au lieu
   d'être jetés — désactiver un mod une session ne fait plus perdre ses binds personnalisés.

### Les autres pièges traités

- **Correspondance exacte des modificateurs.** `G` et `CTRL+G` sont deux chords distincts. Avec une
  correspondance par sous-ensemble (ce que fait le jeu), ajouter une variante modifiée d'un bind
  existant déclenche les deux actions.
- **Le relâchement est apparié sur le code de touche seul, jamais sur les modificateurs.** Appuyer
  `CTRL+G`, relâcher `CTRL` d'abord, puis `G` : le release arrive avec `mods = 0`. Un dispatch qui
  vérifie le chord au relâchement ne matche pas et le bind reste enfoncé indéfiniment. C'est *la*
  cause des touches bloquées.
- **`releaseAll()` sur perte de focus**, branché sur `glfwSetWindowFocusCallback`. Sans ça, alt-tab
  pendant qu'un bind est maintenu le laisse maintenu.
- **Caps Lock et Num Lock masqués** du masque de modificateurs. GLFW les rapporte (0x10, 0x20) ; un
  bind qui cesse de marcher parce que Caps Lock est actif est indiscernable, pour l'utilisateur, d'un
  mod cassé.
- **L'auto-repeat (`action == 2`) n'est pas une nouvelle pression.** Les binds `HOLD` sont pilotés par
  `tick()`, pas par la répétition clavier — dont la fréquence dépend de l'OS.
- **Ordre déterministe** : priorité décroissante, puis ordre d'enregistrement. Deux lancements du
  client résolvent un conflit de la même façon.

### API de conflit

```java
List<Conflict> conflicts = keybinds.rebind("waypoints:toggle-list", KeyChord.parse("CTRL+G"));
// rebind réussit toujours et retourne les conflits — c'est l'UI qui décide quoi en faire

List<KeybindHandle> displaced = keybinds.rebindExclusive("waypoints:toggle-list", chord);
// variante « fais que ça marche » : débind ce qui gênait et retourne la liste
```

Deux binds ne sont en conflit que si leurs contextes peuvent se recouvrir — `E` peut signifier
« ouvrir l'inventaire » en jeu et « fermer l'écran » dans une GUI sans que ce soit un bug.

---

## Librairies conseillées

| Besoin | Choix | Note |
|---|---|---|
| Fenêtre, GL, UI, audio | **LWJGL 3** (`glfw`, `opengl`, `nanovg`, `stb`, `openal`) | déjà câblé dans le `build.gradle.kts` |
| Injection dans le jeu | **SpongePowered Mixin** + **MixinExtras** | ne pas écrire d'ASM à la main |
| Mappings | **mapping-io**, **tiny-remapper** (FabricMC) | lecture/conversion/remap tiny, tsrg, proguard |
| Config | **Gson** ou **Jankson** | Jankson garde les commentaires — appréciable pour un fichier édité à la main |
| Logs | **SLF4J + Log4j2** | Minecraft embarque déjà Log4j2 |
| UI de debug | **imgui-java** | pour les outils de dev, pas pour l'UI utilisateur |
| Alternative UI | **Skija** (binding Skia) | plus riche que NanoVG, binaire beaucoup plus lourd |
| HTTP / téléchargements | `java.net.http.HttpClient` | suffisant, `sendAsync` + `HttpResponse.BodyHandlers.ofInputStream` |
| Infos matériel | **OSHI** | pour un overlay FPS/CPU/RAM |

À éviter : réécrire un moteur de texte (utiliser NanoVG ou du MSDF via stb), et embarquer JCEF pour
l'interface — c'est ~150 Mo et un process de plus pour dessiner des boutons.

---

## Ce qui reste à faire

- `dev.poc.client.api` — la façade de jeu (interfaces `Player`, `World`, `Screen`…) et un adapter de
  référence. Le PoC pose la mécanique de chargement, pas encore la surface d'API.
- Fournir un vrai `VersionManifest` depuis `version_manifest_v2.json` (le PoC en fabrique un factice).
- Le téléchargement parallèle dans `AssetStore` (le `put` unitaire est écrit et vérifie le SHA-1).
- Le fallback processus enfant (`ProcessVersionRuntime`) décrit dans la doc d'architecture.
- Tests : `DependencyResolver` (cycles, deps transitives) et `KeybindManager` (touches bloquées) sont
  du code pur, donc testables sans GPU — c'est là qu'il faut mettre les tests en premier.
