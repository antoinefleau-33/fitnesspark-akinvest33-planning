# Xcodeproj → IPA non signé

Petit site Flask à déployer sur [Render](https://render.com) : vous uploadez un `.zip`
de votre projet Xcode et vous récupérez un `.ipa` **non signé**.

## Déploiement sur Render

1. Poussez ce repo sur GitHub (déjà fait).
2. Sur Render : **New → Blueprint**, choisissez ce repo — le fichier `render.yaml`
   configure tout (Python, gunicorn, plan free).
   Ou en manuel : **New → Web Service**, build `pip install -r requirements.txt`,
   start `gunicorn app:app --timeout 300`.

## Comment ça marche

- **Zip contenant un `.app` compilé** (ex. sorti de `xcodebuild` / Xcode → Product → Build) :
  le bundle est repackagé en `Payload/NomApp.app` → `.ipa` installable après signature.
- **Zip contenant seulement les sources** (`.xcodeproj` + `Info.plist` + fichiers Swift) :
  le serveur Linux ne peut pas compiler avec Xcode ; il génère un IPA *structurel*
  (Info.plist + ressources, sans binaire Mach-O). Il ne s'installera pas tel quel —
  compilez d'abord avec Xcode, puis repassez le `.app` dans le convertisseur.
- L'IPA produit n'est **pas signé** : signez-le avec Xcode, AltStore, Sideloadly, etc.

## Lancer en local

```bash
pip install -r requirements.txt
python app.py
# → http://localhost:5000
```
