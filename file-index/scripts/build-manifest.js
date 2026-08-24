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

const FETCH_TIMEOUT_MS = 10000;
const FETCH_CONCURRENCY = 6;

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

/** Exécute des tâches par petits paquets pour ne pas marteler les serveurs. */
async function inBatches(items, size, worker) {
  const results = [];
  for (let i = 0; i < items.length; i += size) {
    const batch = items.slice(i, i + size);
    results.push(...(await Promise.all(batch.map(worker))));
  }
  return results;
}

/** Valide et normalise les liens externes de config.json. */
async function buildExternalLinks(config, warnings) {
  // Un lien peut être une simple chaîne (l'URL) ou un objet complet.
  const entries = config.links.map((link) => (typeof link === 'string' ? { url: link } : link || {}));

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

    if (!config.allowAnyHost && !isAllowedHost(parsed.hostname, config.allowedHosts)) {
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
      reachable: shouldFetch ? meta.ok : null
    };
  });
}

async function main() {
  const warnings = [];
  const config = readConfig();

  const localItems = scanDir(FILES_DIR);
  const externalItems = await buildExternalLinks(config, warnings);

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
  console.log(`[manifest]   taille totale connue : ${humanSize(manifest.totalSize)}`);
  warnings.forEach((w) => console.warn(`[manifest] ⚠ ${w}`));
}

main().catch((err) => {
  console.error(`[manifest] ✖ ${err.message}`);
  process.exit(1);
});
