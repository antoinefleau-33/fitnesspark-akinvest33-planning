#!/usr/bin/env node
/**
 * Scanne public/files/ et fusionne avec les liens externes de config.json
 * pour produire public/files.json (le manifeste lu par le site).
 *
 * Les liens externes (CDN, autre hébergement…) sont interrogés en HEAD au build
 * pour récupérer automatiquement taille, type et date de dernière modification.
 *
 * Usage : node scripts/build-manifest.js
 *         SKIP_LINK_FETCH=1 node scripts/build-manifest.js   (build hors ligne)
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const FILES_DIR = path.join(ROOT, 'public', 'files');
const CONFIG_PATH = path.join(ROOT, 'config.json');
const OUTPUT_PATH = path.join(ROOT, 'public', 'files.json');

// Fichiers techniques à ne jamais lister
const IGNORED = new Set(['.gitkeep', '.DS_Store', 'Thumbs.db', 'desktop.ini', '.gitignore']);

const FETCH_TIMEOUT_MS = 15000;
// Les pages sont plus lentes : un hébergeur gratuit (Render, Fly…) met parfois
// 15 à 40 s à réveiller son instance avant de répondre.
const PAGE_TIMEOUT_MS = 45000;
const FETCH_CONCURRENCY = 6;

const DEFAULT_DEPTH = 2;
const DEFAULT_MAX_PAGES = 25;
const DEFAULT_MAX_FILES = 200;

// Extensions considérées comme "un fichier à lister" par défaut.
// Les habillages du site (css, js, polices, icônes) sont volontairement exclus.
const DEFAULT_FILE_EXTENSIONS = [
  'exe', 'msi', 'apk', 'ipa', 'tipa', 'dmg', 'pkg', 'deb', 'rpm', 'appimage', 'jar', 'iso', 'bin',
  'zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'csv', 'txt', 'md',
  'mp3', 'wav', 'flac', 'mp4', 'mkv', 'avi', 'mov',
  'p12', 'pfx', 'mobileprovision', 'cer', 'crt', 'pem'
];

// Extensions qui désignent une page à explorer, pas un fichier à télécharger.
const PAGE_EXTENSIONS = new Set(['', 'html', 'htm', 'php', 'asp', 'aspx', 'jsp', 'xhtml']);

// Types MIME les plus courants -> extension affichée quand l'URL n'en donne pas.
// application/octet-stream est volontairement absent : trop générique pour conclure.
const MIME_EXT = {
  'application/vnd.microsoft.portable-executable': 'exe',
  'application/x-msdownload': 'exe',
  'application/x-msdos-program': 'exe',
  'application/x-msi': 'msi',
  'application/x-ms-installer': 'msi',
  'application/zip': 'zip',
  'application/x-zip-compressed': 'zip',
  'application/x-7z-compressed': '7z',
  'application/vnd.rar': 'rar',
  'application/x-rar-compressed': 'rar',
  'application/gzip': 'gz',
  'application/x-tar': 'tar',
  'application/pdf': 'pdf',
  'application/json': 'json',
  'application/javascript': 'js',
  'application/vnd.android.package-archive': 'apk',
  'application/x-apple-diskimage': 'dmg',
  'text/javascript': 'js',
  'text/plain': 'txt',
  'text/html': 'html',
  'text/css': 'css',
  'text/csv': 'csv',
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/svg+xml': 'svg',
  'video/mp4': 'mp4',
  'audio/mpeg': 'mp3'
};

function readConfig() {
  if (!fs.existsSync(CONFIG_PATH)) {
    throw new Error(`config.json introuvable : ${CONFIG_PATH}`);
  }
  const raw = fs.readFileSync(CONFIG_PATH, 'utf8');
  let config;
  try {
    config = JSON.parse(raw);
  } catch (err) {
    throw new Error(`config.json invalide (JSON) : ${err.message}`);
  }
  config.siteName = config.siteName || 'Mes fichiers';
  config.tagline = config.tagline || '';
  config.allowedHosts = Array.isArray(config.allowedHosts) ? config.allowedHosts : [];
  config.links = Array.isArray(config.links) ? config.links : [];
  config.sites = Array.isArray(config.sites) ? config.sites : [];
  config.allowAnyHost = config.allowAnyHost === true;
  config.fetchMetadata = config.fetchMetadata !== false;
  return config;
}

function humanSize(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} o`;
  const units = ['Ko', 'Mo', 'Go', 'To'];
  let value = bytes / 1024;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

function extOf(name) {
  const ext = path.extname(name).toLowerCase().replace('.', '');
  return ext || 'fichier';
}

/** Encode chaque segment du chemin pour l'URL, sans encoder les "/". */
function encodePath(relPath) {
  return relPath.split('/').map(encodeURIComponent).join('/');
}

