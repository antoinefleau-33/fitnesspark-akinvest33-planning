# 📺🎮 FreePlay OS

Un système d'exploitation de salon, basé sur **Ubuntu 24.04 LTS**, qui reproduit
les fonctions de la Freebox en mieux : **chaînes TV Freebox**, **Steam
préinstallé**, **Netflix / Disney+ / Prime Video** (et uniquement ces trois-là,
pour ne pas gâcher de stockage), optimisé **4K**, **gaming** et **Wi-Fi**.

> L'ancienne page HTML de planning a été retirée de cette branche : le dépôt
> contient maintenant le projet FreePlay OS. (Le planning reste intact sur la
> branche `main`.)

> 📱 **C'est pour une tablette Android ?** Sur tablette on ne remplace pas
> l'OS (bootloader verrouillé, perte du DRM Netflix, pas de Steam sur ARM) :
> l'édition **[FreePlay Tablette](tablette-android/README.md)** obtient le même
> résultat par configuration, sans flash et 100 % réversible.

---

## ⚠️ À lire d'abord : on ne peut pas installer un OS *sur* la Freebox

Soyons honnêtes avant tout :

- **La Freebox (Server et Player) est un matériel propriétaire et verrouillé
  par Free.** Il n'existe aucun moyen d'y flasher un autre système : pas de
  bootloader déverrouillable, pas d'images alternatives. Et même si c'était
  possible, tu perdrais Internet, le téléphone et la TV.
- La Freebox **Delta/Ultra** permet de créer des **machines virtuelles** sur le
  Server, mais sans carte graphique : impossible d'y faire tourner Steam, la 4K
  ou Netflix. C'est utile pour des petits serveurs, pas pour le salon.

**La bonne approche — et c'est ce que fait ce projet** : installer FreePlay OS
sur un **PC branché en HDMI à la télé** (mini-PC ou PC de récup). Il utilise le
réseau de ta Freebox pour la TV et Internet, et fait tout ce que le Player
Freebox fait, plus le gaming.

### Matériel recommandé

| Composant | Minimum | Recommandé (4K + gaming) |
|---|---|---|
| Processeur | x86 64 bits, double cœur | Intel Core i5 / AMD Ryzen 5 |
| Mémoire | 8 Go | 16 Go |
| Stockage | SSD 120 Go | SSD 500 Go (les jeux Steam sont gros) |
| Carte graphique | Intégrée (Intel/AMD) | NVIDIA GTX/RTX ou AMD RX |
| Sortie vidéo | HDMI | HDMI 2.0+ (pour la 4K à 60 Hz) |
| Réseau | Wi-Fi 5 GHz | **Câble Ethernet** (imbattable pour le jeu et la 4K) |

---

## 🧰 Ce qui est préinstallé (et rien d'autre)

| Fonction | Application | Détail |
|---|---|---|
| 📺 Chaînes TV Freebox | **Kodi** + PVR IPTV Simple | Chaînes de la Freebox via le réseau local, zapping, guide TV |
| 📺 TV partout + replay | **OQEE by Free** (appli web) | La TV de Free, y compris hors du domicile |
| 🎮 Jeux | **Steam** (+ mode TV Big Picture) | Avec GameMode (priorité aux jeux), MangoHud (affichage FPS) et support manettes |
| 🎬 Streaming | **Netflix, Disney+, Prime Video** | Applis plein écran via Chrome (DRM Widevine). Aucune autre appli de streaming n'est installée |
| 🎞️ Lecteur universel | **VLC** | Fichiers locaux, clés USB, flux réseau |
| 🖥️ Pilotes | NVIDIA / AMD / Intel + Vulkan | Détection et installation automatiques |

### Optimisations incluses

- **Wi-Fi** : économie d'énergie désactivée (finies les micro-latences),
  domaine réglementaire France (tous les canaux 5 GHz, puissance max légale).
- **Réseau** : TCP BBR + file d'attente `fq` (débit et latence améliorés),
  buffers dimensionnés pour la 4K et les téléchargements Steam.
- **Comportement "box"** : démarrage sans mot de passe, jamais de mise en
  veille, pas de verrouillage d'écran, démarrage rapide.
- **Stockage** : suppression des logiciels inutiles (LibreOffice, jeux GNOME,
  Thunderbird…) — la place est réservée aux jeux et aux films.

