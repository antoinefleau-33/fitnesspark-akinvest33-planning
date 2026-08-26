#!/usr/bin/env bash
#
# FreePlay OS — transforme une installation Ubuntu 24.04 fraîche en "box" de
# salon : chaînes TV Freebox, Steam, Netflix / Disney+ / Prime Video, 4K.
#
# Usage :
#   sudo ./install.sh              # après une installation Ubuntu classique
#   sudo ./install.sh --firstboot  # mode automatique (utilisé par l'ISO auto-installante)
#
set -euo pipefail

FIRSTBOOT=0
[[ "${1:-}" == "--firstboot" ]] && FIRSTBOOT=1

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG_DIR="$REPO_DIR/config"

log() { printf '\n\033[1;33m[FreePlay OS]\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------------------
# Vérifications de base
# ---------------------------------------------------------------------------
if [[ $EUID -ne 0 ]]; then
    echo "Lance ce script avec sudo :  sudo ./install.sh"
    exit 1
fi

if [[ "$(uname -m)" != "x86_64" ]]; then
    echo "Ce script nécessite un PC 64 bits (x86_64) : Steam n'existe pas pour cette architecture."
    exit 1
fi

. /etc/os-release
if [[ "${ID:-}" != "ubuntu" ]]; then
    echo "Ce script est prévu pour Ubuntu 24.04 (système détecté : ${PRETTY_NAME:-inconnu})."
    exit 1
fi

# Utilisateur cible : celui qui utilisera la box (pas root)
FP_USER="${SUDO_USER:-}"
if [[ -z "$FP_USER" || "$FP_USER" == "root" ]]; then
    FP_USER="$(getent passwd 1000 | cut -d: -f1 || true)"
fi
if [[ -z "$FP_USER" ]]; then
    echo "Impossible de déterminer l'utilisateur principal (uid 1000 introuvable)."
    exit 1
fi
FP_HOME="$(getent passwd "$FP_USER" | cut -d: -f6)"
log "Utilisateur cible : $FP_USER"

# ---------------------------------------------------------------------------
# Attente d'Internet (indispensable au premier démarrage automatique)
# ---------------------------------------------------------------------------
log "Vérification de la connexion Internet…"
NET_OK=0
for _ in $(seq 1 60); do
    if curl -fsm 5 http://connectivity-check.ubuntu.com >/dev/null 2>&1; then
        NET_OK=1
        break
    fi
    sleep 5
done
if [[ $NET_OK -ne 1 ]]; then
    echo "Pas de connexion Internet. Branche un câble Ethernet (ou configure le Wi-Fi) puis relance."
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
APT="apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"

# ---------------------------------------------------------------------------
# Dépôts logiciels : multiverse (Steam) + architecture 32 bits (jeux)
# ---------------------------------------------------------------------------
log "Activation des dépôts nécessaires (Steam, pilotes)…"
add-apt-repository -y multiverse || true
dpkg --add-architecture i386
$APT update

log "Mise à jour du système…"
$APT upgrade

# ---------------------------------------------------------------------------
# Nettoyage : on retire ce qui ne sert à rien sur une box de salon
# ---------------------------------------------------------------------------
log "Suppression des logiciels inutiles (gain de stockage)…"
$APT purge 'libreoffice*' 'thunderbird*' 'rhythmbox*' 'shotwell*' \
    'transmission-*' 'remmina*' aisleriot gnome-mahjongg gnome-mines \
    gnome-sudoku cheese simple-scan 2>/dev/null || true
$APT autoremove --purge || true

# ---------------------------------------------------------------------------
# Pilotes graphiques + Vulkan (nécessaires pour la 4K et le jeu)
# ---------------------------------------------------------------------------
log "Installation des pilotes graphiques (NVIDIA / AMD / Intel) + Vulkan…"
$APT install ubuntu-drivers-common mesa-vulkan-drivers mesa-vulkan-drivers:i386 \
    libvulkan1 libvulkan1:i386 mesa-utils vainfo
ubuntu-drivers autoinstall || log "ubuntu-drivers a échoué — pas grave sur GPU AMD/Intel, Mesa est déjà installé."

# ---------------------------------------------------------------------------
# Gaming : Steam + GameMode + overlay FPS + manettes
# ---------------------------------------------------------------------------
log "Installation de Steam et des optimisations jeu…"
if ! $APT install steam-installer gamemode mangohud steam-devices; then
    log "Paquet steam-installer indisponible, installation du .deb officiel Valve…"
    curl -fL -o /tmp/steam.deb https://repo.steampowered.com/steam/archive/stable/steam_latest.deb
    $APT install /tmp/steam.deb gamemode mangohud
    rm -f /tmp/steam.deb
fi

# ---------------------------------------------------------------------------
# TV et vidéo : Kodi (chaînes Freebox) + VLC
# ---------------------------------------------------------------------------
log "Installation de Kodi (chaînes TV Freebox) et VLC…"
$APT install kodi kodi-pvr-iptvsimple vlc

log "Pré-configuration des chaînes TV Freebox dans Kodi…"
KODI_PVR_DIR="$FP_HOME/.kodi/userdata/addon_data/pvr.iptvsimple"
mkdir -p "$KODI_PVR_DIR"
cp "$CONFIG_DIR/kodi/instance-settings-1.xml" "$KODI_PVR_DIR/instance-settings-1.xml"
chown -R "$FP_USER:$FP_USER" "$FP_HOME/.kodi"

# ---------------------------------------------------------------------------
# Streaming : Google Chrome (seul navigateur Linux avec le DRM Widevine à jour)
# + applis Netflix / Disney+ / Prime Video / OQEE uniquement (pas de superflu)
# ---------------------------------------------------------------------------
if ! command -v google-chrome >/dev/null 2>&1; then
    log "Installation de Google Chrome (DRM Widevine pour Netflix/Disney+/Prime)…"
    curl -fL -o /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
    $APT install /tmp/chrome.deb
    rm -f /tmp/chrome.deb
fi

log "Création des applis Netflix, Disney+, Prime Video et OQEE (TV Free)…"
install -m 644 "$CONFIG_DIR"/webapps/*.desktop /usr/share/applications/

# Raccourcis sur le bureau
DESKTOP_DIR="$FP_HOME/Bureau"
[[ -d "$DESKTOP_DIR" ]] || DESKTOP_DIR="$FP_HOME/Desktop"
mkdir -p "$DESKTOP_DIR"
cp "$CONFIG_DIR"/webapps/*.desktop "$DESKTOP_DIR"/
for f in steam.desktop kodi.desktop; do
    [[ -f "/usr/share/applications/$f" ]] && cp "/usr/share/applications/$f" "$DESKTOP_DIR"/
done
chmod +x "$DESKTOP_DIR"/*.desktop || true
chown -R "$FP_USER:$FP_USER" "$DESKTOP_DIR"

# ---------------------------------------------------------------------------
# Réseau : Wi-Fi sans économie d'énergie, domaine FR, BBR (latence + débit)
# ---------------------------------------------------------------------------
log "Optimisation du Wi-Fi et du réseau…"
install -m 644 "$CONFIG_DIR/network/wifi-powersave-off.conf" /etc/NetworkManager/conf.d/
install -m 644 "$CONFIG_DIR/network/99-freeplay-gaming.conf" /etc/sysctl.d/
sysctl --system >/dev/null 2>&1 || true
# Domaine réglementaire France : autorise tous les canaux 5 GHz et la
# puissance d'émission maximale légale (meilleure portée / débit Wi-Fi).
echo 'options cfg80211 ieee80211_regdom=FR' > /etc/modprobe.d/freeplay-wifi-fr.conf
iw reg set FR 2>/dev/null || true

# ---------------------------------------------------------------------------
# Comportement "box" : connexion auto, jamais de mise en veille, écran allumé
# ---------------------------------------------------------------------------
log "Activation de la connexion automatique (démarrage direct, comme une box)…"
if [[ -f /etc/gdm3/custom.conf ]]; then
    python3 - "$FP_USER" <<'PYEOF'
import configparser, sys
user = sys.argv[1]
path = "/etc/gdm3/custom.conf"
cp = configparser.ConfigParser()
cp.optionxform = str
cp.read(path)
if "daemon" not in cp:
    cp["daemon"] = {}
cp["daemon"]["AutomaticLoginEnable"] = "true"
cp["daemon"]["AutomaticLogin"] = user
with open(path, "w") as f:
    cp.write(f)
PYEOF
fi

log "Désactivation de la mise en veille et du verrouillage d'écran…"
mkdir -p /etc/dconf/db/local.d /etc/dconf/profile
cat > /etc/dconf/db/local.d/00-freeplay <<'EOF'
[org/gnome/settings-daemon/plugins/power]
sleep-inactive-ac-type='nothing'
idle-dim=false

[org/gnome/desktop/session]
idle-delay=uint32 0

[org/gnome/desktop/screensaver]
lock-enabled=false
EOF
cat > /etc/dconf/profile/user <<'EOF'
user-db:user
system-db:local
EOF
dconf update || true

# Démarrage plus rapide
if [[ -f /etc/default/grub ]]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=1/' /etc/default/grub
    update-grub >/dev/null 2>&1 || true
fi

# ---------------------------------------------------------------------------
# Fin
# ---------------------------------------------------------------------------
if [[ $FIRSTBOOT -eq 1 ]]; then
    systemctl disable freeplay-firstboot.service 2>/dev/null || true
fi

log "Installation terminée !"
cat <<EOF

  ✅ FreePlay OS est prêt. Il reste 3 petites étapes (une seule fois) :

  1. TV      → ouvre Kodi, va dans Extensions > Mes extensions > Clients PVR
               > PVR IPTV Simple Client > Activer. Les chaînes Freebox
               apparaissent dans le menu "TV". (Détails : docs/03-CHAINES-TV-FREEBOX.md)
  2. Steam   → ouvre Steam et connecte-toi à ton compte.
  3. Vidéo   → ouvre Netflix / Disney+ / Prime Video / OQEE depuis le bureau
               et connecte-toi à tes comptes.

  Redémarre le PC pour appliquer tous les réglages :  sudo reboot

EOF
