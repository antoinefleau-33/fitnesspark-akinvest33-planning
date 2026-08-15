# Prompt à donner à Claude Code

> Copie-colle tout ce qui suit dans Claude Code, lancé depuis le dossier où tu veux le projet.
> Il travaille en local sur ton PC : aucun dépôt Git, aucun GitHub.

---

## Contexte et contraintes

Construis un **lanceur Minecraft** en Python avec interface graphique, plus un **mod Fabric**
compagnon. Tout se passe en local sur ma machine Windows.

**Règles absolues :**

- **Aucun Git, aucun GitHub, aucun dépôt.** Tu écris des fichiers dans le dossier courant, c'est
  tout. Ne propose jamais de `git init`, de commit ou de push.
- **Python : bibliothèque standard uniquement.** Pas de `pip install`. La seule exception tolérée
  est PyInstaller, et uniquement pour produire un exécutable final.
- **Interface : Tkinter**, livré avec Python. Pas de customtkinter ni de PyQt : l'objectif est que
  ça démarre par un double-clic sans rien installer.
- Je suis sur **Windows 10**, avec **Java 25** installé.
- Écris tout en **français** : interface, commentaires, messages d'erreur, documentation.

**Méthode de travail que j'exige :**

- Après chaque module, **exécute ton code et montre-moi la sortie réelle**. Pas de « ça devrait
  marcher ».
- Pour l'interface, **prends une capture d'écran et regarde-la** avant de me dire que c'est fini.
- Quand tu ne peux pas vérifier quelque chose, **dis-le explicitement** au lieu de le passer sous
  silence.
- Mesure les performances avec des chiffres, après échauffement du JIT ou de l'interpréteur.

---

## Arborescence cible

```
LanceurMinecraft/
├── Lancer.bat              double-clic pour ouvrir l'interface
├── Compiler-EXE.bat        produit un .exe autonome via PyInstaller
├── mclaunch.py             moteur : auth, téléchargement, lancement (+ menu texte)
├── ui.py                   thème et widgets dessinés à la main
├── gui.py                  fenêtre principale et pages
├── spotify.py              pont vers Spotify
├── README.md
└── mod/                    mod Fabric, à compiler séparément
    ├── build.gradle
    ├── gradle.properties
    ├── settings.gradle
    └── src/main/
        ├── java/dev/poc/...
        └── resources/fabric.mod.json
```

---

## Module 1 — `mclaunch.py` : le moteur

Aucune dépendance externe. Utilisable seul en ligne de commande, et importable par l'interface.

### Authentification Microsoft

Chaîne en cinq étapes : **Microsoft → Xbox Live → XSTS → Minecraft → profil**.

- Flux **device code** (pas de redirection navigateur) :
  `https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode`, scope
  `XboxLive.signin offline_access`. Choisi parce qu'il n'exige ni serveur web local ni URI de
  redirection à déclarer dans Azure — la source d'échec la plus courante.
- Puis `https://user.auth.xboxlive.com/user/authenticate`,
  `https://xsts.auth.xboxlive.com/xsts/authorize` (RelyingParty
  `rp://api.minecraftservices.com/`), puis
  `https://api.minecraftservices.com/authentication/login_with_xbox`.
- **Vérifie la possession du jeu** (`/entitlements/mcstore`) AVANT de lire le profil : sinon
  l'erreur suivante est un 404 sur le profil, qu'on interprète naturellement — et à tort — comme
  un bug du lanceur.
- Traduis en français les erreurs Xbox : `XErr 2148916233` = pas de profil Xbox,
  `2148916238` = compte enfant.
- Raccourcis les erreurs Azure, qui font 5 lignes avec identifiants de trace. Traduis au minimum
  `AADSTS700016` (identifiant d'application inexistant) et `AADSTS7000218` (flux clients publics
  non autorisé).
- Stocke **uniquement le jeton de rafraîchissement** sur disque, en permissions 600. Le jeton de
  session dure ~24 h, avec **5 minutes de marge** avant expiration : un jeton qui expire pendant
  le chargement d'un monde donne une déconnexion au premier serveur, très difficile à relier à sa
  cause.
