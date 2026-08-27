#!/usr/bin/env bash
#
# FreePlay TV — installe l'interface façon Android TV sur la tablette et
# règle la tablette pour que la manette reste connectée en permanence.
#
#   ./install-interface-tv.sh            # installation
#   ./install-interface-tv.sh --annuler  # retour au launcher d'origine
#
# Rien n'est flashé : l'accueil d'origine reste installé et redevient
# l'accueil en une commande.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
APK="$SCRIPT_DIR/FreePlayTV.apk"
PKG="fr.freeplay.tv"
BACKUP="$SCRIPT_DIR/.launcher-origine"

log() { printf '\n\033[1;33m[FreePlay TV]\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------------------
# Vérifications
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
    echo "Aucune tablette détectée. Branche-la en USB, débogage USB activé."
    exit 1
fi

MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
ANDROID_VER="$(adb shell getprop ro.build.version.release | tr -d '\r')"
log "Tablette : $MODEL (Android $ANDROID_VER)"

# ---------------------------------------------------------------------------
# Mode annulation
# ---------------------------------------------------------------------------
if [[ "${1:-}" == "--annuler" ]]; then
    log "Retour à l'accueil d'origine…"
    if [[ -f "$BACKUP" ]]; then
        ORIG="$(cat "$BACKUP")"
        adb shell cmd package set-home-activity "$ORIG" >/dev/null 2>&1 \
            || adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$ORIG" >/dev/null 2>&1 \
            || true
        echo "  Accueil d'origine restauré : $ORIG"
    else
        echo "  Accueil d'origine inconnu : choisis-le à l'écran."
        adb shell am start -a android.settings.HOME_SETTINGS >/dev/null 2>&1 || true
    fi

    adb shell settings put global stay_on_while_plugged_in 0 >/dev/null 2>&1 || true
    adb shell dumpsys deviceidle whitelist "-$PKG" >/dev/null 2>&1 || true

    read -r -p "  Désinstaller aussi l'application FreePlay TV ? [o/N] " REP
    if [[ "${REP,,}" == "o" || "${REP,,}" == "oui" ]]; then
        adb uninstall "$PKG" >/dev/null 2>&1 && echo "  Application désinstallée."
    fi
    log "Terminé : la tablette est revenue à son fonctionnement d'origine."
    exit 0
fi

# ---------------------------------------------------------------------------
# Étape 1 — Mémoriser l'accueil actuel (pour pouvoir revenir en arrière)
# ---------------------------------------------------------------------------
log "Étape 1/4 — Sauvegarde de l'accueil actuel"
CURRENT="$(adb shell cmd package resolve-activity -c android.intent.category.HOME --brief 2>/dev/null \
    | tr -d '\r' | grep -m1 '/' | cut -d/ -f1 || true)"
if [[ -n "$CURRENT" && "$CURRENT" != "$PKG" ]]; then
    echo "$CURRENT" > "$BACKUP"
    echo "  Accueil d'origine mémorisé : $CURRENT"
else
    echo "  (accueil d'origine déjà remplacé ou non détecté)"
fi

# ---------------------------------------------------------------------------
# Étape 2 — Installer l'application
# ---------------------------------------------------------------------------
log "Étape 2/4 — Installation de l'interface FreePlay TV"
if [[ ! -f "$APK" ]]; then
    echo "  Fichier introuvable : $APK"
    echo "  Récupère FreePlayTV.apk depuis le dépôt (dossier tablette-android/)."
    exit 1
fi
adb install -r "$APK"

# ---------------------------------------------------------------------------
# Étape 3 — En faire l'écran d'accueil
# ---------------------------------------------------------------------------
log "Étape 3/4 — FreePlay TV devient l'écran d'accueil"
RESULT="$(adb shell cmd package set-home-activity "$PKG" 2>&1 | tr -d '\r' || true)"
if [[ "$RESULT" == *"Success"* ]]; then
    echo "  ✓ Accueil défini automatiquement."
else
    # Selon les versions d'Android, c'est le gestionnaire de rôles qui décide.
    if adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1; then
        echo "  ✓ Accueil défini automatiquement."
    else
        echo "  ⚠ Réglage automatique refusé par la tablette."
        echo "    L'écran de choix va s'ouvrir : sélectionne « FreePlay TV »"
        echo "    puis « Toujours »."
        adb shell am start -a android.settings.HOME_SETTINGS >/dev/null 2>&1 \
            || adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
        read -r -p "    Appuie sur Entrée une fois que c'est fait… "
    fi
fi

# ---------------------------------------------------------------------------
# Étape 4 — Manette connectée en permanence
# ---------------------------------------------------------------------------
log "Étape 4/4 — Manette toujours connectée"

# Le Bluetooth reste allumé.
adb shell settings put global bluetooth_on 1 >/dev/null 2>&1 || true

# L'app n'est jamais gelée par l'économiseur de batterie : c'est ce qui coupe
# la manette après quelques minutes d'inactivité.
adb shell dumpsys deviceidle whitelist "+$PKG" >/dev/null 2>&1 || true
adb shell cmd appops set "$PKG" RUN_IN_BACKGROUND allow >/dev/null 2>&1 || true
adb shell cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || true

# Écran allumé tant que la tablette est branchée (usage box de salon).
adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true

# Pas de scan Bluetooth permanent en fond : inutile et coûteux en batterie.
adb shell settings put global ble_scan_always_enabled 0 >/dev/null 2>&1 || true

echo "  ✓ Bluetooth maintenu actif, application exemptée de mise en veille."

# Lancer l'accueil tout de suite pour vérifier.
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
log "Terminé !"
cat <<'EOF'

  Sur la tablette :
   • L'écran d'accueil FreePlay TV s'affiche (grandes tuiles, heure,
     état de la manette en haut à droite).
   • Une tuile grise « À installer » signale une appli absente : clique
     dessus, le Play Store s'ouvre sur la bonne fiche.

  Pour appairer la manette (une seule fois) :
   1. Manette éteinte, maintiens son bouton d'appairage jusqu'à ce que la
      LED clignote rapidement.
   2. Sur la tablette, ouvre la tuile « Manette » et sélectionne-la.
   Ensuite elle se reconnecte toute seule à chaque allumage.

  Tout annuler :  ./install-interface-tv.sh --annuler

EOF
