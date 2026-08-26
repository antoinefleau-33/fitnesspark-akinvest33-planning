# 💾 Changer l'OS de la machine : installer FreePlay OS

Ce guide explique **comment remplacer le système d'une machine** (un vieux PC,
un mini-PC…) par FreePlay OS. À faire **après** avoir testé en VM
([01-TESTER-EN-VM.md](01-TESTER-EN-VM.md)).

## ⚠️ Avant tout : sauvegarde

Changer d'OS **efface le disque** de la machine. S'il y a des photos, des
documents ou quoi que ce soit d'important dessus : copie-les d'abord sur une
clé USB, un disque externe ou un cloud. Il n'y a pas de retour en arrière.

## Étape 1 — Préparer la clé USB d'installation

Il te faut une clé USB de **8 Go minimum** (elle sera effacée).

1. Récupère l'image à installer :
   - *Chemin 2 (recommandé)* : l'ISO officielle **Ubuntu Desktop 24.04** →
     <https://ubuntu.com/download/desktop>
   - *Chemin 3 (avancé)* : ton ISO **`iso/freeplay-os.iso`** construite avec
     `iso/build-iso.sh`.
2. Télécharge **balenaEtcher** (<https://etcher.balena.io>) — ou Rufus sur
   Windows (<https://rufus.ie>).
3. Lance Etcher : choisis l'ISO → choisis la clé USB → **Flash**.

## Étape 2 — Démarrer la machine sur la clé USB

1. Branche la clé sur la machine cible, allume-la et **tapote la touche du
   menu de démarrage** dès l'allumage :

   | Marque | Touche habituelle |
   |---|---|
   | Dell, Lenovo, Acer | `F12` |
   | HP | `F9` (ou `Échap`) |
   | Asus | `F8` (ou `Échap`) |
   | MSI, Gigabyte | `F11` |
   | Beaucoup de mini-PC | `F7`, `F11` ou `Suppr` |

2. Dans le menu, choisis la ligne correspondant à ta clé USB (souvent préfixée
   « UEFI »).
3. Si la machine refuse de démarrer sur la clé : entre dans le BIOS/UEFI
   (`F2` ou `Suppr`), et désactive « Secure Boot », puis réessaie.

## Étape 3 — Installer

### Chemin 2 : Ubuntu classique + script (recommandé)

1. Choisis **« Essayer Ubuntu »** d'abord : ça lance l'OS depuis la clé sans
   rien installer — bonus : ça permet de vérifier que le Wi-Fi, l'écran et le
   son de CETTE machine fonctionnent, avant de toucher au disque.
2. Quand c'est bon, double-clique **« Installer Ubuntu »** : langue français,
   installation normale, « Effacer le disque et installer Ubuntu ».
3. Au premier démarrage, ouvre l'application **Terminal** et lance :

   ```bash
   sudo apt install -y git
   git clone https://github.com/antoinefleau-33/fitnesspark-akinvest33-planning -b claude/freebox-optimized-os-6fs2jy freeplay-os
   cd freeplay-os
   sudo ./setup/install.sh
   ```

4. Le script installe tout (Steam, Kodi + chaînes Freebox, Netflix/Disney+/
   Prime, pilotes, optimisations). Compte 15–40 min selon la connexion.
5. Redémarre : `sudo reboot`.

### Chemin 3 : ISO auto-installante (avancé)

1. Démarre sur la clé : l'installation se lance **toute seule** et **efface le
   disque sans poser de question**.
2. La machine redémarre sur FreePlay OS (compte `freeplay`, mot de passe
   `freeplay`). Le premier démarrage télécharge et installe les applications :
   laisse la machine tranquille 10–30 min (suivi possible avec
   `tail -f /var/log/freeplay-install.log`).
3. Change le mot de passe : **Paramètres → Utilisateurs**, ou `passwd` dans un
   terminal.

## Étape 4 — Premiers réglages (une seule fois)

1. **TV** : ouvre Kodi → Extensions → Mes extensions → Clients PVR →
   *PVR IPTV Simple Client* → **Activer**. Les chaînes Freebox apparaissent
   dans le menu **TV**. (Détails et dépannage :
   [03-CHAINES-TV-FREEBOX.md](03-CHAINES-TV-FREEBOX.md))
2. **Steam** : ouvre Steam, connecte-toi, active éventuellement le mode
   Big Picture au démarrage (icône « Steam (mode TV) » sur le bureau).
3. **Netflix / Disney+ / Prime Video / OQEE** : ouvre chaque appli depuis le
   bureau et connecte-toi à tes comptes.
4. **Branchements conseillés** : HDMI 2.0 vers la télé, et si possible câble
   **Ethernet** vers la Freebox (le Wi-Fi optimisé dépanne bien, mais le câble
   reste roi pour le jeu et la 4K).

## Revenir en arrière / changer encore d'OS

Le principe est toujours le même, dans les deux sens : flasher une clé USB
avec l'OS voulu (Windows : outil « Media Creation Tool » de Microsoft),
démarrer dessus, installer. C'est exactement la manipulation que tu viens
d'apprendre.