function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Un host est autorisé si le motif correspond exactement, s'il en est un
 * sous-domaine, ou s'il correspond à un motif à joker :
 *   "mon-site.fr"      -> mon-site.fr et cdn.mon-site.fr
 *   "*.mon-site.fr"    -> cdn.mon-site.fr, a.b.mon-site.fr (pas mon-site.fr)
 *   "cdn.*"            -> cdn.nimportequoi.com
 */
function hostMatches(host, pattern) {
  const p = String(pattern).toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '').trim();
  if (!p) return false;
  if (p === '*') return true;
  if (p.includes('*')) {
    const rx = new RegExp(`^${p.split('*').map(escapeRegex).join('.*')}$`);
    return rx.test(host);
  }
  return host === p || host.endsWith(`.${p}`);
}

function isAllowedHost(host, allowedHosts) {
  const h = host.toLowerCase();
  return allowedHosts.some((pattern) => hostMatches(h, pattern));
}

/** Parcours récursif de public/files/. */
function scanDir(dir, relBase = '') {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name, 'fr'));

  for (const entry of entries) {
    if (IGNORED.has(entry.name) || entry.name.startsWith('.')) continue;
    const abs = path.join(dir, entry.name);
    const rel = relBase ? `${relBase}/${entry.name}` : entry.name;

    if (entry.isDirectory()) {
      out.push(...scanDir(abs, rel));
      continue;
    }
    if (!entry.isFile()) continue;

    const stat = fs.statSync(abs);
    out.push({
      id: `local:${rel}`,
      name: entry.name,
      folder: relBase || '',
      ext: extOf(entry.name),
      size: stat.size,
      sizeLabel: humanSize(stat.size),
      modified: stat.mtime.toISOString(),
      url: `/files/${encodePath(rel)}`,
      source: 'local',
      host: '',
      description: ''
    });
  }
  return out;
}

/**
 * Une requête, avec délai maximum.
 * Accept-Encoding: identity est indispensable : sans lui le serveur renvoie le
 * corps compressé et content-length donne la taille gzip, pas celle du fichier.
 */
