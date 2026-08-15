# Lanceur Minecraft avec compte Microsoft

Un seul fichier Python, aucune dépendance à installer. Il télécharge le jeu, installe Fabric, gère
la connexion Microsoft et lance Minecraft.

## Démarrage — le plus simple

**Windows :** double-clique sur `Lancer.bat`. Un menu s'ouvre, tu choisis par numéro.

**Mac / Linux :**

```bash
python3 mclaunch.py
```

Sans argument, le lanceur ouvre un menu :

```
========================================================
   LANCEUR MINECRAFT
========================================================
  Dossier de jeu : C:\Users\...\game
  Compte         : non connecte
  Installe       : rien pour l'instant

  1) JOUER
  2) Installer une version (avec Fabric)
  3) Se connecter a mon compte Microsoft
  4) Configurer le lanceur
  ...
```

Suis l'ordre **4 → 3 → 2 → 1** la première fois : configurer, se connecter, installer, jouer.
Ensuite, seul le choix 1 sert.

## En ligne de commande

Les sous-commandes restent disponibles si tu préfères :

```bash
python3 mclaunch.py setup
python3 mclaunch.py login
python3 mclaunch.py install 26.2 --fabric
python3 mclaunch.py play 26.2
python3 mclaunch.py play 26.2 --dry-run   # affiche la commande sans lancer
```

## Si tu vois « error: the following arguments are required: command »

C'était le comportement de la version 1.0 quand on lançait le script sans rien derrière. Ce n'est
pas une panne : il manquait juste une sous-commande. Depuis la 1.1, le script ouvre le menu à la
place. Récupère la dernière version du fichier.

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
pyinstaller --onefile --name MonLanceur mclaunch.py
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

## État de vérification

Testé sur Minecraft 26.2 / Fabric 0.19.3 :

- résolution de version et fusion du profil Fabric (138 librairies) — OK
- filtrage par système : 68 librairies retenues sur 131, natifs des autres OS correctement exclus — OK
- téléchargement du client (39,2 Mo) avec vérification SHA-1 — OK
- relance sans re-télécharger — OK
- construction de la ligne de commande, aucune variable non substituée, jeton masqué à l'affichage — OK

**Non testé :** la connexion Microsoft elle-même et le lancement effectif du jeu. Les deux
demandent un vrai compte et un écran, que je n'ai pas ici. La chaîne d'authentification suit la
procédure documentée par Mojang, mais attends-toi à devoir ajuster un détail au premier essai —
surtout du côté de l'autorisation Azure.