- **Masque le jeton dans tous les affichages.** Qui le lit a un accès complet au compte.
- **N'implémente PAS de mode hors-ligne.** Il fabrique une identité que les serveurs en ligne
  rejettent — c'est exactement la cause du `Failed to verify username` qu'on cherche à éviter.

L'utilisateur doit créer une application Azure gratuite (comptes personnels uniquement, flux
clients publics activé). Explique-le dans l'interface et le README.

### Installation d'une version

- Manifeste : `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
- Évalue les **règles** Mojang (`os.name`, `os.arch`, `features`). Attention : dès qu'une liste de
  règles existe, le défaut est « interdit ». L'ignorer donne `-XstartOnFirstThread` sous Linux.
- **Les sous-dossiers de `natives/` doivent être DÉDUITS des arguments JVM**, jamais écrits en
  dur. Minecraft 26.2 en attend quatre : `java`, `jna`, `lwjgl`, `netty`. Une liste figée en
  oublie un à la version suivante, et une bibliothèque native échoue à s'extraire sans message
  clair.
- Format moderne : les natifs sont des librairies ordinaires avec un suffixe de classifieur, et
  LWJGL les extrait lui-même depuis le classpath. Garde quand même le chemin de dépaquetage pour
  les versions ≤ 1.18.
- Installe Fabric en récupérant son profil sur `https://meta.fabricmc.net/v2` et **fusionne-le**
  avec le descripteur vanilla (`inheritsFrom`). Les librairies de Fabric passent **avant** celles
  de vanilla dans le classpath.
- **Minecraft 26.2 exige Java 25.** Détecte la version installée et préviens si elle est trop
  ancienne. Cherche Java dans le PATH **puis** dans `C:\Program Files\Java`,
  `\Eclipse Adoptium`, `\Microsoft\jdk`, `\Zulu` : un JDK fraîchement installé n'est pas toujours
  dans le PATH.

### Téléchargements — le point critique de performance

Une version complète, c'est ~580 Mo dont **5057 fichiers de ressources de 10 Ko en moyenne**.

**Une implémentation naïve, un fichier après l'autre, prend 39 minutes** — j'ai mesuré 465 ms par
fichier dont ~450 ms rien qu'en négociation TLS, pour 20 ms de transfert utile. **97 % du temps
passé à ouvrir des connexions.**

Deux corrections, à faire dès le début :

1. **Réutiliser la connexion HTTPS** par fil, avec `http.client.HTTPSConnection` en keep-alive
   stocké dans un `threading.local()`. Tous ces fichiers viennent du même serveur.
2. **Télécharger en parallèle**, 16 fils (c'est ce qu'utilise le lanceur officiel).

Résultat attendu : **~20 secondes** au lieu de 39 minutes, et **0,1 seconde** quand tout est déjà
présent. Mesure-le et montre-moi les chiffres.

Détails qui comptent :

- Nom de fichier temporaire **unique par fil** (`<cible>.<threadid>.part`), puis renommage
  atomique.
- **Compare la taille reçue à la taille attendue** avant d'écrire.
- **4 tentatives avec attente croissante** (0,4 s, 0,8 s, 1,6 s) et délai de connexion qui monte.
  Trois tentatives immédiates échouent toutes de la même façon face à un serveur qui limite.
- **Une seconde passe en série** sur ce qui a échoué en parallèle : un échec vient le plus souvent
  d'une saturation momentanée.
- Limite le rapport de progression à ~10 par seconde. À 5000 fichiers, notifier chaque succès
  sature la file de l'interface et la ralentit plus que le téléchargement.
- Vérification par **taille** pour les milliers de ressources et les librairies ; **SHA-1**
  seulement pour le client jar et les manifestes. Recalculer le SHA-1 de 500 Mo à chaque
  lancement coûte plus cher que le risque couvert.

### Ne jamais annoncer un succès faux