async function request(url, method, extraHeaders) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  const headers = { 'Accept-Encoding': 'identity', ...extraHeaders };
  try {
    return await fetch(url, { method, headers, redirect: 'follow', signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** Taille annoncée par une réponse, uniquement si elle n'est pas compressée. */
function sizeFromResponse(res) {
  // Un corps compressé (gzip/br) donne une longueur qui n'est pas celle du fichier.
  const encoding = (res.headers.get('content-encoding') || '').toLowerCase();
  const compressed = encoding && encoding !== 'identity';

  const contentRange = res.headers.get('content-range');
  if (contentRange) {
    const match = /\/(\d+)\s*$/.exec(contentRange);
    if (match && !compressed) return Number(match[1]);
    return null;
  }

  if (compressed) return null;
  const contentLength = res.headers.get('content-length');
  if (contentLength && /^\d+$/.test(contentLength)) return Number(contentLength);
  return null;
}

/**
 * Récupère taille / type / date d'un fichier distant, sans le télécharger.
 *
 * 1. HEAD : suffit pour la plupart des hébergeurs.
 * 2. Si la taille manque (réponse en chunked) ou si HEAD est refusé :
 *    GET du premier octet seulement (Range) — le total est dans content-range.
 *
 * Limite connue : un CDN qui stocke ses fichiers texte déjà compressés
 * (jsDelivr par exemple) annonce la taille compressée jusque dans content-range.
 * Sans effet sur les .exe / .zip / .msi, qui ne sont jamais recompressés.
 */
async function fetchMeta(url) {
  let res = null;
  let size = null;

  try {
    res = await request(url, 'HEAD');
    if (res.ok) size = sizeFromResponse(res);
  } catch {
    res = null;
  }

  if (!res || !res.ok || size === null) {
    try {
      const ranged = await request(url, 'GET', { Range: 'bytes=0-0' });
      if (ranged.ok || ranged.status === 206) {
        const rangedSize = sizeFromResponse(ranged);
        if (rangedSize !== null) size = rangedSize;
        if (!res || !res.ok) res = ranged;
      } else if (!res) {
        return { ok: false, error: `HTTP ${ranged.status}` };
      }
    } catch (err) {
      if (!res || !res.ok) {
        const reason = err.name === 'AbortError' ? `pas de réponse en ${FETCH_TIMEOUT_MS / 1000}s` : err.message;
        return { ok: false, error: reason };
      }
    }
  }

  if (!res || (!res.ok && res.status !== 206)) {
    return { ok: false, error: res ? `HTTP ${res.status}` : 'injoignable' };
  }

  const contentType = (res.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();

  let modified = null;
  const lastModified = res.headers.get('last-modified');
  if (lastModified) {
    const date = new Date(lastModified);
    if (!isNaN(date.getTime())) modified = date.toISOString();
  }

  // Nom de fichier proposé par le serveur (content-disposition), s'il y en a un.
  let filename = null;
  const disposition = res.headers.get('content-disposition') || '';
  const nameMatch = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  if (nameMatch) {
    try {
      filename = decodeURIComponent(nameMatch[1].trim());
    } catch {
      filename = nameMatch[1].trim();
    }
  }

  return { ok: true, size, contentType, modified, filename };
}

/** Récupère le HTML d'une page. Deux tentatives : un hébergeur endormi met du temps. */
async function fetchPage(url, warnings) {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), PAGE_TIMEOUT_MS);
      let res;
      try {
        res = await fetch(url, {
          method: 'GET',
          headers: { 'Accept-Encoding': 'identity', Accept: 'text/html,*/*' },
          redirect: 'follow',
          signal: controller.signal
        });
      } finally {
        clearTimeout(timer);
      }

      if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };

      const type = (res.headers.get('content-type') || '').toLowerCase();
      if (!type.includes('html')) return { ok: false, error: `pas du HTML (${type || 'type inconnu'})` };

      return { ok: true, html: await res.text(), finalUrl: res.url || url };
    } catch (err) {
      if (attempt === 2) {
        const reason = err.name === 'AbortError' ? `pas de réponse en ${PAGE_TIMEOUT_MS / 1000}s` : err.message;
        return { ok: false, error: reason };
      }
      warnings.push(`Nouvelle tentative sur ${url} (${err.name === 'AbortError' ? 'trop lent' : err.message})`);
    }
  }
  return { ok: false, error: 'inconnu' };
}

/** Extrait les URL référencées par une page (liens, ressources, listings de dossier). */
function extractUrls(html, pageUrl) {
  const found = new Set();
  const rx = /(?:href|src|data-href|data-url)\s*=\s*["']([^"']+)["']/gi;
  let match;

  while ((match = rx.exec(html)) !== null) {
    const raw = match[1].trim();
    if (!raw || raw.startsWith('#') || /^(mailto|tel|javascript|data):/i.test(raw)) continue;
    try {
      const url = new URL(raw, pageUrl);
      url.hash = '';
      found.add(url.href);
    } catch {
      /* lien malformé : on l'ignore */
    }
  }
  return [...found];
}

/** Extension d'une URL, sans le point, en minuscules. */
function urlExtension(url) {
  const last = url.pathname.split('/').filter(Boolean).pop() || '';
  return path.extname(last).toLowerCase().replace('.', '');
}

/**
 * Explore un site à partir de sa seule adresse et renvoie les fichiers trouvés.
 *
 * Suit les liens internes (même hôte) jusqu'à `depth` niveaux, sans dépasser
 * `maxPages` pages ni `maxFiles` fichiers. Gère aussi bien les pages classiques
 * que les listings de dossier générés par Apache/nginx.
 */
