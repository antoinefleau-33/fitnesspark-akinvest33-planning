# 1. Architecture modulaire

Modèle : Fabric plutôt que Forge. Un noyau minimal, un contrat étroit, et tout le reste en
modules — y compris les fonctionnalités du client lui-même.

## Le contrat

```java
public interface ClientModule {
    void onLoad(ModuleContext ctx) throws Exception;
    default void onEnable(ModuleContext ctx) {}
    default void onDisable(ModuleContext ctx) {}
    default void onUnload(ModuleContext ctx) {}
}
```

Descripteur `module.json` à la racine du jar : id, version, entrypoint, contraintes semver
(`depends`, `suggests`, `conflicts`), versions de jeu supportées.

## La décision structurante : le contexte est un scope

Tout ce qu'un module obtient passe par `ModuleContext`, et **tout ce qui est enregistré via lui est
révoqué à sa fermeture** — abonnements au bus, keybinds, ressources déclarées via `onClose`.

Ce n'est pas une commodité, c'est ce qui rend le déchargement à chaud fiable *sans faire confiance
au module*. Même un module qui plante dans `onUnload`, ou qui ne l'implémente pas, ne peut rien
laisser derrière lui. Sans ce mécanisme, un seul module négligent suffit à retenir un classloader
et à faire échouer un changement de version.

Le module d'exemple le montre : son `onUnload` est vide, et c'est correct.

Le `ModuleManager` ajoute deux filets par-dessus : `bus.purgeClassLoader()` et
`keybinds.unregisterNamespace()`, tous deux journalisés quand ils trouvent quelque chose — un
avertissement dans les logs pointe alors directement le module fautif.

## Classloader parent-last, avec liste blanche

`ModuleClassLoader` cherche dans l'ordre : classes déjà chargées → **packages protégés (parent
obligatoire)** → jar du module → jars des dépendances déclarées → parent.

La liste blanche (`java.*`, `dev.poc.api.*`, `org.lwjgl.*`, `org.slf4j.*`) n'est pas un
raffinement. Si `dev.poc.api` était chargé en parent-last, le module chargerait sa propre copie de
`GameBridge` : un type différent de celui manipulé par le shell, et chaque appel échouerait sur un
`ClassCastException` indéchiffrable (« GameBridge cannot be cast to GameBridge »).

Le parent-last sur le reste permet en revanche à un module d'embarquer sa propre version d'une
librairie sans polluer les autres.

Le drapeau `isolated: false` du `module.json` place le module dans un classloader partagé, pour les
cas où plusieurs modules doivent se voir mutuellement (un module de thèmes étendu par des paquets
de thèmes).

## Résolution des dépendances

Validation des contraintes semver, détection des conflits déclarés, filtrage par version de jeu,
puis tri topologique.

Le tri est un DFS avec détection de cycle plutôt qu'un algorithme de Kahn, pour une raison
pratique : quand un cycle existe, le DFS peut en restituer le **chemin exact**.

```
cycle de dépendances : mod-a → mod-b → mod-a
```

Sur une plateforme à modules, ce genre de détail décide de l'expérience de développement — un
« cycle détecté » sans chemin oblige à tout inspecter à la main.

Doublons d'id : la version la plus haute gagne, avec un avertissement. Dépendance manquante ou
contrainte non satisfaite : exception, chargement interrompu pour ce module uniquement.

## Bus d'événements

Deux choix pour tenir 240 fps :

- **Pas de parcours de hiérarchie au post.** La table de dispatch d'un type est calculée une fois
  (super-classes et interfaces incluses) puis mise en cache. Un `RenderHud` posté 240 fois par
  seconde ne doit pas refaire d'introspection.
- **Copy-on-write** plutôt que verrous : le post est le chemin chaud, les (dés)abonnements sont
  rares. Un module qui se désabonne pendant l'itération ne provoque pas de
  `ConcurrentModificationException`.

Les handlers annotés `@Subscribe` passent par `MethodHandle` plutôt que `Method.invoke` : après
échauffement du JIT, le coût rejoint celui d'un appel virtuel direct.

Une exception dans un handler est journalisée et n'interrompt ni le dispatch ni les autres
handlers — un module tiers ne doit pas pouvoir figer le rendu du client.

## Chargement : ce que fait le ModuleManager

Pour chaque module dans l'ordre topologique : construction du classloader (avec les loaders de ses
dépendances), `Class.forName(entrypoint)`, instanciation par constructeur sans argument, puis
`onLoad` **avec le TCCL positionné sur le classloader du module** — sans quoi les librairies qui
font du `Class.forName` implicite (`ServiceLoader`, drivers) chercheraient dans le shell et
échoueraient.

Un module qui plante au chargement passe en état `ERRORED` avec sa stacktrace conservée, et les
autres continuent de se charger.

## Vérification de fuite

`ModuleManager.assertCollectable(classLoader, timeout)` — `PhantomReference` + `System.gc()`. Le
GC n'est pas obligé d'obéir, donc le résultat est indicatif ; il détecte néanmoins les régressions
franches (un thread oublié, un handler resté abonné). À câbler sur un raccourci de debug pendant
le développement : chaque rechargement de module qui ne libère pas son loader fait grossir la
métaspace jusqu'au `OutOfMemoryError`.

## Librairies suggérées

| Besoin | Choix | Pourquoi |
|---|---|---|
| Transformation bytecode | `org.spongepowered:mixin` + `ow2.asm` | mixins pour les hooks structurels, ASM direct pour les passes massives |
| Remapping | `net.fabricmc:tiny-remapper` | l'outil de référence, gère tiny v2 et ProGuard |
| Lecture de mappings | `net.fabricmc:mapping-io` | supporte tous les formats en une API |
| JSON | `jackson-jr` **relocalisé** | Minecraft embarque déjà Gson : ne jamais exposer le vôtre |
| Logs | `slf4j-api` + `logback` | façade partagée entre shell et versions |
| Caches | `caffeine` | pour le store d'assets |

Règle générale pour le shell : **toute dépendance ajoutée devient visible par Minecraft**. C'est
pourquoi `poc-api` et `poc-core` n'en ont aucune, y compris pour le JSON.
