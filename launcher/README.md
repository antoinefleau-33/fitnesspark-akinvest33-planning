# Lanceur Minecraft avec compte Microsoft

Un seul fichier Python, aucune dépendance à installer. Il télécharge le jeu, installe Fabric, gère
la connexion Microsoft et lance Minecraft.

## Installation

**Première fois :** récupère `Installer.bat`, place-le où tu veux et double-clique. Il télécharge
tout le reste et démarre le lanceur.

**Ensuite :** double-clique sur `Lancer.bat`.

**Mises à jour :** dans le lanceur, *Paramètres → Vérifier et mettre à jour*. Plus besoin de
revenir sur GitHub — les fichiers sont remplacés sur place, avec une sauvegarde `.backup` de
chacun. La nouvelle version prend effet au redémarrage du lanceur.

## Démarrage

**Windows :** double-clique sur `Lancer.bat`. L'interface graphique s'ouvre.

**Mac / Linux :**

```bash
python3 gui.py
```

Quatre pages dans la barre de gauche :

| Page | Ce qu'on y fait |
|---|---|
| **Jouer** | choisir une version installée et lancer le jeu |
| **Mods** | ajouter tes mods, les activer/désactiver, installer le pack performance |
| **Paramètres** | mémoire allouée, dossier de jeu, identifiant Azure, chemin de Java |
| **Compte** | connexion Microsoft |

