#!/usr/bin/env bash
#
# FreePlay OS — construit une ISO d'installation 100 % automatique à partir
# de l'ISO officielle Ubuntu Desktop 24.04.
#
# À lancer sur un PC Linux (ou WSL2 sous Windows) :
#   1. Télécharge l'ISO officielle : https://releases.ubuntu.com/24.04/
#      (fichier ubuntu-24.04.x-desktop-amd64.iso)
#   2. sudo apt install xorriso
#   3. ./build-iso.sh /chemin/vers/ubuntu-24.04.x-desktop-amd64.iso
#
# Résultat : iso/freeplay-os.iso — à tester en machine virtuelle d'abord
# (docs/01-TESTER-EN-VM.md), puis à flasher sur une clé USB
# (docs/02-INSTALLER-SUR-LA-MACHINE.md).
#
# ⚠️ L'ISO produite EFFACE TOUT LE DISQUE de la machine sur laquelle
#    on l'installe, sans poser de question.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
WORK_DIR="$SCRIPT_DIR/work"
OUT_ISO="$SCRIPT_DIR/freeplay-os.iso"

die() { echo "ERREUR : $*" >&2; exit 1; }

SRC_ISO="${1:-}"
[[ -n "$SRC_ISO" ]] || die "Usage : ./build-iso.sh /chemin/vers/ubuntu-24.04.x-desktop-amd64.iso"
[[ -f "$SRC_ISO" ]] || die "ISO introuvable : $SRC_ISO"
command -v xorriso >/dev/null 2>&1 || die "xorriso manquant. Installe-le avec : sudo apt install xorriso"

echo "==> Nettoyage du dossier de travail…"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/iso"

echo "==> Extraction de l'ISO Ubuntu (quelques minutes)…"
xorriso -osirrox on -indev "$SRC_ISO" -extract / "$WORK_DIR/iso" >/dev/null 2>&1
chmod -R u+w "$WORK_DIR/iso"

echo "==> Récupération des paramètres de démarrage de l'ISO d'origine…"
xorriso -indev "$SRC_ISO" -report_el_torito as_mkisofs > "$WORK_DIR/mkisofs_opts.txt" 2>/dev/null

echo "==> Ajout de la configuration FreePlay OS…"
FP_DIR="$WORK_DIR/iso/freeplay"
mkdir -p "$FP_DIR/payload"
cp "$SCRIPT_DIR/autoinstall/user-data" "$FP_DIR/user-data"
cp "$SCRIPT_DIR/autoinstall/meta-data" "$FP_DIR/meta-data"
cp -r "$REPO_DIR/setup" "$REPO_DIR/config" "$FP_DIR/payload/"
[[ -f "$REPO_DIR/README.md" ]] && cp "$REPO_DIR/README.md" "$FP_DIR/payload/"

echo "==> Activation de l'installation automatique dans le menu de démarrage…"
GRUB_CFG="$WORK_DIR/iso/boot/grub/grub.cfg"
[[ -f "$GRUB_CFG" ]] || die "grub.cfg introuvable dans l'ISO — est-ce bien une ISO Ubuntu Desktop ?"
sed -i 's|---|autoinstall ds=nocloud\\;s=/cdrom/freeplay/ ---|g' "$GRUB_CFG"
sed -i 's|Try or Install Ubuntu|Installer FreePlay OS (EFFACE LE DISQUE)|g' "$GRUB_CFG"

echo "==> Construction de l'ISO finale…"
rm -f "$OUT_ISO"
# shellcheck disable=SC2046
eval "xorriso -as mkisofs $(cat "$WORK_DIR/mkisofs_opts.txt") -o '$OUT_ISO' '$WORK_DIR/iso'" >/dev/null 2>&1

rm -rf "$WORK_DIR"
echo ""
echo "✅ Terminé : $OUT_ISO"
echo "   1. Teste-la d'abord en machine virtuelle  → docs/01-TESTER-EN-VM.md"
echo "   2. Puis flashe-la sur une clé USB          → docs/02-INSTALLER-SUR-LA-MACHINE.md"
echo "   Compte créé : freeplay / freeplay (mot de passe à changer ensuite)"
