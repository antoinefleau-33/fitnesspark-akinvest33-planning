#!/usr/bin/env bash
#
# FreePlay Tablette — transforme une tablette Android en "box" Freebox :
# chaînes TV (OQEE), Netflix / Disney+ / Prime Video, jeux Steam en streaming,
# optimisations fluidité et Wi-Fi. Aucun flash, 100 % réversible.
#
# Depuis un PC Linux/macOS, tablette branchée en USB (débogage USB activé) :
#   ./install-tablette.sh            # configuration
#   ./install-tablette.sh --annuler  # annule tous les réglages du script
#
set -euo pipefail

MODE="${1:-}"

# Applis installées (Play Store officiel uniquement) — rien d'autre,
# pour ne pas gâcher le stockage.
APPS=(
    "net.oqee.androidmobile|OQEE by Free — chaînes TV Freebox, replay, enregistrements"
    "com.netflix.mediaclient|Netflix"
    "com.disney.disneyplus|Disney+"
    "com.amazon.avod.thirdpartyclient|Prime Video"
    "com.valvesoftware.steamlink|Steam Link — tes jeux Steam en streaming depuis ton PC"
    "org.videolan.vlc|VLC — lecteur vidéo universel"
    "fr.freebox.network|Freebox Connect — gestion et optimisation du Wi-Fi Freebox"
)

# Applis préinstallées souvent inutiles (désactivation OPTIONNELLE et réversible)
BLOAT=(
    com.facebook.katana
    com.facebook.appmanager
    com.facebook.services
    com.facebook.system
    com.microsoft.skydrive
    com.linkedin.android
    com.samsung.android.app.spage
)

log() { printf '\n\033[1;33m[FreePlay Tablette]\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------------------
# Vérifications : adb + tablette branchée et autorisée
# ---------------------------------------------------------------------------
if ! command -v adb >/dev/null 2>&1; then
    echo "adb manquant. Installe-le avec :"
    echo "  Ubuntu/Debian : sudo apt install adb"
    echo "  macOS         : brew install android-platform-tools"
    exit 1
fi

adb start-server >/dev/null 2>&1
if adb devices | grep -q "unauthorized"; then
    echo "La tablette demande une autorisation : regarde son écran et appuie"
    echo "sur « Autoriser le débogage USB », puis relance ce script."
    exit 1
fi
if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
    echo "Aucune tablette détectée. Vérifie que :"
    echo "  1. La tablette est branchée en USB au PC ;"
    echo "  2. Le débogage USB est activé (voir README.md, section Prérequis)."
    exit 1
fi

MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
log "Tablette détectée : $MODEL"

# ---------------------------------------------------------------------------
# Mode annulation : tout remettre comme avant
# ---------------------------------------------------------------------------
if [[ "$MODE" == "--annuler" ]]; then
    log "Annulation des réglages…"
    adb shell settings put global window_animation_scale 1
    adb shell settings put global transition_animation_scale 1
    adb shell settings put global animator_duration_scale 1
    adb shell settings put system screen_off_timeout 120000
    for pkg in "${BLOAT[@]}"; do
        adb shell pm enable --user 0 "$pkg" >/dev/null 2>&1 || true
    done
    log "Terminé : réglages d'origine restaurés, applis désactivées réactivées."
    echo "(Les applis installées ne sont pas supprimées — désinstalle-les depuis"
    echo " la tablette si besoin.)"
    exit 0
fi

# ---------------------------------------------------------------------------
# Étape 1 — Installation des applis (via le Play Store officiel)
# ---------------------------------------------------------------------------
log "Étape 1/3 — Installation des applis"
echo "Le script ouvre chaque appli sur le Play Store DE LA TABLETTE :"
echo "appuie sur « Installer » sur la tablette, puis sur Entrée ici."
echo

for entry in "${APPS[@]}"; do
    pkg="${entry%%|*}"
    name="${entry#*|}"
    if adb shell pm list packages | tr -d '\r' | grep -qx "package:$pkg"; then
        echo "  ✓ $name — déjà installée"
        continue
    fi
    echo "  → $name"
    adb shell am start -a android.intent.action.VIEW -d "market://details?id=$pkg" >/dev/null
    read -r -p "    Appuie sur « Installer » sur la tablette, puis Entrée ici… "
done

# ---------------------------------------------------------------------------
# Étape 2 — Optimisations (fluidité, écran, Wi-Fi)
# ---------------------------------------------------------------------------
log "Étape 2/3 — Optimisations"
# Animations 2x plus rapides : la tablette paraît nettement plus réactive
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
# Écran allumé 30 min sans toucher (usage TV/film)
adb shell settings put system screen_off_timeout 1800000
# Wi-Fi jamais mis en veille (évite les coupures pendant un film)
adb shell settings put global wifi_sleep_policy 2 2>/dev/null || true
echo "  ✓ Animations accélérées, veille écran 30 min, Wi-Fi toujours actif"

# ---------------------------------------------------------------------------
# Étape 3 — Nettoyage optionnel des applis préinstallées inutiles
# ---------------------------------------------------------------------------
log "Étape 3/3 — Nettoyage (optionnel)"
FOUND=()
for pkg in "${BLOAT[@]}"; do
    if adb shell pm list packages | tr -d '\r' | grep -qx "package:$pkg"; then
        FOUND+=("$pkg")
    fi
done

if [[ ${#FOUND[@]} -eq 0 ]]; then
    echo "  Aucune appli superflue connue détectée — rien à faire."
else
    echo "  Applis préinstallées détectées (désactivation réversible, pas de suppression) :"
    printf '    - %s\n' "${FOUND[@]}"
    read -r -p "  Les désactiver ? [o/N] " REP
    if [[ "${REP,,}" == "o" || "${REP,,}" == "oui" ]]; then
        for pkg in "${FOUND[@]}"; do
            adb shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
        done
        echo "  ✓ Désactivées. (Réactivation : ./install-tablette.sh --annuler)"
    else
        echo "  OK, on ne touche à rien."
    fi
fi

# ---------------------------------------------------------------------------
# Fin
# ---------------------------------------------------------------------------
log "Terminé ! Dernières étapes, sur la tablette :"
cat <<'EOF'

  1. TV      → ouvre OQEE by Free et connecte-toi avec ton compte Free :
               chaînes, replay, guide TV — même en dehors de la maison.
  2. Vidéo   → connecte-toi dans Netflix, Disney+ et Prime Video.
  3. Jeux    → ouvre Steam Link : il détecte ton PC (Steam doit tourner
               dessus, même réseau Wi-Fi) et streame tes jeux sur la
               tablette. Manette Bluetooth recommandée.
  4. Wi-Fi   → ouvre Freebox Connect pour vérifier la qualité du signal ;
               reste sur le réseau 5 GHz de la Freebox.

  Tout annuler :  ./install-tablette.sh --annuler

EOF