async function crawlSite(rawSite, warnings) {
  const site = typeof rawSite === 'string' ? { url: rawSite } : rawSite || {};

  let root;
  try {
    root = new URL(String(site.url).trim());
  } catch {
    warnings.push(`Site ignoré (URL invalide) : ${site.url}`);
    return [];
  }

  const depth = Number.isFinite(site.depth) ? site.depth : DEFAULT_DEPTH;
  const maxPages = Number.isFinite(site.maxPages) ? site.maxPages : DEFAULT_MAX_PAGES;
  const maxFiles = Number.isFinite(site.maxFiles) ? site.maxFiles : DEFAULT_MAX_FILES;

  const allExtensions = site.extensions === 'all';
  const extensions = new Set(
    allExtensions ? [] : (Array.isArray(site.extensions) ? site.extensions : DEFAULT_FILE_EXTENSIONS)
      .map((e) => String(e).toLowerCase().replace(/^\./, ''))
  );

  // Ne garder que ce qui est sous ce chemin (ex. "/downloads/"), si demandé.
  const prefix = site.path ? String(site.path) : root.pathname.replace(/[^/]*$/, '');

  const queue = [{ url: root.href, level: 0 }];
  const visited = new Set([root.href]);
  const files = new Set();
  let pagesRead = 0;

  while (queue.length && pagesRead < maxPages && files.size < maxFiles) {
    const { url, level } = queue.shift();
    const page = await fetchPage(url, warnings);
    pagesRead++;

    if (!page.ok) {
      warnings.push(`Page illisible : ${url} (${page.error})`);
      continue;
    }

    for (const href of extractUrls(page.html, page.finalUrl)) {
      let target;
      try {
        target = new URL(href);
      } catch {
        continue;
      }

      if (target.hostname !== root.hostname) continue;
      if (!target.pathname.startsWith(prefix)) continue;

      const ext = urlExtension(target);

      if (PAGE_EXTENSIONS.has(ext)) {
        // Une page : on l'explore si on n'a pas atteint la profondeur demandée.
        if (level < depth && !visited.has(target.href) && visited.size < maxPages * 4) {
          visited.add(target.href);
          queue.push({ url: target.href, level: level + 1 });
        }
        continue;
      }

      if (!allExtensions && !extensions.has(ext)) continue;
      if (files.size < maxFiles) files.add(target.href);
    }
  }

  if (files.size >= maxFiles) {
    warnings.push(`${root.hostname} : limite de ${maxFiles} fichiers atteinte, la liste est tronquée.`);
  }
  if (pagesRead >= maxPages && queue.length) {
    warnings.push(`${root.hostname} : limite de ${maxPages} pages atteinte, ${queue.length} page(s) non explorée(s).`);
  }

  console.log(`[manifest]   ${root.hostname} : ${pagesRead} page(s) lue(s), ${files.size} fichier(s) trouvé(s)`);
  return [...files].map((url) => ({ url, discoveredOn: root.hostname }));
}

/** Explore tous les sites déclarés dans config.json > sites. */
async function crawlSites(config, warnings) {
  const results = [];
  for (const site of config.sites) {
    results.push(...(await crawlSite(site, warnings)));
  }
  return results;
}

/** Exécute des tâches par petits paquets pour ne pas marteler les serveurs. */
async function inBatches(items, size, worker) {
  const results = [];
  for (let i = 0; i < items.length; i += size) {
    const batch = items.slice(i, i + size);
    results.push(...(await Promise.all(batch.map(worker))));
  }
  return results;
}

/**
 * Valide et normalise les liens externes : ceux écrits dans config.json > links
 * et ceux découverts automatiquement en explorant config.json > sites.
 */