**C'est le bug le plus grave que j'ai rencontré.** Des téléchargements échouaient, l'erreur partait
dans une sortie console que l'interface n'affiche jamais, et l'installation se déclarait terminée.
Le jeu plantait ensuite sur
`java.nio.file.NoSuchFileException: assets\objects\9c\9cf743...`, vingt minutes plus tard.

Exigences :

- **Contrôle final sur le disque** après installation. S'il manque quoi que ce soit, lève une
  exception visible dans l'interface. Ne te fie jamais au seul compteur de succès.
- Fournis `verify_install()` (liste ce qui manque, sans rien télécharger) et `repair_install()`
  (récupère uniquement les fichiers absents). Un bouton **« Réparer »** dans l'interface.

### Lancement

- Substitue toutes les variables Mojang. **Vérifie qu'il n'en reste aucune** non substituée.
- 26.2 n'utilise plus `--userType` ; garde la substitution pour les versions plus anciennes.
- Capture la sortie du jeu (`stdout` + `stderr`) et **lis-la en continu dans un fil** : un tube
  non vidé se remplit et bloque le jeu au bout de quelques dizaines de Ko.

---

## Module 2 — `ui.py` et `gui.py` : l'interface

Thème sombre. Palette de départ :

```
fond #0D0F14 · barre latérale #12161F · panneaux #181D28 · cartes #1F2634
survol #2A3344 · accent #4C8DFF · texte #E9ECF2 · texte atténué #8A93A6
vert #4ADE80 · ambre #FBBF24 · rouge #F87171
```

Police : `Segoe UI` sous Windows. La police Tk par défaut donne immédiatement un air de logiciel
des années 90.

**Redessine les widgets sur des `Canvas`** — boutons, interrupteurs, curseurs, ascenseur, barre de
progression. Les widgets Tk natifs, en particulier l'ascenseur avec ses flèches, trahissent l'âge
de la bibliothèque dans un thème sombre.

### Pièges Tkinter que j'ai payés — ne les reproduis pas

1. **N'utilise JAMAIS `self._w` ou `self._h` comme attributs.** `_w` est le nom interne que
   Tkinter donne au chemin du widget. L'écraser casse toute création d'élément sur ce Canvas, avec
   une erreur incompréhensible (`invalid command name "520"`). Nomme-les `_width` / `_height`.
2. **Borne le rayon des coins arrondis** à la moitié du plus petit côté. Sans ça, une barre fine et
   longue reçoit un rayon supérieur à sa demi-hauteur et le lissage produit une **pointe
   triangulaire** au bout.
3. **`wraplength` sur tout `Label` de description.** Un Label sans limite réclame la largeur
   naturelle de son texte et pousse les boutons de droite hors de la zone visible.
4. **L'ascenseur doit garder sa place en permanence** (pastille masquée quand inutile). Le montrer
   et le cacher change la largeur disponible et rogne les enfants déjà positionnés.
5. **`highlightthickness=0` sur les `Text`**, sinon Tk dessine un cadre clair.
6. Propage les liaisons `<Enter>`/`<Leave>` à **toute la descendance** : entrer dans un enfant
   déclenche un `<Leave>` sur le parent, et le bouton clignote.
7. **Force UTF-8 sur la sortie sous Windows** (`sys.stdout.reconfigure`) : sur certaines consoles
   en cp1252, un simple accent lève un `UnicodeEncodeError`.
8. Garde une **référence aux `PhotoImage`**, sinon Tk les libère et rien ne s'affiche.

### Animations

Deux modèles, et le choix n'est pas cosmétique :

- **Courbes de durée** pour ce qui a un début et une fin connus.
- **Ressorts** pour ce qui réagit à l'utilisateur. Un ressort accepte un changement de cible en
  cours de course en conservant sa vélocité ; une courbe redémarre à zéro — c'est exactement la
  saccade qu'on ressent en balayant vite une liste.

Intégration **semi-implicite** (vélocité mise à jour avant la position), avec pas sous-divisé à
1/120 s : stable même si le framerate chute.

