# 3. Sélecteur de version in-game

Le défi central : passer de 1.8.9 à 1.20.1 sans relancer le processus.

## La contrainte qui détermine toute l'architecture

On ne peut pas « recharger » Minecraft dans le même classloader. Le jeu est saturé d'état
statique — `Minecraft.getInstance()`, les registres de blocs et d'items, les caches de textures,
les singletons du moteur de rendu. Une classe déjà chargée ne peut être ni redéfinie ni
réinitialisée depuis Java standard.

La seule primitive du JDK qui permette de repartir de zéro est l'**abandon d'un classloader** :
quand plus aucune référence forte ne pointe vers lui, le GC récupère le loader, ses classes et
tous leurs champs statiques. C'est le pivot du système. Tout le reste — arrêt des threads, purge
des handlers, arène GL — n'existe que pour rendre cet abandon possible.

## Architecture en trois couches

```
┌──────────────────────────────────────────────────────────────────────┐
│ COUCHE 1 — SHELL           (classloader applicatif, vit pour toujours)│
│                                                                      │
│  Fenêtre GLFW + contexte OpenGL  ←── créés UNE fois, jamais détruits  │
│  LWJGL 3 + natifs                ←── chargés UNE fois (contrainte JNI)│
│  poc-api  (GameBridge, GameAdapter, Keybind…)                        │
│  UI du client, InputPipeline, ModuleManager, VersionSwitcher          │
│  Compte, config, profil de keybinds                                  │
└───────────────┬──────────────────────────────────────────────────────┘
                │ ne connaît QUE GameAdapter + GameBridge
                │ (surface étroite = peu d'occasions de fuite)
   ┌────────────┴─────────────┐        ┌──────────────────────────┐
   ▼                          ▼        ▼                          ▼
┌────────────────────────┐ ┌────────────────────────┐  ┌────────────────────┐
│ COUCHE 2 — VERSION     │ │ COUCHE 2 — VERSION     │  │ COUCHE 3 — MODULES │
│ VersionClassLoader     │ │ VersionClassLoader     │  │ ModuleClassLoader  │
│  « mc:1.8.9 »          │ │  « mc:1.20.1 »         │  │  parent-last       │
│                        │ │                        │  │                    │
│ client-poc.jar remappé │ │ client-poc.jar remappé │  │ hud-example.jar    │
│ Guava 17, Gson 2.2.4   │ │ Guava 31, Gson 2.10    │  │ ne voit QUE poc-api│
│ Netty 4.0.23           │ │ Netty 4.1.82           │  │                    │
│ Adapter189             │ │ Adapter1201            │  │ → 1 jar, toutes    │
│ + shim LWJGL2→3        │ │                        │  │   les versions     │
└────────────────────────┘ └────────────────────────┘  └────────────────────┘
   un seul est vivant à la fois ; l'autre a été abandonné au GC
```

Les deux versions n'existent jamais simultanément — le schéma montre l'avant et l'après d'une
bascule. Ce qu'il faut lire : **Guava 17 et Guava 31 coexisteraient sans se voir**, parce que la
délégation est parent-last. C'est ce qui rend le multi-version possible ; en parent-first, la
seconde version exploserait sur des `NoSuchMethodError`.

## Les trois contraintes dures

### 1. Les natifs ne se chargent qu'une fois par JVM

Une bibliothèque native est liée au classloader qui l'a chargée. Un second `System.load()` de la
même `.so`/`.dll` depuis un autre loader lève :

```
UnsatisfiedLinkError: Native Library /…/liblwjgl.so already loaded in another classloader
```

Donc LWJGL vit dans le shell, en un seul exemplaire, et chaque version l'emprunte via la liste
blanche parent-first de `VersionClassLoader`.

**Conséquence directe** : toutes les versions doivent tourner sur LWJGL 3. Or 1.12.2 et
antérieures ciblent LWJGL 2 et son API `Display`/`Keyboard`/`Mouse`. Il faut réécrire ces appels
au chargement — c'est précisément ce que fait le projet open-source **lwjgl3ify** (écosystème
GTNH), qui est la référence à étudier avant d'écrire quoi que ce soit. Sur 1.8.9 c'est plusieurs
milliers de sites d'appel : une passe ASM systématique, pas des mixins.

C'est de loin le poste de travail le plus lourd du projet. Si l'objectif est de démarrer vite,
**commencer par 1.16 → 1.20** (toutes en LWJGL 3, aucun shim) et ajouter 1.8.9 ensuite.

### 2. Un seul contexte OpenGL, possédé par le shell

L'adaptateur ne crée jamais de fenêtre. Il reçoit le handle GLFW via `GameEnvironment` et un mixin
détourne la création de `com.mojang.blaze3d.platform.Window` pour lui injecter ce handle. Sans
cela : deux fenêtres, un clignotement à chaque bascule, et un second contexte GL à synchroniser.

Corollaire : les objets GL doivent être libérés explicitement à l'arrêt. Minecraft laisse traîner
textures et FBO — sans importance quand le processus s'arrête juste après, fatal quand on enchaîne
dix bascules dans la même JVM. D'où `GameEnvironment.GlArena`, qui trace les allocations et les
libère de force.

### 3. Le remapping se fait à l'installation, jamais à la bascule

