# 🔏 IPA / TIPA Signer

Un site web qui **signe vos fichiers `.ipa` / `.tipa` avec vos propres certificats Apple**
(fichier `.p12` + mot de passe + profil `.mobileprovision`), que vous avez achetés
vous-même sur [AppleP12.com](https://applep12.com) ou un service équivalent.

La signature est **réelle** et se fait côté serveur grâce à
[`zsign`](https://github.com/zhlynn/zsign) — pas besoin de Mac.

> ⚖️ **Cadre d'usage.** Cet outil n'utilise **que le certificat que vous fournissez**.
> Il ne délivre aucune signature mutualisée et ne contourne aucune protection :
> il applique votre propre signature, exactement comme le ferait Xcode. C'est le
> workflow classique de sideloading (AltStore, Sideloadly, TrollStore…).

---

## Ce que ça fait

1. Vous déposez : l'app (`.ipa`/`.tipa`), votre certificat (`.p12`), votre profil (`.mobileprovision`) + le mot de passe du P12.
2. Le serveur re-signe l'app avec `zsign`.
3. Vous téléchargez l'`.ipa` signé, prêt à installer.

**Confidentialité :** chaque signature s'exécute dans un dossier temporaire isolé,
supprimé immédiatement après l'envoi de la réponse. Le mot de passe n'est **jamais**
journalisé ni conservé.

---

## Lancer en local

### Avec Docker (recommandé — compile `zsign` automatiquement)

```bash
cd ipa-signer
docker build -t ipa-signer .
docker run -p 3000:3000 ipa-signer
# → http://localhost:3000
```

### Sans Docker (Node.js)

Il faut alors installer `zsign` vous-même et le rendre accessible dans le `PATH`
(ou pointer `ZSIGN_BIN` vers le binaire) :

```bash
# 1) Compiler zsign (Linux, nécessite g++, make, pkg-config, libssl-dev)
sudo apt-get install -y git g++ make pkg-config libssl-dev
git clone --depth 1 https://github.com/zhlynn/zsign.git
cd zsign/build/linux && make clean && make
sudo cp ../../bin/zsign /usr/local/bin/ && cd ../../..

# 2) Lancer le serveur
cd ipa-signer
npm install
ZSIGN_BIN=/usr/local/bin/zsign npm start
# → http://localhost:3000
```

Vérifiez que `zsign` est bien détecté : `curl http://localhost:3000/api/health`.

---

## Déploiement gratuit

L'app est un simple conteneur Docker qui écoute sur `$PORT` (3000 par défaut).
Trois hébergeurs proposent une offre gratuite qui fait tourner ce conteneur :

### Option A — Render (le plus simple)

1. Poussez ce repo sur GitHub.
2. Sur [render.com](https://render.com) → **New** → **Web Service** → connectez le repo.
3. Réglages :
   - **Root Directory** : `ipa-signer`
   - **Runtime** : `Docker`
   - **Instance Type** : `Free`
4. Créez. Render compile l'image (zsign inclus) et publie une URL `https://…onrender.com`.

> Le fichier [`render.yaml`](./render.yaml) permet aussi un déploiement en un clic
> via *Blueprint*. Note : le plan gratuit se met en veille après inactivité
> (~30 s de réveil) et limite la RAM (512 Mo → privilégiez des IPA de taille raisonnable).

### Option B — Fly.io

```bash
cd ipa-signer
fly launch --copy-config --now     # utilise fly.toml + Dockerfile
```

Offre gratuite avec petites machines qui s'éteignent au repos et redémarrent à la demande.

### Option C — Hugging Face Spaces (RAM plus généreuse, gratuit)

1. Créez un **Space** → SDK **Docker**.
2. Copiez-y le contenu du dossier `ipa-signer/` (dont le `Dockerfile`).
3. Ajoutez ces lignes tout en haut du `README.md` du Space (frontmatter) :

   ```yaml
   ---
   title: IPA Signer
   sdk: docker
   app_port: 7860
   ---
   ```

4. Le serveur lit `$PORT` (fourni par HF = 7860), aucune autre config requise.

---

## Configuration (variables d'environnement)

| Variable        | Défaut               | Rôle                                             |
|-----------------|----------------------|--------------------------------------------------|
| `PORT`          | `3000`               | Port d'écoute HTTP                                |
| `MAX_UPLOAD_MB` | `2048`               | Taille max d'un fichier uploadé (Mo)             |
| `ZSIGN_BIN`     | `zsign`              | Chemin du binaire zsign                          |
| `WORK_ROOT`     | `<tmp>/ipa-signer`   | Dossier des jobs temporaires                     |

---

## API

- `GET  /api/health` → `{ ok, zsign }` (vérifie que zsign répond)
- `POST /api/sign` (multipart/form-data) :
  - `ipa` *(fichier, requis)* — l'app `.ipa`/`.tipa`
  - `p12` *(fichier, requis)* — certificat `.p12`
  - `mobileprovision` *(fichier, requis)* — profil de provisioning
  - `password` *(texte)* — mot de passe du P12 (vide si aucun)
  - `bundleId`, `bundleName` *(texte, facultatifs)* — pour modifier l'identité de l'app
  - **Réponse** : le fichier `.ipa` signé (200), ou un JSON `{ ok:false, error, log }` en cas d'échec.

---

## Dépannage

| Symptôme                                   | Cause probable                                             |
|--------------------------------------------|-----------------------------------------------------------|
| « Mot de passe du P12 incorrect »          | Mauvais mot de passe, ou P12 corrompu/incompatible        |
| « Profil de provisioning invalide »        | Le `.mobileprovision` ne correspond pas au certificat     |
| Signature OK mais l'app ne s'installe pas  | Bundle ID/UDID non couverts par le profil, cert. révoqué  |
| `zsign: introuvable` sur `/api/health`     | Binaire non installé (hors Docker) — voir « Sans Docker » |

---

*Signature locale via `zsign`. Vos certificats, vos règles.*
