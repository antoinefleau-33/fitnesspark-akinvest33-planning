# 🧪 Tester FreePlay OS dans une machine virtuelle (avant la vraie machine)

Une machine virtuelle (VM) = un "faux PC" qui tourne dans une fenêtre de ton
ordinateur actuel. **Rien n'est modifié sur ton PC** : c'est le moyen sûr de
voir l'OS, de vérifier que tout te plaît, et seulement ensuite de l'installer
pour de vrai.

## 1. Installer VirtualBox

1. Télécharge VirtualBox (gratuit) : <https://www.virtualbox.org/wiki/Downloads>
   (Windows, macOS ou Linux).
2. Installe-le en laissant les options par défaut.

## 2. Créer la machine virtuelle

1. Ouvre VirtualBox → **Nouvelle**.
2. Nom : `FreePlay OS` — Type : **Linux** — Version : **Ubuntu (64-bit)**.
3. Image ISO : choisis…
   - l'ISO officielle **Ubuntu Desktop 24.04** si tu testes le *chemin 2*
     (installation classique puis script), ou
   - **`iso/freeplay-os.iso`** si tu as construit l'ISO automatique
     (*chemin 3*). ⚠️ Coche « Skip Unattended Installation » dans VirtualBox :
     c'est notre ISO qui gère l'installation, pas VirtualBox.
4. Mémoire : **8192 Mo** si possible (4096 minimum). Processeurs : **4**.
5. Disque : **60 Go** (il n'occupera que ce qui est réellement utilisé).
6. Dans **Configuration → Affichage** : mémoire vidéo à 128 Mo et active
   l'accélération 3D.
7. Démarre la VM.

## 3. Ce que tu peux vérifier en VM

| ✅ Testable en VM | ❌ Non testable en VM |
|---|---|
| L'installation se déroule bien | Les performances 3D réelles (la VM n'a pas ta vraie carte graphique) |
| L'interface, les applis présentes | La 4K fluide |
| Netflix/Disney+/Prime (connexion, lecture) | La sortie HDMI vers la télé |
| Les chaînes TV Freebox (si ton PC est connecté au réseau de ta Freebox) | Le Wi-Fi réel de la machine finale |
| Steam (connexion, boutique, mode Big Picture) | Les jeux exigeants |

Autrement dit : la VM sert à valider que **tout est là et fonctionne** ; les
performances, elles, se jugent sur la vraie machine.

## 4. Astuces

- Avec l'ISO auto (*chemin 3*) : l'installation démarre toute seule et efface
  le disque **de la VM** (aucun risque pour ton PC). Au premier démarrage, le
  service `freeplay-firstboot` télécharge Steam, Kodi, Chrome… : laisse-lui
  10–30 min selon ta connexion. Suivi en direct :
  `tail -f /var/log/freeplay-install.log` — compte : `freeplay` / `freeplay`.
- Avec l'ISO Ubuntu classique (*chemin 2*) : installe Ubuntu dans la VM, puis
  lance `sudo ./setup/install.sh` comme sur une vraie machine.
- Si la VM est lente, c'est normal : c'est un PC émulé. La vraie machine sera
  bien plus rapide.

## 5. Quand tout te convient

Passe au guide suivant pour installer sur la vraie machine :
➡️ [02-INSTALLER-SUR-LA-MACHINE.md](02-INSTALLER-SUR-LA-MACHINE.md)