Remapper le client 1.20.1 prend 10 à 30 s. Le faire au clic rendrait la fonctionnalité
inutilisable. Pipeline d'installation :

```
version_manifest_v2.json (piston-meta)
        │
        ▼
JSON de version ──► client.jar + libraries[] (filtrées par règles d'OS) + assetIndex
        │
        ▼
mappings :  Mojmap (1.14.4+, ProGuard)  │  Yarn (1.14+, tiny v2)  │  MCP/Searge (1.8.9, 1.12.2)
        │
        ▼
tiny-remapper : obfusqué ──► espace de noms unifié
        │
        ▼
cache : versions/<id>/client-poc.jar  (+ empreinte : hash jar ⊕ hash mappings ⊕ version remapper)
```

Assets : store partagé adressé par hash (`assets/objects/ab/abcdef…`), exactement comme le
launcher officiel. Deux versions qui partagent un index ne téléchargent rien deux fois. Attention
au cas 1.8.9, qui exige une arborescence `assets/virtual/legacy/` reconstruite à plat depuis
l'index.

## La limite qu'il faut accepter

Un espace de noms unifié n'unifie pas la **structure** du jeu. `EntityPlayerSP` (1.8.9) et
`LocalPlayer` (1.20.1) ne sont pas la même classe sous un autre nom : hiérarchie, champs et
pipeline de rendu ont changé. Aucun remapping ne comble cet écart.

D'où le choix d'architecture : les modules ne touchent jamais aux classes du jeu, ils passent par
`GameBridge`. Seul l'adaptateur — quelques milliers de lignes par famille de version — connaît les
détails. C'est ce qui permet à `hud-example.jar` de fonctionner de 1.8.9 à 1.20.1 sans
recompilation, et c'est le seul compromis qui reste maintenable dans la durée.

## Séquence de bascule

L'ordre n'est pas négociable ; chaque inversion produit soit une fuite, soit un crash natif.

```
 1. Mémoriser le contexte à restaurer (adresse du serveur)   ← avant de perdre le pont
 2. Décharger les modules                                    ← ils référencent le jeu
 3. adapter.shutdown()                                       ← réseau, puis executors, puis rendu
 4. glArena.freeAll()                                        ← pendant que le loader vit encore
 5. scope.stopThreads(3s)                                    ← interrompre + join + signaler
 6. bus.purgeClassLoader(loader)                             ← filet de sécurité
 7. loader.close(), couper toutes les références
 8. Vérifier la collectabilité (PhantomReference + System.gc)
 9. Nouveau VersionClassLoader + ServiceLoader<GameAdapter>
10. adapter.boot(env)  → reconnexion automatique au serveur
11. Recharger les modules, filtrés par compatibilité de version
```

Tout s'exécute sur le **thread principal**, celui qui possède le contexte GL. Un arrêt concurrent
au rendu produit des crashs natifs indébogables. Le coût est une pause de 2 à 4 s, masquée par un
overlay dessiné par le shell — qui, lui, survit à la bascule puisqu'il n'appartient à aucune
version.

## Les six voies de fuite à surveiller

Une seule suffit à retenir ~150 Mo de métaspace par bascule, jusqu'à l'`OutOfMemoryError`.

| Voie | Symptôme | Traitement |
|---|---|---|
| Threads (Netty, chunk builder, audio) | métaspace qui monte | `SessionScope.stopThreads`, puis signaler les récalcitrants |
| `Runtime.addShutdownHook` | référence jusqu'à la fin du processus | proscrire dans les adaptateurs |
| ThreadLocals posés sur le thread principal | fuite silencieuse | retirer explicitement |
| Caches JDK (`Introspector`, `ImageIO`, `ResourceBundle`) | une ouverture de PNG suffit | `SessionScope.clearJdkCaches` |
| Callbacks GLFW enregistrés par la version | UI du client qui cesse de répondre | le shell possède les callbacks, il route |
| Objets GL | VRAM qui ne redescend jamais | `GlArena.freeAll` |

Outillage : `-XX:+HeapDumpOnOutOfMemoryError`, puis Eclipse MAT sur le dump avec un filtre « chemin
vers les GC roots » depuis l'instance de `VersionClassLoader`. C'est le seul moyen fiable de
trouver la référence coupable.

## Plan B : un processus par version

Si l'isolation en un seul processus s'avère trop coûteuse à stabiliser, l'alternative est une JVM
enfant par version, pilotée par IPC (socket Unix / named pipe).

- **Pour** : isolation parfaite, 1.8.9 peut garder LWJGL 2 (plus de shim à écrire), un crash de
  version ne tue pas le client.
- **Contre** : +200 à 400 Mo de RSS, et surtout le problème de la fenêtre. Le reparentage natif
  (`SetParent` sur Windows, `XReparentWindow` sur X11, rien de propre sur Wayland ni macOS) est
  fragile. L'option praticable est un overlay transparent toujours au-dessus, avec routage manuel
  des entrées — ce qui déplace la complexité plutôt que de la supprimer.

Mon avis : le mono-processus est le bon choix si l'on se limite d'abord à 1.16+. Le multi-processus
devient intéressant seulement si le support de 1.8.9 est non négociable dès le départ.
