# Sélecteur de version in-game — architecture

C'est le point dur du cahier des charges, et la raison est simple : **Minecraft n'a jamais été écrit
pour être déchargé**. Tout l'état du jeu est statique (le singleton `Minecraft`, les registries de
blocs/items, les atlas de textures, les caches de modèles), et il n'existe aucun chemin de code qui
le remette à zéro. Toute solution consiste donc à *jeter le namespace entier* plutôt qu'à le
réinitialiser.

---

## 1. Les cinq contraintes réelles

| # | Contrainte | Conséquence |
|---|---|---|
| 1 | **État statique non réinitialisable** | On ne peut pas « redémarrer » une version en place. Il faut un ClassLoader (ou un process) jetable. |
| 2 | **JNI : une lib native = un seul ClassLoader** | Charger deux fois `lwjgl.so` lève `UnsatisfiedLinkError: Native Library ... already loaded in another classloader`. Et JNI ne décharge jamais avant GC du loader. |
| 3 | **LWJGL 2 vs LWJGL 3** | 1.8.9 utilise LWJGL 2 (`org.lwjgl.opengl.Display`, qui crée *sa propre* fenêtre). 1.13+ utilise LWJGL 3 / GLFW. Les deux ne cohabitent pas dans un même process de façon fiable. |
| 4 | **Le contexte GL appartient à un thread et à un process** | Une fenêtre GLFW ne se transmet pas d'un process à un autre. Le partage de contexte GL est intra-process uniquement. |
| 5 | **Mappings différents par version** | `net.minecraft.client.Minecraft` en mojmap 1.20.1 n'a rien à voir avec `bao` en 1.8.9. Un module compilé une fois ne peut pas taper directement dans le jeu. |