### Pages

**Jouer** — liste des versions installées, gros bouton, barre de progression.

> **Piège majeur.** Après installation, deux lignes apparaissent : `26.2` et `26.2 (Fabric)`.
> J'ai lancé la mauvaise pendant des jours sans comprendre pourquoi mes mods ne se chargeaient pas
> (`Is Modded: Probably not` dans le rapport de crash). Exigences : pastille **AVEC MODS** /
> **SANS MODS** bien visible, sélection automatique de Fabric quand des mods sont installés (en
> l'annonçant dans la barre d'état, jamais en silence), et confirmation avant de lancer une
> version nue avec des mods présents.

**Mes mods** — lis `fabric.mod.json` **dans chaque `.jar`** pour afficher le vrai nom, la version,
la description et l'icône, jamais le nom de fichier. Interrupteur qui renomme en `.jar.disabled`
(convention Fabric, réactivation sans retéléchargement). Ajout par sélecteur de fichiers.

> **Résolution de dépendances obligatoire.** Fabric refuse de démarrer avec
> `Incompatible mods found!` et liste des identifiants (`cloth-config`, `almanac`, `fabric-api`)
> sans dire où les trouver. Lis `depends` **et `provides`** de chaque mod — `provides` est
> essentiel, Fabric API y déclare ses dizaines de modules internes qui sinon ressortiraient tous
> comme manquants. **Boucle jusqu'à stabilité** : installer Fabric API satisfait d'un coup
> beaucoup de modules, et les bibliothèques ajoutées ont leurs propres dépendances. Bouton
> **« Réparer les dépendances »** et signalement automatique avant le lancement.

**Découvrir** — recherche dans le catalogue Modrinth (`https://api.modrinth.com/v2/search`),
filtrée par facettes sur la version installée et le chargeur. Installation en un clic, **avec les
dépendances**. Affiche aussi « ⚠ prévu pour X » sur les mods incompatibles : implémente le
sous-ensemble semver de Fabric (`*`, exact, `>=`, `>`, `<=`, `<`, `~`, `^`, conjonctions) et
teste-le. `~26.2` doit accepter 26.2.1 et refuser 26.1.2.

**Console** — sortie du jeu en direct, erreurs en rouge, fenêtre glissante de ~4000 lignes.
**S'ouvre d'elle-même sur un arrêt anormal** et traduit les causes connues : ressource manquante
→ « clique sur Réparer », `UnsupportedClassVersionError` → Java trop ancien, `OutOfMemoryError` →
mémoire insuffisante.

**Paramètres** — curseur de mémoire, identifiant Azure, dossier de jeu, chemin Java avec bouton de
détection.

> Écris dans l'aide que **4 à 6 Go suffisent** et qu'allouer 16 Go **dégrade** le jeu : les pauses
> du ramasse-miettes s'allongent et créent des micro-saccades.

**Compte** — connexion Microsoft, code affiché **en gros et espacé** (un code de 8 lettres se
recopie beaucoup moins mal quand les caractères sont détachés), boutons copier et ouvrir la page.
Avatar : vraie tête du joueur, découpée dans sa peau récupérée sur
`https://sessionserver.mojang.com/session/minecraft/profile/<uuid>` (l'URL de texture est en
base64 dans une propriété). Superpose le second calque, sinon beaucoup de joueurs sont chauves.
Agrandis par duplication de pixels (`zoom`), jamais par lissage.

### Fils d'exécution

Toute tâche longue part **hors du fil de l'interface**, avec une file relue par `after()`. Sinon la
fenêtre se fige et Windows affiche « ne répond pas » — le symptôme qui fait croire à un plantage
alors que tout va bien.

### Démarrage

`Lancer.bat` doit faire `cd /d "%~dp0"` (sinon un double-clic démarre dans `System32`) et essayer
`py`, puis `python`, puis `python3` — sous Windows, `python3` est souvent un alias du Microsoft
Store qui ouvre la boutique au lieu de lancer quoi que ce soit.

