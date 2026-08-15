# 4. Gestionnaire de keybindings

## Le bug, précisément

Ce qu'on appelle « le problème `key.anything` » recouvre deux défauts distincts de
`net.minecraft.client.option.KeyBinding`, qu'il faut séparer pour les corriger.

**Défaut 1 — l'identité est la clé de traduction.** Un `KeyBinding` s'enregistre dans une map
statique `KEYS_BY_ID` indexée par sa clé de traduction. Beaucoup de mods copient l'exemple le plus
répandu et déclarent littéralement `"key.anything"`, ou un libellé générique du même genre. Deux
mods qui choisissent la même chaîne s'écrasent : le second enregistrement remplace le premier dans
la map, et le keybind du premier mod devient inerte. Aucun message d'erreur — le mod se charge, le
bind apparaît dans l'écran des options, il ne se déclenche simplement jamais.

**Défaut 2 — une touche ne peut porter qu'un seul bind.** La map `KEY_TO_BINDINGS` associe une
touche physique à **un** `KeyBinding`. Deux binds sur `G`, même avec des identités distinctes, et
un seul des deux reçoit `wasPressed()`. C'est ce qui fait qu'un keybind « fonctionne » jusqu'au
jour où l'utilisateur installe un autre mod.

À quoi s'ajoutent trois défauts de conception qui font écrire le même code défensif dans chaque
mod : pas de modificateurs (Ctrl/Maj/Alt), pas de notion de contexte, et un binding sur le keycode
plutôt que sur le scancode — d'où le fameux ZQSD qui ne suit pas la disposition AZERTY.

## Les six décisions de conception

### 1. Identité namespacée, imposée

```java
Keybind.builder("hud-example:toggle")   // <moduleId>:<action>, validé par regex
```

Le format est vérifié dans le constructeur du record, et `ModuleContextImpl` refuse en plus
l'enregistrement si le namespace ne correspond pas à l'id du module. Une collision lève une
`DuplicateKeybindException` **au chargement**, pas un échec silencieux à l'exécution. Le libellé
affiché est un champ séparé, purement cosmétique, et peut être dupliqué sans conséquence.

### 2. Multimap, pas map

`KeybindRegistry` indexe `(device, code) → List<Entry>`. Tous les candidats sont conservés ; c'est
le résolveur qui arbitre à chaque appui. Le bucket est trié **une fois à l'indexation** — pas à
chaque frappe — selon trois critères :

1. **spécificité** décroissante (nombre de modificateurs) ;
2. **priorité** décroissante (arbitrage explicite entre modules) ;
3. **séquence** croissante (ordre d'enregistrement : départage stable, indépendant du hash).

### 3. Chords avec règle de spécificité

Un appui sur `Ctrl+G` ne doit pas déclencher aussi le bind `G` nu. Le résolveur sert le premier
niveau de spécificité applicable puis s'arrête :

```java
int spec = e.effectiveChord.specificity();
if (servedSpecificity >= 0 && spec < servedSpecificity) break;
```

Vérifié par le démonstrateur : sur `Ctrl+G`, seul `screenshot:capture` se déclenche, alors que
trois binds partagent la touche `G`.

### 4. Binding sur le scancode

Le scancode est la position physique de la touche ; le keycode dépend de la disposition. En
bindant sur le scancode et en n'utilisant `glfwGetKeyName(GLFW_KEY_UNKNOWN, scancode)` **que pour
l'affichage**, un profil reste cohérent quand l'utilisateur change de disposition en cours de
partie. La touche bindée s'affiche « W » en QWERTY et « Z » en AZERTY, sans qu'aucune
configuration ne bouge. La sérialisation stocke `CTRL+key.34`, jamais un libellé lisible.

### 5. Contextes, y compris pour la détection de conflits

`ActivationContext` porte un ensemble de *scopes* (`GAMEPLAY`, `SCREEN`, `TEXT_INPUT`). Deux
usages :

- **au dispatch** : un bind `IN_GAME` ne se déclenche pas pendant la saisie du chat ;
- **à l'analyse de conflits** : deux binds sur la même touche dans des contextes disjoints ne sont
  **pas** un conflit. La plupart des écrans de contrôles affichent ici des faux positifs qui
  poussent l'utilisateur à rebinder inutilement.

Le démonstrateur le vérifie : même touche, contextes `IN_GAME` et `IN_SCREEN` → 0 conflit.

### 6. Pipeline en amont du jeu, pas en sondage

L'`InputPipeline` reçoit les callbacks GLFW **avant** la session Minecraft, et lui transmet ce
qu'il n'a pas consommé. C'est l'inverse de l'approche mod classique (sonder `Keyboard.isKeyDown`
pendant le tick), qui perd les appuis plus courts qu'un tick et ne peut pas empêcher le jeu de
réagir.

## Les bugs d'exécution traités

**Touches collées.** Un écran s'ouvre pendant qu'une touche est maintenue ; le relâchement part
vers l'écran, le bind reste « enfoncé » à vie. C'est le bug le plus courant des clients moddés —
ouvrir le chat en sprintant et voir le personnage courir indéfiniment. Traité par un flush des
binds tenus sur changement de scope **et** sur perte de focus fenêtre :

```java
if (scope != lastScope) { flushHeld(false); lastScope = scope; }
```

Vérifié par le démonstrateur : `isActive("core:sprint")` passe de `true` à `false` à l'ouverture du
chat.

**Désynchronisation des modificateurs.** Alt+Tab laisse Alt collé, parce que le relâchement part à
l'OS. Le masque est reconstruit à partir de l'état physique suivi par le pipeline, jamais du champ
`mods` fourni par GLFW, et `glfwSetWindowFocusCallback` remet tout à zéro.

**Auto-répétition.** `GLFW_REPEAT` ne déclenche un bind `PRESS` que si `allowRepeat()` a été
demandé explicitement.

## Modes de déclenchement

Vanilla n'offre que `wasPressed()` (front montant, via un compteur) et `isPressed()` (niveau).
Chaque mod réimplémente donc son propre détecteur de double-tap ou de bascule, avec autant de
désynchronisations. Les six modes sont ici dans le pipeline : `PRESS`, `RELEASE`, `HOLD`,
`TOGGLE`, `DOUBLE_TAP` (fenêtre 250 ms), `LONG_PRESS` (seuil 400 ms).

Détail qui compte sur `DOUBLE_TAP` : après un déclenchement, l'horodatage est remis à zéro plutôt
que mis à jour, sinon un troisième appui rapide déclenche une seconde fois en cascade.

## Consommation

Un bind `consuming` (défaut) stoppe la chaîne : ni les binds moins prioritaires, ni le jeu ne
voient l'appui. Un bind `passthrough()` laisse passer. Le `KeyEvent.Consumption` est partagé entre
les handlers d'un même appui, donc un module peut décider à l'exécution — utile pour un bind
conditionnel qui ne doit consommer que lorsqu'il agit réellement.

## Reste à faire pour une version de production

- **Séquences multi-touches** (`G` puis `H`, façon Emacs) : ajouter un automate au-dessus du
  résolveur, avec un délai d'expiration et une indication visuelle de l'état intermédiaire.
- **Manettes** : ajouter un troisième `Chord.Device` et interroger `glfwGetGamepadState` dans
  `tick()` — les manettes ne produisent pas d'événements, seulement un état à sonder.
- **Profils par serveur** : un jeu de bindings différent selon l'adresse du serveur, appliqué à la
  connexion. `importProfile` conserve déjà les ids inconnus, ce qui rend le basculement de profil
  non destructif.