async function buildExternalLinks(config, warnings, discovered = []) {
  // Un lien peut être une simple chaîne (l'URL) ou un objet complet.
  const written = config.links.map((link) => (typeof link === 'string' ? { url: link } : link || {}));
  const entries = [...written, ...discovered];

  // Les sites que tu as toi-même déclarés sont autorisés d'office.
  const siteHosts = config.sites
    .map((site) => {
      try {
        return new URL(String(typeof site === 'string' ? site : site && site.url).trim()).hostname;
      } catch {
        return null;
      }
    })
    .filter(Boolean);
  const allowedHosts = [...config.allowedHosts, ...siteHosts];

  const candidates = [];

  entries.forEach((link, index) => {
    const label = link.name || link.url || `links[${index}]`;

    if (typeof link.url !== 'string' || !link.url.trim()) {
      warnings.push(`Lien ignoré (url manquante) : ${label}`);
      return;
    }

    let parsed;
    try {
      parsed = new URL(link.url.trim());
    } catch {
      warnings.push(`Lien ignoré (URL invalide) : ${label} -> ${link.url}`);
      return;
    }

    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
      warnings.push(`Lien ignoré (protocole non supporté) : ${label} -> ${parsed.protocol}`);
      return;
    }

    if (!config.allowAnyHost && !isAllowedHost(parsed.hostname, allowedHosts)) {
      warnings.push(
        `Lien REFUSÉ (domaine hors allowedHosts) : ${label} -> ${parsed.hostname}. ` +
        `Ajoute "${parsed.hostname}" (ou un motif comme "*.${parsed.hostname.split('.').slice(-2).join('.')}") ` +
        `dans config.json > allowedHosts si cet hébergement est bien à toi.`
      );
      return;
    }

    candidates.push({ link, parsed });
  });

  const shouldFetch = config.fetchMetadata && !process.env.SKIP_LINK_FETCH;

  const metas = shouldFetch
    ? await inBatches(candidates, FETCH_CONCURRENCY, ({ parsed }) => fetchMeta(parsed.href))
    : candidates.map(() => ({ ok: false, error: 'récupération désactivée' }));

  return candidates.map(({ link, parsed }, index) => {
    const meta = metas[index] || { ok: false, error: 'inconnu' };
    const fileFromUrl = decodeURIComponent(parsed.pathname.split('/').filter(Boolean).pop() || '');

    // Priorité : ce que tu as écrit dans config.json > ce que le serveur annonce > l'URL.
    const name = link.name || meta.filename || fileFromUrl || parsed.hostname;

    let ext = path.extname(name) ? extOf(name) : '';
    if (!ext && fileFromUrl && path.extname(fileFromUrl)) ext = extOf(fileFromUrl);
    if (!ext && meta.contentType && MIME_EXT[meta.contentType]) ext = MIME_EXT[meta.contentType];
    if (!ext) ext = 'fichier';

    const size = Number.isFinite(link.size) ? link.size : Number.isFinite(meta.size) ? meta.size : null;
    const modified = link.modified || meta.modified || null;

    if (shouldFetch && !meta.ok) {
      warnings.push(`Infos non récupérées pour ${parsed.href} (${meta.error}) — le lien reste listé.`);
    }

    return {
      id: `external:${parsed.href}`,
      name,
      folder: link.folder || '',
      ext,
      size,
      sizeLabel: size === null ? '' : humanSize(size),
      modified,
      url: parsed.href,
      source: 'external',
      host: parsed.hostname,
      description: link.description || '',
      discoveredOn: link.discoveredOn || '',
      reachable: shouldFetch ? meta.ok : null
    };
  });
}

async function main() {
  const warnings = [];
  const config = readConfig();

  const localItems = scanDir(FILES_DIR);

  const shouldCrawl = config.sites.length > 0 && !process.env.SKIP_LINK_FETCH;
  if (shouldCrawl) console.log(`[manifest] exploration de ${config.sites.length} site(s)…`);
  const discovered = shouldCrawl ? await crawlSites(config, warnings) : [];

  const externalItems = await buildExternalLinks(config, warnings, discovered);

  // Dédoublonnage par URL (un même fichier listé deux fois n'apparaît qu'une fois)
  const seen = new Set();
  const items = [...localItems, ...externalItems].filter((item) => {
    if (seen.has(item.url)) return false;
    seen.add(item.url);
    return true;
  });

  const manifest = {
    siteName: config.siteName,
    tagline: config.tagline,
    generatedAt: new Date().toISOString(),
    totalCount: items.length,
    totalSize: items.reduce((sum, item) => sum + (item.size || 0), 0),
    allowedHosts: config.allowedHosts,
    items
  };

  fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

  console.log(`[manifest] ${items.length} entrée(s) -> public/files.json`);
  console.log(`[manifest]   ${localItems.length} fichier(s) local(aux) dans public/files/`);
  console.log(`[manifest]   ${externalItems.length} lien(s) externe(s) validé(s)`);
  console.log(`[manifest]   dont ${externalItems.filter((i) => i.discoveredOn).length} trouvé(s) en explorant tes sites`);
  console.log(`[manifest]   taille totale connue : ${humanSize(manifest.totalSize)}`);
  warnings.forEach((w) => console.warn(`[manifest] ⚠ ${w}`));
}

main().catch((err) => {
  console.error(`[manifest] ✖ ${err.message}`);
  process.exit(1);
});