`Compiler-EXE.bat` produit un `.exe` autonome avec PyInstaller
(`--onefile --windowed`). Il pèsera 15 à 20 Mo, et ce poids a une raison : il contient
l'interpréteur Python et Tk, donc il fonctionne sur un PC sans Python installé.

---

## Module 3 — `spotify.py`

J'ai **Spotify Premium**.

Trois backends, choisis automatiquement :

1. **Windows sans compte** : titre de la fenêtre Spotify (format « Artiste - Titre ») lu par
   `ctypes` + `EnumWindows`, et touches multimédia globales (`0xB3` lecture/pause, `0xB0` suivant,
   `0xB1` précédent) via `keybd_event`. Fonctionne même avec un compte gratuit.
2. **Linux** : MPRIS via `dbus-send`, utile pour développer.
3. **API Web** (PKCE) : seule voie pour les playlists et le choix d'un morceau. **Exige Premium** —
   Spotify renvoie 403 sur un compte gratuit, ce n'est pas contournable.

Redirection sur `http://127.0.0.1:8888/callback`, **port fixe** : Spotify compare l'adresse
déclarée caractère par caractère.

**Page Musique au style Minecraft** : biseau clair en haut à gauche et sombre en bas à droite,
aplats sans coins arrondis, police à chasse fixe, ombre portée d'un pixel sous chaque texte (c'est
cette ombre qui rend un texte immédiatement reconnaissable). Carte du morceau, barre de
progression verte, playlists cliquables.

> **Tk ne décode ni le JPEG**, et toutes les pochettes Spotify sont en JPEG. Génère une mosaïque
> de gros pixels déterministe à partir du titre : chaque morceau a son motif et le retrouve. Ne
> laisse pas un carré vide.

Rafraîchis **seulement quand la page est visible**, pour ne pas consommer le quota d'API en fond.

### Pont pour le mod

Petit serveur HTTP local (`http.server`) exposant `/status`, `/playlists`, `/playpause`, `/next`,
`/previous`, `/play`.

- **Écoute sur `127.0.0.1` uniquement.** Sur `0.0.0.0`, n'importe qui sur le Wi-Fi pourrait
  piloter la musique.
- **Jeton obligatoire** en en-tête `X-Token`. Sans lui, n'importe quelle page web ouverte dans le
  navigateur pourrait envoyer des requêtes depuis du JavaScript.
- Écris port et jeton dans `<dossier de jeu>/.spotify-bridge.json`, régénérés à chaque lancement.
- Démarre le serveur **avant** le jeu, arrête-le à sa fermeture.

---

## Module 4 — le mod Fabric

Deux fonctionnalités : incrustation Spotify, et diagnostic de rendu des BlockEntity.

**Structure impérative : isole tout ce qui ne dépend pas de Minecraft** dans des classes Java
pures, compilables avec un simple `javac`. Chez moi, 7 classes sur 12 le sont — parseur JSON
minimal, état, client HTTP du pont, filtres, statistiques, politique d'occlusion. **Compile-les et
teste-les réellement**, y compris le client HTTP contre le serveur Python lancé pour de vrai.
Quand Mojang change une signature, la casse reste confinée aux autres.

### Incrustation musique

Le mod ne parle jamais à Spotify directement : il n'a ni les jetons ni la possibilité d'ouvrir un
navigateur. Il lit le fichier de jeton **à chaque cycle** (port et jeton changent quand je relance
le lanceur), interroge le serveur local depuis un **fil dédié**, et le fil de rendu ne fait que
lire une référence atomique. Un appel HTTP depuis le fil de rendu gèlerait l'image.

### Diagnostic BlockEntity

Le but est de mesurer le coût de chargement et de rendu.

- **Filtre par défaut : présence d'un `BlockEntityRenderer`**, pas par type de bloc. C'est lui qui
  détermine le coût de rendu. Sur mes mesures : 2466 BlockEntity avec renderer contre 1485 pour
  les seuls coffres/shulkers/ender chests — **40 % du coût invisible** avec le filtre étroit, et
  les hoppers, qui dominent le coût de *tick*, n'y sont pas. Prévois aussi un filtre par types
  explicites, un filtre « ticking », et un filtre « renderer hors portée ».
