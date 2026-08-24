# Mes fichiers — index HTML des fichiers hébergés

Un site statique qui liste **tous tes fichiers hébergés** (.exe, .zip, .pdf, …) avec un lien
de téléchargement direct pour chacun. Recherche, filtre par type, tri, et bouton « Copier le lien ».

Deux sources de fichiers :

1. **Locaux** — tout ce que tu déposes dans `public/files/` est détecté automatiquement
   (sous-dossiers inclus), avec sa taille et sa date.
2. **Externes** — les fichiers hébergés sur **tes autres sites**, déclarés dans `config.json` > `links`.
   Un lien dont le domaine n'est pas dans `allowedHosts` est **refusé au build** : impossible de
   lister par erreur un fichier qui n'est pas à toi.

## Utilisation

```bash
npm run scan     # régénère public/files.json depuis public/files/ + config.json
npm start        # scan + serveur local sur http://localhost:3000
```

### Ajouter un fichier

```bash
cp MonApp-Setup.exe public/files/      # ou public/files/outils/MonApp-Setup.exe
npm run scan
git add -A && git commit -m "Ajout MonApp-Setup.exe" && git push
```

Vercel relance `npm run build` (= le scan) à chaque push : la liste est toujours à jour,
tu n'as jamais à éditer le HTML.

### Ajouter un fichier hébergé ailleurs

Dans `config.json` :

```json
{
  "allowedHosts": ["mon-autre-site.vercel.app"],
  "links": [
    {
      "name": "Installeur v2.1.exe",
      "url": "https://mon-autre-site.vercel.app/dl/setup-v2.1.exe",
      "description": "Version stable",
      "size": 48200000
    }
  ]
}
```

`size` et `modified` sont facultatifs (impossible de les deviner à distance).

## Déploiement Vercel

Nouveau projet Vercel sur ce repo, avec :

- **Root Directory** : `file-index` (Settings > General > Build & Deployment)
- Framework preset : *Other* (le reste est déjà dans `vercel.json` :
  build `node scripts/build-manifest.js`, output `public`)

Tant que le code vit sur une branche autre que la branche par défaut, il faut aussi
pointer la production dessus : **Settings > Environments > Production > Branch Tracking**,
saisir le nom de la branche, puis *Save*. Une fois le code fusionné dans `main`,
remettre `main` ici.

Le dossier `file-index/` est indépendant du `index.html` à la racine du repo
(l'app de planning) : les deux peuvent être déployés comme deux projets Vercel séparés.

## Limites à connaître

- **GitHub** refuse les fichiers de plus de 100 Mo (avertissement dès 50 Mo). Pour un gros `.exe`,
  passe par une *Release* GitHub ou Vercel Blob, et déclare l'URL dans `config.json` > `links`.
- Un déploiement Vercel est plafonné à quelques centaines de Mo : garde `public/files/` léger.
- La page est en `noindex` et le site n'est pas listé publiquement, mais **une URL Vercel reste
  publique** : n'y mets rien de confidentiel sans protection (Vercel Authentication / mot de passe).
