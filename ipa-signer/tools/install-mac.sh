#!/usr/bin/env bash
#
# install-mac.sh — installe un .ipa SIGNÉ sur l'iPhone branché en USB (macOS).
#
# Garde ta signature AppleP12 telle quelle (il n'y a AUCUNE re-signature ici).
# Utilise libimobiledevice, installé automatiquement via Homebrew si besoin.
#
# Usage :
#   ./install-mac.sh ~/Downloads/MonApp-signed.ipa
#   ./install-mac.sh "https://ton-site.onrender.com/f/<id>/app.ipa"
#
set -euo pipefail

IPA_ARG="${1:-}"
if [[ -z "$IPA_ARG" ]]; then
  echo "Usage : $0 <fichier.ipa | URL-de-téléchargement>"
  exit 1
fi

# 1) Dépendances -------------------------------------------------------------
if ! command -v brew >/dev/null 2>&1; then
  echo "❌ Homebrew n'est pas installé."
  echo "   Installe-le depuis https://brew.sh puis relance ce script."
  exit 1
fi
for pkg in libimobiledevice ideviceinstaller; do
  if ! brew list "$pkg" >/dev/null 2>&1; then
    echo ">> Installation de $pkg (une seule fois)…"
    brew install "$pkg"
  fi
done

# 2) Récupérer l'IPA (chemin local OU URL) -----------------------------------
IPA="$IPA_ARG"
TMPDIR_DL=""
if [[ "$IPA_ARG" =~ ^https?:// ]]; then
  TMPDIR_DL="$(mktemp -d)"
  IPA="$TMPDIR_DL/app.ipa"
  echo ">> Téléchargement de l'IPA…"
  curl -fL -o "$IPA" "$IPA_ARG"
fi
if [[ ! -f "$IPA" ]]; then
  echo "❌ Fichier introuvable : $IPA"
  exit 1
fi

cleanup() { [[ -n "$TMPDIR_DL" ]] && rm -rf "$TMPDIR_DL"; }
trap cleanup EXIT

# 3) Détecter l'iPhone -------------------------------------------------------
echo ">> Recherche de l'iPhone (branché, déverrouillé, « Se fier à cet ordinateur »)…"
UDID="$(idevice_id -l 2>/dev/null | head -n1 || true)"
if [[ -z "$UDID" ]]; then
  echo "❌ Aucun iPhone détecté."
  echo "   • Branche-le en USB, déverrouille-le, et touche « Se fier »."
  echo "   • Vérifie qu'il apparaît dans le Finder."
  exit 1
fi
echo "   iPhone détecté — UDID : $UDID"

# 4) Installer ---------------------------------------------------------------
echo ">> Installation en cours…"
if ideviceinstaller -u "$UDID" -i "$IPA"; then
  echo "✅ Installé ! Regarde l'écran d'accueil de l'iPhone."
  echo "   (Si l'app ne s'ouvre pas : Réglages → Général → VPN et gestion de l'appareil → Faire confiance.)"
else
  echo ""
  echo "❌ Échec de l'installation. Le message ci-dessus donne la VRAIE cause :"
  echo "   • ApplicationVerificationFailed .......... certificat non fiable ou RÉVOQUÉ"
  echo "   • MismatchedApplicationIdentifierEntitlement  profil ≠ bundle id de l'app"
  echo "   • 0xE8008015 / device not in provisioning  l'UDID de cet iPhone n'est pas dans le profil"
  echo ""
  echo "   👉 L'UDID de CET iPhone est : $UDID"
  echo "      Il doit figurer dans ton .mobileprovision. Sinon, demande à AppleP12"
  echo "      un profil incluant cet UDID (ou un certificat d'entreprise)."
  exit 1
fi
