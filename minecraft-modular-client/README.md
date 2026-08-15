# Client Minecraft modulaire — proof-of-concept

POC d'architecture pour un client Java modulaire, multi-version, à interface personnalisée.
Projet personnel à but éducatif : exploration de l'isolation par classloader, du rendu 2D par
instanciation et de la résolution d'entrées.

## Vérifier tout de suite

Le cœur (`poc-api` + `poc-core`) n'a **aucune dépendance externe** et se compile avec un simple
`javac`. Le démonstrateur exécute la logique de keybinds et de résolution de modules sans contexte
graphique :

```bash
cd minecraft-modular-client
find poc-api poc-core -name '*.java' > /tmp/srcs.txt
javac -encoding UTF-8 -d /tmp/out @/tmp/srcs.txt
java -Dstdout.encoding=UTF-8 -cp /tmp/out dev.poc.core.Demo
```

Il vérifie six comportements :

1. trois binds sur la touche `G` — seul le bon se déclenche, et `Ctrl+G` n'active pas le bind `G` ;
2. la touche maintenue est bien relâchée quand le chat s'ouvre (bug de touche collée) ;
3. deux binds sur la même touche dans des contextes disjoints → 0 conflit ;
4. un id de keybind dupliqué échoue bruyamment au chargement ;
5. la sérialisation d'un chord est indépendante de la disposition clavier ;
6. tri topologique des modules, et cycle restitué avec son chemin exact.

`poc-ui` demande LWJGL 3 et passe par Gradle (`./gradlew :poc-ui:build`).

## Structure

```
poc-api/       Contrat partagé. Zéro dépendance : chargé par le classloader racine et
               visible depuis toutes les sessions de jeu.
               ├── module/  ClientModule, ModuleContext, GameBridge, ModuleMetadata
               ├── event/   EventBus, @Subscribe, GameEvents
               ├── input/   Keybind, Chord, ActivationMode, ActivationContext
               └── game/    GameAdapter, GameEnvironment

poc-core/      Shell. Zéro dépendance également (parseur JSON maison inclus).
               ├── module/  chargement, classloader parent-last, résolution semver
               ├── input/   KeybindRegistry (multimap), InputPipeline (résolveur)
               ├── event/   bus copy-on-write avec cache de dispatch
               ├── version/ VersionClassLoader, VersionSwitcher, SessionScope
               └── Demo.java

poc-ui/        LWJGL 3 / OpenGL 3.3 core. Renderer SDF instancié, animations à ressort,
               fenêtre et contexte GL possédés par le shell.

poc-adapters/  Un adaptateur par famille de version. Le SEUL code autorisé à importer
               net.minecraft.*. Découverte par ServiceLoader.

poc-modules/   Modules d'exemple. Ne référencent jamais le jeu, uniquement GameBridge —
               d'où un jar unique qui fonctionne de 1.8.9 à 1.20.1.
```

## Documentation

| Document | Contenu |
|---|---|
| [01 — Architecture modulaire](docs/01-architecture-modulaire.md) | contrat, contexte-scope, classloader, résolution de dépendances, bus |
| [02 — UI et rendu](docs/02-ui-rendering.md) | OpenGL vs Vulkan, SDF, instanciation, ressorts vs courbes, MSDF, dual-kawase |
| [03 — Changement de version](docs/03-version-switching.md) | **le morceau principal** : schéma d'architecture, contraintes JNI, remapping, séquence de bascule, fuites |
| [04 — Keybindings](docs/04-keybindings.md) | anatomie du bug `key.anything`, résolveur, contextes, scancodes, touches collées |

## Les quatre décisions qui structurent tout le reste

**1. Un classloader par version, en délégation parent-last.** C'est la seule primitive du JDK qui
permette de repartir d'un état statique vierge. 1.8.9 (Guava 17, Netty 4.0) et 1.20.1 (Guava 31,
Netty 4.1) ne se voient jamais.

**2. LWJGL et la fenêtre appartiennent au shell.** Une bibliothèque native ne peut être chargée que
par un seul classloader dans une JVM : un second `System.load` lève `UnsatisfiedLinkError`. La
fenêtre et le contexte GL survivent donc à toutes les bascules — d'où l'absence de clignotement, et
une UI de client qui continue de tourner pendant qu'aucune version n'est chargée.

**3. Les modules ne référencent jamais le jeu.** Ils passent par `GameBridge`. Seul l'adaptateur —
quelques milliers de lignes par famille de version — connaît `EntityPlayerSP` ou `LocalPlayer`.
Aucun remapping ne peut combler la différence de *structure* entre 1.8.9 et 1.20.1 ; l'abstraction,
si.

**4. Le contexte de module est un scope.** Tout ce qui est enregistré via lui est révoqué à sa
fermeture. C'est ce qui rend le déchargement fiable sans faire confiance au module — et sans
déchargement fiable, il n'y a pas de changement de version.

## État d'avancement

| Composant | État |
|---|---|
| Modèle de keybinds, résolveur, pipeline d'entrée | complet, exécutable et vérifié |
| Chargement de modules, classloader, résolution semver, bus | complet, exécutable et vérifié |
| Classloader de version, SessionScope, machine à états de bascule | complet, non testé contre un vrai jar Minecraft |
| Renderer SDF, animations, fenêtre GLFW | complet, nécessite LWJGL pour tourner |
| Adaptateurs de version | squelettes commentés — c'est le gros du travail restant |
| Provisionnement (téléchargement, remapping) | interface + pipeline documenté, implémentation à écrire |
| Rendu de texte | non fait, choix documenté (MSDF) |

## Ordre de travail conseillé

1. **Provisionnement 1.20.1** : manifeste → jar + libs + assets → remapping via tiny-remapper.
   Rien de conceptuellement difficile, mais c'est le prérequis de tout le reste.
2. **Adaptateur 1.20.1** : le mixin qui injecte le handle de fenêtre du shell dans
   `com.mojang.blaze3d.platform.Window` est le point délicat.
3. **Une seconde version LWJGL 3** (1.16.5 ou 1.19.4) : c'est là que les vraies fuites
   apparaissent. Outiller avec MAT dès le premier échec de collectabilité.
4. **1.8.9 en dernier**, et seulement si nécessaire : la réécriture LWJGL 2 → 3 est à elle seule
   plus lourde que les trois étapes précédentes. Étudier `lwjgl3ify` avant d'écrire une ligne.