La première fois : **Paramètres** (coller l'identifiant Azure) → **Compte** (se connecter) →
**Jouer** → *Installer*. Ensuite, seul le bouton JOUER sert.

### Gérer ses mods

La page Mods lit le contenu de chaque `.jar` pour afficher le vrai nom, la version, la description
et l'icône du mod — pas le nom de fichier. L'interrupteur active ou désactive sans supprimer : le
fichier est renommé en `.jar.disabled`, la convention que Fabric comprend, donc réactiver ne
demande aucun téléchargement.

Le bouton **Pack performance** télécharge Sodium, Lithium, FerriteCore et une dizaine d'autres
directement depuis Modrinth, pour la version installée.

## Interface graphique ou ligne de commande

`gui.py` est l'interface ; `mclaunch.py` contient toute la logique et reste utilisable seul.
Sans argument, `mclaunch.py` ouvre un menu texte numéroté. Les sous-commandes restent disponibles :

```bash
python3 mclaunch.py setup
python3 mclaunch.py login
python3 mclaunch.py install 26.2 --fabric
python3 mclaunch.py play 26.2
python3 mclaunch.py play 26.2 --dry-run   # affiche la commande sans lancer
```

## Si tu vois « error: the following arguments are required: command »

C'était le comportement de la version 1.0 en ligne de commande sans sous-commande. Ce n'était pas
une panne. Depuis la 1.1 il n'y a plus rien à taper : `Lancer.bat` ouvre directement l'interface.

## Ce qu'il te faut avant

**Java 25.** Minecraft 26.2 l'exige. Avec une version plus ancienne, le jeu refuse de démarrer.
Le lanceur te prévient s'il détecte un Java trop vieux.

**Une application Azure.** C'est l'étape obligatoire et la seule un peu pénible. Microsoft impose
que chaque lanceur ait sa propre identité — je ne peux pas t'en fournir une, et emprunter celle
d'un autre lanceur la ferait révoquer.

### Créer l'application Azure (10 minutes, gratuit)

1. Va sur [portal.azure.com](https://portal.azure.com), connecte-toi.
2. Cherche « Inscriptions d'applications » (*App registrations*) → **Nouvelle inscription**.
3. Nom : ce que tu veux. Types de comptes : **comptes Microsoft personnels uniquement**.
4. Ne mets **pas** d'URI de redirection : le lanceur utilise le mode « code d'appareil », qui n'en
   a pas besoin.
5. Une fois créée, va dans **Authentification** → active **Autoriser les flux clients publics**.
6. Copie l'**ID d'application (client)** depuis la page Vue d'ensemble.
7. Colle-le quand `setup` te le demande.

**Étape souvent oubliée :** les applications créées récemment doivent demander l'autorisation
d'utiliser l'API Minecraft via un formulaire Microsoft. Sans ça, la connexion échoue à l'étape
Xbox Live. Compte jusqu'à 24 h après approbation.

Tu n'as pas besoin de secret client.

## Comment ça marche

Se connecter, c'est une chaîne de cinq étapes :

```
Microsoft  →  Xbox Live  →  XSTS  →  Minecraft  →  ton profil
```

Le lanceur ne garde en mémoire durable que le *jeton de rafraîchissement*, dans
`~/.poclauncher/account.json` (permissions 600). Le jeton de session, lui, dure environ 24 h et est
renouvelé automatiquement. Il est masqué dans tous les affichages : quiconque le lit a un accès
complet à ton compte.

## « Launcher non compatible » : ce que ça veut dire vraiment

Ce message précis n'existe pas côté Minecraft. Ce que tu as probablement vu, c'est l'un de ces
trois cas — et les trois sont réglés par ce lanceur :

| Message | Cause réelle | Solution |
|---|---|---|
| `Failed to verify username` | Le serveur a demandé à Mojang de confirmer ton identité, et la réponse était négative. Ton jeton d'authentification est absent, faux ou expiré. | La vraie connexion Microsoft, celle que fait ce lanceur. |
| `Invalid session (Try restarting your game and the launcher)` | Même chose, jeton périmé. | Le lanceur renouvelle automatiquement. |
| `Outdated client` / `Outdated server` | Tu n'es pas sur la même version que le serveur. | `install` la bonne version, ou utilise ViaFabricPlus. |

Le point commun : **ce n'est jamais le lanceur qui est refusé, c'est la session.** Les serveurs en
ligne ne savent pas quel lanceur tu utilises et ne peuvent pas le savoir. Prism Launcher, MultiMC
ou celui-ci passent exactement comme le lanceur officiel, du moment que l'authentification est
correcte.

Un point à ne pas confondre : certains serveurs font tourner un anti-triche qui, lui, regarde le
*client* (pas le lanceur) et peut refuser un client modifié. Ce lanceur ne cherche pas à masquer
quoi que ce soit à ces systèmes, et je ne l'ai pas conçu pour — il lance un client normal, avec une
identité honnête.

## Pas de mode hors-ligne

Volontairement absent. Un mode « hors-ligne » fabrique une fausse identité : ça permet de jouer en
solo sans compte, mais **aucun serveur en ligne ne l'acceptera jamais** — c'est précisément la
cause du `Failed to verify username`. Comme ton objectif est que rejoindre un serveur fonctionne,
l'ajouter ne ferait que réintroduire le problème.

## En faire un vrai exécutable

Le script se lance déjà directement :

```bash
chmod +x mclaunch.py
./mclaunch.py play 26.2
```

Pour un `.exe` Windows autonome, sans Python installé :

```bash
pip install pyinstaller
pyinstaller --onefile --windowed --name MonLanceur gui.py
```

Le binaire arrive dans `dist/`. Note qu'un exécutable PyInstaller est souvent signalé à tort par
les antivirus — c'est un faux positif classique de PyInstaller, pas un problème de ce code.

## Avec les mods de performance

Le script voisin télécharge les mods :

```bash
python3 mclaunch.py install 26.2 --fabric
python3 ../modpack/resolve_mods.py --mc 26.2 --allow-alpha --download ~/.poclauncher/game/mods
python3 mclaunch.py play 26.2
```

## Commandes

| Commande | Rôle |
|---|---|
| `setup` | identifiant Azure, dossier de jeu, mémoire, chemin Java |
| `login` | connexion Microsoft (`--force` pour repartir de zéro) |
| `logout` | oublie la session |
| `versions` | liste les versions disponibles |
| `install <v>` | télécharge une version (`--fabric` pour ajouter Fabric) |
| `play <v>` | lance le jeu (`--dry-run` pour voir la commande) |

## Vitesse d'installation

Une version complète, c'est ~580 Mo : le jeu, 68 librairies et **5057 fichiers de ressources**
(textures, sons, langues). Ces ressources font 10 Ko en moyenne.

La version 1.1 les téléchargeait un par un, chacun sur une connexion sécurisée neuve : environ
450 ms de négociation pour 20 ms de transfert utile. Soit **39 minutes**, dont l'immense majorité
passée à ouvrir et fermer des connexions.

Depuis la 1.2, les connexions sont réutilisées et 16 fichiers sont téléchargés en même temps.
Mesuré sur une installation complète de 26.2 partant de zéro : **20 secondes**. Une réinstallation,
quand tout est déjà présent : **0,1 seconde** au lieu de plusieurs minutes de vérification.

Ce chiffre de 20 s a été obtenu sur une connexion rapide. Chez toi, le temps sera surtout dicté par
ton débit : 580 Mo sur une fibre à 100 Mb/s font environ une minute, incompressible. Le point
important est qu'il n'y a plus de temps perdu ailleurs que dans le transfert lui-même.

## Le jeu plante sur « NoSuchFileException »

Message typique dans le rapport de crash :

```
java.nio.file.NoSuchFileException: ...\assets\objects\9c\9cf7432d...
```

Un fichier de ressource manque. Cause : son téléchargement a échoué et, jusqu'à la version 1.2,
le lanceur l'ignorait — il annonçait « Installation terminée » avec des fichiers absents.

**Solution : bouton « Réparer » sur la page Jouer** (ou `python mclaunch.py repair 26.2`). Il
compare l'installation à l'index officiel et récupère uniquement ce qui manque, en quelques
secondes plutôt que 580 Mo.

Depuis la 1.2, ça ne devrait plus se produire :

- quatre tentatives par fichier, avec attente croissante entre chaque ;
- une seconde passe en série sur tout ce qui a échoué en parallèle ;
- la taille reçue est comparée à la taille attendue avant d'écrire ;
- **contrôle final sur le disque** : s'il manque quoi que ce soit, l'installation échoue
  visiblement au lieu de se déclarer réussie.

## État de vérification

Testé sur Minecraft 26.2 / Fabric 0.19.3 :

- résolution de version et fusion du profil Fabric (138 librairies) — OK
- filtrage par système : 68 librairies retenues sur 131, natifs des autres OS correctement exclus — OK
- téléchargement du client (39,2 Mo) avec vérification SHA-1 — OK
- relance sans re-télécharger — OK
- installation complète de 26.2 depuis zéro : 582 Mo en 20 s ; seconde passe en 0,1 s — OK
- profil Fabric installé et fusionné, 76 jars au classpath — OK
- réseau simulé défaillant : l'installation lève bien une erreur au lieu de s'annoncer terminée — OK
- réparation ciblée après suppression de fichiers : détectés, récupérés, vérification à zéro manquant — OK
- construction de la ligne de commande, aucune variable non substituée, jeton masqué à l'affichage — OK
- interface : les quatre pages et les deux fenêtres modales ont été rendues sous affichage virtuel
  et inspectées visuellement, avec des données de test (versions installées, cinq mods avec
  icônes, compte connecté) — OK

**Non testé :** la connexion Microsoft elle-même et le lancement effectif du jeu. Les deux
demandent un vrai compte et un écran, que je n'ai pas ici. La chaîne d'authentification suit la
procédure documentée par Mojang, mais attends-toi à devoir ajuster un détail au premier essai —
surtout du côté de l'autorisation Azure.
