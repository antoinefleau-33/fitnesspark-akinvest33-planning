# Terminal Linux pour iPhone (fork iSH personnalisé)

Application iOS de terminal Linux basée sur [iSH](https://github.com/ish-app/ish)
(émulateur x86 en usermode faisant tourner un vrai Alpine Linux localement sur
l'iPhone — pas besoin de serveur distant).

## Ce qui est préinstallé

Le rootfs Alpine embarqué dans l'app est personnalisé avec :

- **bash** (shell de connexion par défaut de root)
- **zsh**
- **git**
- **cloudflared** (binaire officiel Cloudflare linux-386)
- openssh-client, curl, ca-certificates
- `apk` reste disponible pour installer d'autres paquets (`apk add python3`, etc.)

> Note : iOS interdit le fork/exec natif et le JIT, c'est pourquoi un Termux
> natif est impossible sur iPhone. iSH émule un CPU x86 : tout fonctionne,
> mais les programmes lourds (dont cloudflared) sont plus lents que du natif.

## Compilation

Tout se fait via GitHub Actions (`.github/workflows/build-ish-terminal.yml`) :

1. Un job Linux construit le rootfs Alpine x86 personnalisé.
2. Un job macOS compile iSH avec Xcode **sans signature de code**, remplace le
   `root.tar.gz` embarqué par le rootfs personnalisé, et produit
   `iSH-terminal-unsigned.ipa`.

Le fichier `.ipa` est disponible dans les **artifacts** du run GitHub Actions
(onglet *Actions* du dépôt → run « Build iSH Terminal IPA » → artifact
`iSH-terminal-unsigned-ipa`).

## Signature et installation (sans App Store)

L'`.ipa` est **non signé** — à signer soi-même, au choix :

- **Sideloadly** ou **AltStore** : glisser l'`.ipa`, se connecter avec son
  Apple ID (gratuit : app valable 7 jours, renouvelable ; compte développeur
  payant : 1 an).
- **Xcode** : *Window → Devices and Simulators*, ou re-signature avec
  `codesign` / `xcrun` et son profil de provisioning.
- **Certificat entreprise / TrollStore** selon votre situation.

Après installation, au premier lancement l'app extrait le rootfs : bash, zsh,
git et cloudflared sont immédiatement disponibles (`cloudflared tunnel --help`).

## Licences

iSH est distribué sous licence GPL/MIT (voir le dépôt iSH). Usage personnel,
pas de publication App Store prévue.