### Les limites, en toute transparence

- **Netflix en 4K n'existe pas sur PC Linux** (ni Chrome sous Windows
  d'ailleurs) : Netflix limite les navigateurs à 1080p, c'est leur DRM, pas
  l'OS. Disney+ et Prime Video ont des limites similaires. La télé, Kodi,
  YouTube, VLC et les jeux profitent bien de la 4K. Pour du Netflix 4K natif,
  garde le Player Freebox ou une clé Android TV certifiée à côté.
- **Les chaînes TV Freebox par le réseau local** ne marchent que chez toi,
  connecté à la Freebox, et certaines chaînes cryptées passent par OQEE
  uniquement. Détails : [docs/03-CHAINES-TV-FREEBOX.md](docs/03-CHAINES-TV-FREEBOX.md).

---

## 🚀 Comment l'installer (3 chemins possibles)

### Chemin 1 — Tester d'abord dans une machine virtuelle ✅ (recommandé)

Avant de toucher à ta vraie machine, essaie l'OS dans VirtualBox depuis ton PC
actuel. Rien n'est modifié sur ton ordinateur.

➡️ **[docs/01-TESTER-EN-VM.md](docs/01-TESTER-EN-VM.md)**

### Chemin 2 — Le plus simple : Ubuntu + le script (recommandé pour la vraie machine)

1. Installe **Ubuntu Desktop 24.04 LTS** normalement sur la machine
   ([ubuntu.com/download](https://ubuntu.com/download/desktop)) — l'installateur
   te guide en français.
2. Ouvre un terminal dans le dossier du projet et lance :

   ```bash
   git clone https://github.com/antoinefleau-33/fitnesspark-akinvest33-planning -b claude/freebox-optimized-os-6fs2jy freeplay-os
   cd freeplay-os
   sudo ./setup/install.sh
   ```

3. Redémarre. C'est tout : le script installe et configure tout le reste.

### Chemin 3 — L'ISO auto-installante (avancé)

Construis une ISO qui installe FreePlay OS **toute seule, sans aucune
question** (elle **efface tout le disque** de la machine cible) :

```bash
sudo apt install xorriso
./iso/build-iso.sh /chemin/vers/ubuntu-24.04.x-desktop-amd64.iso
```

Tu obtiens `iso/freeplay-os.iso` : teste-la en VM (chemin 1), puis flashe-la
sur une clé USB (guide d'installation). Compte créé automatiquement :
`freeplay` / mot de passe `freeplay` (change-le après).

➡️ Guide complet du changement d'OS : **[docs/02-INSTALLER-SUR-LA-MACHINE.md](docs/02-INSTALLER-SUR-LA-MACHINE.md)**

---

## 📁 Contenu du dépôt

```
setup/install.sh        Le cœur du projet : transforme Ubuntu 24.04 en FreePlay OS
setup/…firstboot.service  Lance l'installation au 1er démarrage (ISO auto)
config/webapps/         Applis Netflix, Disney+, Prime Video, OQEE, Steam TV
config/network/         Optimisations Wi-Fi et réseau
config/kodi/            Chaînes TV Freebox préconfigurées pour Kodi
iso/build-iso.sh        Fabrique l'ISO d'installation automatique
docs/                   Guides pas à pas (VM, installation, TV)
```

## ❓ FAQ

**Pourquoi Ubuntu et pas un OS écrit de zéro ?**
Un OS de zéro = pas de pilotes, pas de Steam, pas de DRM Netflix. Ubuntu LTS
apporte le socle (pilotes, sécurité, mises à jour pendant 5 ans) et FreePlay
OS le spécialise en box TV/gaming. C'est exactement la méthode de SteamOS
(basé sur Arch) ou de l'OS de la Freebox elle-même (basé sur Linux).

**Et si je veux surtout jouer ?**
Jette aussi un œil à [Bazzite](https://bazzite.gg) : une distribution
"console de salon" toute faite avec Steam en mode console. FreePlay OS reste
plus proche de l'esprit "box TV + jeux".

**Je peux revenir en arrière ?**
Oui : réinstaller Windows (ou autre) par clé USB efface FreePlay OS. Pense
juste à sauvegarder tes fichiers avant tout changement d'OS.
