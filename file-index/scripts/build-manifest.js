#!/usr/bin/env node
/**
 * Scanne public/files/ et fusionne avec les liens externes de config.json
 * pour produire public/files.json (le manifeste lu par le site).
 *
 * Usage : node scripts/build-manifest.js
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const FILES_DIR = path.join(ROOT, 'public', 'files');
const CONFIG_PATH = path.join(ROOT, 'config.json');
const OUTPUT_PATH = path.join(ROOT, 'public', 'files.json');

// Fichiers techniques à ne jamais lister
const IGNORED = new Set(['.gitkeep', '.DS_Store', 'Thumbs.db', 'desktop.ini', '.gitignore']);

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

/** Un host est autorisé s'il est dans la liste, ou s'il en est un sous-domaine. */
function isAllowedHost(host, allowedHosts) {
  const h = host.toLowerCase();
  return allowedHosts.some((allowed) => {
    const a = String(allowed).toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
    return h === a || h.endsWith(`.${a}`);
  });
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

/** Valide et normalise les liens externes de config.json. */
function buildExternalLinks(config, warnings) {
  const out = [];

  config.links.forEach((link, index) => {
    const label = link && link.name ? link.name : `links[${index}]`;

    if (!link || typeof link.url !== 'string' || !link.url.trim()) {
      warnings.push(`Lien ignoré (url manquante) : ${label}`);
      return;
    }

    let parsed;
    try {
      parsed = new URL(link.url);
    } catch {
      warnings.push(`Lien ignoré (URL invalide) : ${label} -> ${link.url}`);
      return;
    }

    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
      warnings.push(`Lien ignoré (protocole non supporté) : ${label} -> ${parsed.protocol}`);
      return;
    }

    if (!isAllowedHost(parsed.hostname, config.allowedHosts)) {
      warnings.push(
        `Lien REFUSÉ (domaine hors allowedHosts) : ${label} -> ${parsed.hostname}. ` +
        `Ajoute "${parsed.hostname}" dans config.json > allowedHosts si ce site est bien à toi.`
      );
      return;
    }

    const fileFromUrl = decodeURIComponent(parsed.pathname.split('/').filter(Boolean).pop() || '');
    const name = link.name || fileFromUrl || parsed.hostname;
    // Le type vient du nom s'il porte une extension, sinon de l'URL (ex. /setup.exe)
    const ext = path.extname(name) ? extOf(name) : extOf(fileFromUrl);
    const size = Number.isFinite(link.size) ? link.size : null;

    out.push({
      id: `external:${parsed.href}`,
      name,
      folder: link.folder || '',
      ext,
      size: size === null ? null : size,
      sizeLabel: size === null ? '' : humanSize(size),
      modified: link.modified || null,
      url: parsed.href,
      source: 'external',
      host: parsed.hostname,
      description: link.description || ''
    });
  });

  return out;
}

function main() {
  const warnings = [];
  const config = readConfig();

  const localItems = scanDir(FILES_DIR);
  const externalItems = buildExternalLinks(config, warnings);

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

try {
  main();
} catch (err) {
  console.error(`[manifest] ✖ ${err.message}`);
  process.exit(1);
}
