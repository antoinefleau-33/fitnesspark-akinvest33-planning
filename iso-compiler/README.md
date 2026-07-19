# 💿 ISO Compiler — Terminal Linux (WSL) + sudo

Un outil web pour **compiler des images ISO** depuis un navigateur, avec un **vrai
terminal Linux interactif** (WSL sous Windows, bash sous Linux), le support de
**`sudo`** (mot de passe interactif inclus) et l'**import de fichiers** à graver.

> Pourquoi un petit serveur et pas juste une page HTML ?
> Un navigateur est isolé (sandbox) : il ne peut pas exécuter WSL, `sudo` ni des
> commandes système. Ce serveur Node.js local fait le pont entre la page web et
> ton vrai terminal. Tout tourne **en local sur ta machine**, rien n'est envoyé
> ailleurs.

---

## 🚀 Installation

### Prérequis
- **Node.js 16+** ([nodejs.org](https://nodejs.org))
- Sous **Windows** : **WSL** installé (`wsl --install`) avec une distro (Ubuntu…)
- L'outil ISO dans le terminal Linux : `xorriso` (recommandé), `genisoimage` ou `mkisofs`
  ```bash
  # dans WSL / Linux :
  sudo apt update && sudo apt install -y xorriso genisoimage
  ```

### Lancer l'outil
```bash
cd iso-compiler
npm install
npm start
```
Puis ouvre **http://localhost:3000**

> 💡 **Conseil (Windows)** : pour l'expérience la plus fluide et 100 % native,
> lance le serveur **depuis WSL** (Node installé dans Ubuntu). `sudo` et `xorriso`
> fonctionnent alors nativement, sans conversion de chemin. Sinon, lance-le depuis
> Windows : il ouvrira automatiquement `wsl.exe`.

---

## 🖥️ Le terminal réel

Le terminal de droite est un **vrai shell** :
- tape n'importe quelle commande Linux (`ls`, `apt`, `xorriso`, `sudo …`) ;
- les prompts `sudo` s'affichent, tu tapes ton mot de passe directement ;
- redimensionnement, couleurs, historique — comme un terminal natif.

Pour un **vrai pseudo-terminal (TTY)** complet (applis plein écran type `vim`,
`htop`), installe `node-pty` (déjà en dépendance optionnelle) :
```bash
npm install node-pty
```
S'il ne s'installe pas (outils de compil manquants), l'outil bascule
automatiquement sur un mode « pipes » qui gère quand même la plupart des
commandes, `sudo` inclus.

---

## 📋 Coller depuis le presse-papier (pensé pour mobile/iPhone)

- **Bouton « 📋 Coller » dans la barre du terminal** : colle le contenu de ton
  presse-papier directement dans le terminal (commandes, mots de passe…).
- **Bouton « 📋 Coller depuis le presse-papier » dans le panneau d'import** :
  transforme ce que tu as copié en fichier ajouté à l'ISO — du texte (tu choisis
  le nom du fichier) ou même une **photo/capture d'écran copiée** (importée telle
  quelle en image).

Dans les deux cas une petite fenêtre s'ouvre : soit tu utilises
**« ⚡ Coller automatiquement »** (sur iPhone, Safari affichera sa bulle
« Coller » à confirmer — nécessite HTTPS ou localhost), soit tu fais un
**appui long → Coller** dans la zone de texte, ce qui marche partout, même en
HTTP sur le réseau local.

## 📦 Compiler une ISO

1. **Importer** — glisse tes fichiers/dossiers dans la zone d'import (panneau 1).
   Ils sont copiés dans `workspace/source/`.
2. **Paramètres** (panneau 2) — nom de volume, nom du fichier `.iso`, outil, et
   option `sudo` si besoin.
3. **Compiler l'ISO** — la commande s'exécute **dans le terminal** ; tu vois la
   sortie en direct. L'ISO est écrite dans `workspace/output/`.
4. **Télécharger** — l'ISO produite apparaît dans le panneau 3 avec un lien de
   téléchargement.

Exemple de commande générée (outil `xorriso`) :
```bash
xorriso -as mkisofs -J -R -V 'MON_ISO' -o '/…/workspace/output/image.iso' '/…/workspace/source'
```

---

## ⚙️ Configuration (variables d'environnement)

| Variable      | Rôle                                             | Défaut                     |
|---------------|--------------------------------------------------|----------------------------|
| `PORT`        | Port du serveur web                              | `3000`                     |
| `WORKSPACE`   | Dossier de travail (source + output)             | `./workspace`              |
| `SHELL_CMD`   | Commande shell à lancer (ex: `wsl.exe -d Ubuntu`)| WSL (Win) / bash (Linux)   |

Exemple :
```bash
SHELL_CMD="wsl.exe -d Ubuntu-22.04" PORT=8080 npm start
```

---

## 🗂️ Structure

```
iso-compiler/
├── server.js          # backend : terminal WebSocket, upload, build ISO
├── public/
│   └── index.html     # interface (terminal xterm.js + panneaux)
├── package.json
├── .gitignore
└── workspace/         # créé au 1er lancement
    ├── source/        # fichiers importés
    └── output/        # ISO générées
```

---

## 🔒 Sécurité

Cet outil donne un accès shell complet à ta machine via le navigateur : il est
prévu pour un usage **local uniquement**. Ne l'expose **pas** sur Internet ni sur
un réseau non fiable. Par défaut il écoute sur `localhost`.