- Vérifie le ticker **côté client** : beaucoup de BlockEntity ont un ticker serveur et aucun
  client, les confondre surestime massivement la charge.
- Utilise `renderer.isInRenderDistance(be, camPos)` plutôt que la distance seule : un panneau se
  cull bien plus tôt qu'un coffre.
- Parcours les chunks via le gestionnaire de chunks **sans forcer le chargement**. Charger des
  chunks pour les mesurer revient à mesurer l'outil.
- Ne recollecte pas à chaque frame (une fois sur 10 suffit) et **affiche le coût de l'outil
  lui-même** — un diagnostic qui ne s'inclut pas dans sa mesure ment sur ce qu'il observe.
- Détecte les **chunks saturés** : c'est ce qui répond à « quel chunk fait chuter le framerate ».
  Une ferme à hoppers concentre des centaines de BlockEntity dans deux chunks, la moyenne globale
  ne le montre jamais.

**Modes d'occlusion :**

- `OCCLUDED` — normal.
- `OCCLUDED_DIMMED` — **par défaut**. Double passe : partie cachée en `GL_GREATER` atténuée,
  partie visible en `GL_LEQUAL` à pleine intensité. Plus informatif qu'un aplat traversant, car il
  laisse distinguer ce qui est réellement occlus — la question même qu'on se pose en déboguant du
  culling. `glDepthMask(false)` sur la première passe, sinon les lignes derrière le décor écrivent
  dans le depth buffer et masquent la suite.
- `THROUGH_WALLS` — **à limiter au solo strict**, vérifié à chaque frame : serveur intégré actif,
  non publié au LAN, aucune connexion distante. Ailleurs, dégrade en double passe. Mets cette
  règle dans **une seule fonction testable** ; ne la réimplémente nulle part ailleurs.

**Pièges OpenGL :**

- **N'appelle jamais `glEnable`/`glDisable` directement.** Blaze3D maintient un cache logiciel de
  l'état GL ; un appel brut le désynchronise, Blaze3D croit le test de profondeur toujours actif
  et ne le réactive jamais. Le symptôme apparaît **plusieurs frames plus tard, ailleurs** — HUD
  disparu, particules mal triées. Passe par `RenderSystem.*`.
- **Restaure l'état dans un `finally`.** Si un appel de dessin lève et que tu ne restaures qu'en
  chemin nominal, le jeu reste sans test de profondeur et la cause est invisible dans la
  stacktrace.
- **Coordonnées relatives à la caméra**, soustraction en `double` avant conversion en `float`. Un
  `float` a 24 bits de mantisse : à x = 1 000 000 les boîtes tremblent visiblement.
- `glLineWidth > 1.0` n'est pas garanti en profil core. Pour une épaisseur fiable il faut extruder
  des quads face caméra.

**Fichiers de build** : `build.gradle` avec `fabric-loom`, Java 25, et un `gradle.properties` dont
tu me diras d'aller vérifier les numéros exacts sur https://fabricmc.net/develop — ils changent à
chaque build.

---

## Ordre de travail

1. `mclaunch.py` : authentification + installation + lancement, testé en ligne de commande
2. Téléchargements parallèles, **avec mesure avant/après**
3. `ui.py` + `gui.py` : les pages Jouer, Mods, Paramètres, Compte
4. Découvrir (Modrinth) et résolution des dépendances
5. Console et traduction des erreurs
6. `spotify.py` + page Musique
7. Le mod Fabric

Après chaque étape : exécute, montre-moi la sortie, et dis-moi ce que tu n'as pas pu vérifier.

## Ce que je ne veux pas

- Pas de Git ni de dépôt distant
- Pas de mise à jour automatique depuis Internet (tout est local)
- Pas de mode hors-ligne pour l'authentification
- Pas de « c'est prêt » sans exécution réelle à l'appui
