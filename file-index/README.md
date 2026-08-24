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

### Lister un site entier avec sa seule adresse

Tu donnes l'adresse, l'index se débrouille : il ouvre la page, suit les liens internes
et liste les fichiers qu'il trouve, avec leur taille et leur date.

```json
{
  "sites": [
    "https://mon-site.fr/",
    { "url": "https://autre-site.fr/downloads/", "depth": 3, "extensions": ["exe", "zip"] }
  ]
}
```

| Réglage | Défaut | Rôle |
| --- | --- | --- |
| `depth` | `2` | Nombre de niveaux de liens suivis depuis la page de départ |
| `maxPages` | `25` | Plafond de pages lues, pour ne pas explorer un site sans fin |
| `maxFiles` | `200` | Plafond de fichiers listés |
| `extensions` | binaires et documents | Liste à lister, ou `"all"` pour absolument tout |
| `path` | le dossier de l'URL donnée | Ne garder que ce qui est sous ce chemin |

L'exploration reste **sur le domaine du site** : un lien vers un autre domaine n'est
suivi que s'il est autorisé dans `allowedHosts`. Les habillages du site (`.css`, `.js`,
polices, images) sont écartés par défaut — mets `"extensions": "all"` pour les voir.

Les listings de dossier générés par Apache/nginx sont lus comme n'importe quelle page.

**Ce que ça ne peut pas faire** : trouver des fichiers qu'aucune page ne mentionne.
Un site qui garde ses fichiers en base, derrière une connexion, ou qui les renvoie
directement en réponse à un formulaire (sans les ranger à une URL) n'a rien d'indexable —
aucun outil ne peut deviner des adresses qui ne sont écrites nulle part.

### Ajouter un fichier hébergé ailleurs (CDN, autre hébergeur)

Colle l'URL dans `config.json`, c'est tout — le build va interroger le lien
(requête `HEAD`, sans télécharger le fichier) pour récupérer **taille, type et date** :

```json
{
  "allowedHosts": ["cdn.mon-site.fr", "*.mon-autre-site.com"],
  "links": [
    "https://cdn.mon-site.fr/setup-v2.1.exe",
    {
      "name": "Installeur v2.1",
      "url": "https://cdn.mon-site.fr/dl/build-final",
      "description": "Version stable"
    }
  ]
}
```

Ce que le build sait retrouver tout seul :

| Info | D'où elle vient |
| --- | --- |
| Taille | `content-length`, ou `content-range` si l'hébergeur refuse `HEAD` |
| Date | `last-modified` |
| Nom | `content-disposition`, sinon la fin de l'URL |
| Type | l'extension de l'URL, sinon le `content-type` (ex. `application/x-msdownload` → `exe`) |

Tu peux toujours forcer une valeur en l'écrivant dans le lien (`name`, `description`,
`size`, `modified`) : ce que tu écris a la priorité sur ce que le serveur annonce.

Un lien qui ne répond pas reste listé, mais il est marqué **« lien mort ? »** en rouge
sur la page et signalé dans les logs de build.

#### Domaines autorisés

`allowedHosts` accepte les jokers :

| Motif | Couvre |
| --- | --- |
| `mon-site.fr` | `mon-site.fr` **et** `cdn.mon-site.fr` |
| `*.mon-site.fr` | les sous-domaines uniquement |
| `cdn.*` | n'importe quel domaine commençant par `cdn.` |

Pour désactiver le filtre entièrement : `"allowAnyHost": true`.
Pour un build sans aucune requête réseau : `"fetchMetadata": false`,
ou `SKIP_LINK_FETCH=1 npm run scan`.

**Limite connue** : un CDN qui stocke ses fichiers texte déjà compressés (jsDelivr par
exemple) annonce la taille compressée. Sans effet sur les `.exe` / `.zip` / `.msi`,
qui ne sont jamais recompressés — vérifié, la taille annoncée y est exacte à l'octet.

## En ligne

**https://mes-fichiers-eta.vercel.app** — projet Vercel `mes-fichiers` (équipe `zfrcfs-projects`).

> L'autre adresse, `mes-fichiers-zfrcfs-projects.vercel.app`, est protégée par le SSO Vercel
> (elle renvoie vers une page de connexion) : c'est l'URL interne de l'équipe, pas celle à partager.

### Mettre le site à jour

Le projet Vercel **n'est pas relié au dépôt GitHub** : le compte Vercel est connecté au GitHub
`zfrcf`, qui n'a pas les droits d'écriture sur `antoinefleau-33/fitnesspark-akinvest33-planning`.
Un `git push` ne redéploie donc rien pour l'instant. Trois façons de publier :

1. **Depuis ta machine** (le plus simple) :

   ```bash
   cd file-index
   npx vercel --prod
   ```

2. **Rétablir le lien GitHub** pour retrouver le déploiement automatique à chaque push :
   donner à `zfrcf` un accès en écriture au dépôt, ou connecter le compte GitHub
   `antoinefleau-33` à Vercel, puis relier le projet (Settings > Git) avec
   **Root Directory** = `file-index`.

3. Me redemander un déploiement.

## Déploiement Vercel (nouveau projet)

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