La contrainte 3 est celle qui tue la solution naïve. Elle a une réponse connue :
**normaliser toutes les versions sur LWJGL 3** avec un backport — c'est exactement ce que fait
[LWJGL3ify](https://github.com/GTNewHorizons/lwjgl3ify) (projet GTNH) pour faire tourner 1.7.10 et
1.8.9 sur LWJGL 3 + Java 17+. Sans ce travail préalable, le saut 1.8.9 ↔ 1.20.1 *dans le même
process* n'est pas faisable.

---

## 2. Deux architectures possibles

### Architecture A — un seul process, un ClassLoader par version

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Process unique (le « shell »)                                            │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ System ClassLoader — ne change JAMAIS de toute la session           │  │
│  │   org.lwjgl.*  (GLFW, GL, NanoVG)   ← natives chargées UNE fois     │  │
│  │   dev.poc.client.api.*              ← la façade que voient les mods │  │
│  │   dev.poc.client.{module,keybind,event,ui,version}.*                │  │
│  │   → possède : la fenêtre GLFW, le contexte GL, l'UI, l'audio,       │  │
│  │     la session de compte, la config, les keybinds                   │  │
│  └──────────────────────────┬─────────────────────────────────────────┘  │
│                             │ parent                                     │
│         ┌───────────────────┴────────────────────┐                       │
│         ▼                                        ▼                       │
│  ┌──────────────────────┐              ┌──────────────────────┐          │
│  │ URLClassLoader       │              │ URLClassLoader       │          │
│  │ "mc:1.8.9"  ACTIVE   │              │ "mc:1.20.1" STANDBY  │          │
│  │  client-1.8.9.jar    │              │  client-1.20.1.jar   │          │
│  │  libs 1.8.9          │              │  libs 1.20.1         │          │
│  │  adapter-1.8.9.jar ──┼── implements │  adapter-1.20.1.jar  │          │
│  │  (état statique MC)  │   api.*      │  (état statique MC)  │          │
│  └──────────────────────┘              └──────────────────────┘          │
│         ▲                                        ▲                       │
│         └──── close() → tout l'état part au GC ──┘                       │
└──────────────────────────────────────────────────────────────────────────┘
```

**Ce qu'on gagne :** la fenêtre, l'UI du client, les polices, les atlas UI, le device audio et la
session survivent au swap. L'interface ne clignote jamais. Le swap coûte ~1 à 3 s (le temps du
`detach`/`attach`), et quasiment 0 si la cible est déjà en `STANDBY`.

**Ce que ça impose :**
- toutes les versions doivent tourner sur **le même LWJGL** (cf. LWJGL3ify pour les vieilles) ;
- `detach()` doit supprimer **tous** les objets GL de la version (VAO, textures, shaders, FBO). Ils
  vivent sur le GPU, pas dans le heap : le GC du ClassLoader ne les libère pas. Trois swaps qui
  fuient = VRAM saturée ;
- aucune classe de version ne doit être référencée depuis le shell (sinon le loader ne meurt jamais).

Implémentation : [`IsolatedVersionRuntime`](../src/main/java/dev/poc/client/version/IsolatedVersionRuntime.java).

### Architecture B — un process JVM par version

```
┌─────────────────────────────┐        IPC (socket local / stdio)
│ Shell (process parent)      │◄──────────────────────────────────────┐
│  UI du client (NanoVG)      │                                       │
│  téléchargements + store    │        ┌──────────────────────────┐   │
│  config, comptes, keybinds  │───────►│ JVM enfant « 1.8.9 »     │───┘
│  PAS de contexte GL du jeu  │  spawn │  Java 8, LWJGL 2         │
└─────────────────────────────┘        │  sa propre fenêtre       │
                                       └──────────────────────────┘
                                       ┌──────────────────────────┐
                              warm ───►│ JVM enfant « 1.20.1 »    │
                                       │  Java 21, LWJGL 3        │
                                       │  préchargée, écran noir  │
                                       └──────────────────────────┘
```

**Ce qu'on gagne :** aucune des contraintes 1 à 4 ne s'applique. Chaque version a son JVM, sa version
de Java, son LWJGL, ses natives. C'est robuste par construction.

**Ce qu'on perd :** la fenêtre appartient à l'enfant. Le swap est visuellement une relance (fenêtre
qui disparaît/réapparaît). Le ré-parentage natif d'une fenêtre entre process existe (Win32
`SetParent`, XEmbed sous X11) mais c'est du code par plateforme, cassé sous Wayland, et fragile.

**C'est ce que font les clients commerciaux.** Lunar Client a un launcher persistant et un process
de jeu par version ; changer de version relance le process de jeu, pas le launcher.

### Recommandation

Commencer par **B**, garder l'API du coordinateur identique, migrer vers **A** version par version
une fois qu'elles tournent toutes sur LWJGL 3. `VersionSwitchCoordinator` est déjà écrit pour ça :
`VersionRuntime` est une interface, seule l'implémentation change. On peut même mixer — A pour la
famille 1.16→1.21, B pour 1.8.9 tant qu'elle n'est pas portée.

---

## 3. Séquence d'un swap

```
 t │ thread    │ phase      │ ce qui se passe                          │ jeu visible ?
───┼───────────┼────────────┼──────────────────────────────────────────┼──────────────
 0 │ worker    │ RESOLVING  │ manifest de la version cible             │ oui, 60 fps
 1 │ worker    │ FETCHING   │ artefacts manquants → AssetStore (sha1)  │ oui, 60 fps
 2 │ worker    │ PREPARING  │ ClassLoader, remap, pré-link adapter     │ oui, 60 fps
───┼───────────┼────────────┼──────────────────────────────────────────┼──────────────
 3 │ render    │ DRAINING   │ modules désactivés, état sauvegardé      │ figé
 4 │ render    │ DETACHING  │ suppression des objets GL, libération    │ écran noir
 5 │ render    │ ATTACHING  │ la cible prend la fenêtre et s'initialise │ écran noir
 6 │ render    │ RESUMING   │ modules ré-activés sur le nouvel adapter │ figé
───┼───────────┼────────────┼──────────────────────────────────────────┼──────────────
```

Les phases 0-2 tournent **pendant que la version courante rend encore**. Seules 3-6 sont visibles.
`warm(versionId)` déplace 0-2 hors du chemin critique : on l'appelle quand l'utilisateur *ouvre* le
menu de versions, pas quand il clique.

Voir [`VersionSwitchCoordinator`](../src/main/java/dev/poc/client/version/VersionSwitchCoordinator.java),
y compris le rollback : si `attach()` échoue, l'ancien runtime est ré-attaché plutôt que de laisser
une fenêtre morte.

---

## 4. Mappings : ne jamais compiler les modules contre le jeu

Le piège classique est de laisser les modules importer `net.minecraft.*`. Un module devient alors
lié à une version, et « supporter 1.8.9 → 1.20.1 » veut dire 12 builds par module.

La bonne structure est une **façade + un adapter par version** :

```
   module (compilé UNE fois)
        │  import dev.poc.client.api.Player;
        ▼
   dev.poc.client.api.*         ← interfaces pures, dans le loader parent, stables
        ▲            ▲
        │            │  implements
   adapter-1.8.9  adapter-1.20.1   ← un petit jar par version, dans le loader de version
        │            │
        ▼            ▼
   bao.class     net.minecraft.client.player.LocalPlayer
```

- **Les modules ne voient que `dev.poc.client.api`.** Ces interfaces sont chargées par le parent (cf.
  `SHARED_PREFIXES` dans [`ModuleClassLoader`](../src/main/java/dev/poc/client/module/ModuleClassLoader.java)),
  donc le même type est vu par le shell, l'adapter et le module.
- **Chaque adapter est compilé contre sa version**, avec ses propres mappings. C'est le seul endroit
  du projet où le nom `net.minecraft` apparaît.
- Après un swap, `SwapHooks.afterAttach` rebranche les modules sur le nouvel adapter. Un module qui
  a mis en cache un objet de l'ancien adapter doit le relâcher dans `onDisable` — c'est justement
  pour ça que le coordinateur désactive les modules avant le detach.

**Outils de mapping :**

| Besoin | Outil |
|---|---|
| Lire/convertir des mappings (tiny, tsrg, proguard) | `net.fabricmc:mapping-io` |
| Remapper un jar au build ou à chaud | `net.fabricmc:tiny-remapper` |
| Mappings intermédiaires stables | Fabric intermediary (via `net.fabricmc:intermediary`) |
| Mappings lisibles modernes | Mojmap (`client.txt` proguard, distribué par Mojang) ou Yarn |
| Vieilles versions (≤ 1.12) | MCP / SRG — c'est la seule option réaliste sur 1.8.9 |
| Injection de code dans le jeu | `org.spongepowered:mixin` + `io.github.llamalad7:mixinextras` |

Note pratique : `tiny-remapper` peut aussi remapper le bytecode d'un module au chargement, ce qui
permet d'écrire des modules contre les noms intermediary plutôt que contre une façade. C'est ce que
fait Fabric. C'est plus souple mais ça ne franchit pas la frontière 1.8.9 ↔ 1.20.1, où ce ne sont pas
les *noms* qui changent mais les *signatures et l'architecture du rendu*. Pour du multi-version large,
la façade reste la seule approche qui tient.

---

## 5. Assets, librairies, natives

`AssetStore` est adressé par contenu (SHA-1), comme le launcher officiel :

```
~/.poc-client/
├── objects/                     partagé par toutes les versions
│   ├── a3/a3f1c2…               lwjgl-glfw-3.3.3.jar
│   └── 7d/7d9e04…               minecraft/sounds/…
├── versions/
│   ├── 1.8.9/                   hardlinks vers objects/ + natives extraites
│   └── 1.20.1/
└── modules/
```

Points qui comptent en pratique :

- **Hardlink, pas copie.** Huit versions installées passent de ~8 Go à ~2 Go, et « matérialiser » une
  version coûte quelques millisecondes au lieu de plusieurs centaines de Mo de I/O.
- **Écriture en `.part` puis `move` atomique.** Un téléchargement interrompu ne doit jamais pouvoir
  passer pour un objet complet au lancement suivant — c'est la cause n°1 des « j'ai réinstallé, ça
  marche » dans les launchers.
- **Vérification SHA-1 au stockage, pas au chargement.** On paye le hash une fois.
- **Natives** : extraites par version dans `versions/<id>/natives/`. En architecture A elles ne sont
  chargées qu'une fois (celles du shell) ; en architecture B chaque enfant a les siennes.

Manifest source : `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`.

---

## 6. Ce qui survit à un swap, et ce qui ne survit pas

| Survit | Ne survit pas |
|---|---|
| Fenêtre, contexte GL, UI du client (archi A) | La connexion serveur en cours — on est déconnecté, forcément |
| Session de compte, config, thèmes | L'état du monde, l'inventaire côté client |
| Keybinds (persistés par id, cf. `KeybindManager.save`) | Tout objet du jeu mis en cache par un module |
| Modules chargés (ré-activés après `attach`) | Les objets GL de la version sortante (à supprimer explicitement) |

Un module qui doit conserver de l'état à travers un swap le sérialise dans `onDisable` et le relit
dans `onEnable` — le contexte lui fournit un `dataDirectory()` pour ça.
