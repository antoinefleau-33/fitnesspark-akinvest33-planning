'use strict';

/**
 * IPA / TIPA Signer — backend
 *
 * Reçoit un IPA/TIPA + un certificat P12 (+ mot de passe) + un provisioning,
 * lance zsign côté serveur, puis renvoie l'IPA signé.
 *
 * Aucune donnée n'est conservée : chaque job travaille dans un dossier temporaire
 * qui est supprimé dès la réponse envoyée. Les mots de passe ne sont jamais loggés.
 */

const express = require('express');
const multer = require('multer');
const { execFile } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const fsp = require('fs/promises');
const os = require('os');
const path = require('path');

const app = express();
// Render/Fly/HF terminent le TLS en amont : on fait confiance au proxy pour
// que req.protocol reflète bien "https" (indispensable pour le lien OTA).
app.set('trust proxy', true);
const PORT = process.env.PORT || 3000;

// Durée de conservation d'un IPA signé pour permettre l'installation OTA (minutes).
const RETENTION_MIN = parseInt(process.env.RETENTION_MIN || '30', 10);

// Emplacement du binaire zsign (surchargé dans l'image Docker si besoin).
const ZSIGN_BIN = process.env.ZSIGN_BIN || 'zsign';

// Taille max par upload (IPA). 2 Go par défaut ; ajustable via MAX_UPLOAD_MB.
const MAX_UPLOAD_MB = parseInt(process.env.MAX_UPLOAD_MB || '2048', 10);

// Racine des jobs temporaires.
const WORK_ROOT = process.env.WORK_ROOT || path.join(os.tmpdir(), 'ipa-signer');
fs.mkdirSync(WORK_ROOT, { recursive: true });

// ---------------------------------------------------------------------------
// Upload : stockage sur disque (les IPA peuvent peser plusieurs centaines de Mo,
// on évite donc la mémoire). Un dossier isolé par requête.
// ---------------------------------------------------------------------------
const storage = multer.diskStorage({
  destination(req, file, cb) {
    if (!req.jobDir) {
      req.jobId = crypto.randomBytes(12).toString('hex');
      req.jobDir = path.join(WORK_ROOT, req.jobId);
      try {
        fs.mkdirSync(req.jobDir, { recursive: true });
      } catch (err) {
        return cb(err);
      }
    }
    cb(null, req.jobDir);
  },
  filename(req, file, cb) {
    // Nom neutre par champ pour éviter tout souci de caractères / traversal.
    const safe = {
      ipa: 'input',            // extension ajoutée plus bas
      p12: 'cert.p12',
      mobileprovision: 'profile.mobileprovision',
    }[file.fieldname] || crypto.randomBytes(6).toString('hex');

    if (file.fieldname === 'ipa') {
      const ext = path.extname(file.originalname).toLowerCase() === '.tipa' ? '.tipa' : '.ipa';
      return cb(null, safe + ext);
    }
    cb(null, safe);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: MAX_UPLOAD_MB * 1024 * 1024 },
});

// ---------------------------------------------------------------------------
// Utilitaires
// ---------------------------------------------------------------------------
async function cleanup(dir) {
  if (!dir) return;
  try {
    await fsp.rm(dir, { recursive: true, force: true });
  } catch (_) {
    /* best effort */
  }
}

function runZsign(args, cwd) {
  return new Promise((resolve) => {
    execFile(
      ZSIGN_BIN,
      args,
      { cwd, maxBuffer: 16 * 1024 * 1024, timeout: 10 * 60 * 1000 },
      (error, stdout, stderr) => {
        resolve({ error, stdout: stdout || '', stderr: stderr || '' });
      }
    );
  });
}

// Nettoie le journal zsign : retire les codes couleur ANSI, masque tout secret,
// et ne garde que les dernières lignes utiles.
function sanitizeLog(text) {
  return String(text)
    // eslint-disable-next-line no-control-regex
    .replace(/\x1b\[[0-9;]*m/g, '')     // codes couleur ANSI
    .replace(/(-p\s+)\S+/g, '$1******') // masque un éventuel mot de passe en clair
    .replace(/^>>>\s*/gm, '')            // préfixe cosmétique de zsign
    .split('\n')
    .map((l) => l.trimEnd())
    .filter((l) => l.trim().length)
    .slice(-40)
    .join('\n');
}

// Extrait AppName / BundleId / Version depuis la sortie de zsign.
// zsign imprime toujours (cf. src/bundle.cpp) :
//   >>> AppName: 	<nom>
//   >>> BundleId: 	<id>
//   >>> Version: 	<version>
function parseAppMeta(text) {
  const clean = String(text).replace(/\x1b\[[0-9;]*m/g, ''); // eslint-disable-line no-control-regex
  const grab = (label) => {
    const m = clean.match(new RegExp('>>>\\s*' + label + ':\\s*([^\\n]+)', 'i'));
    return m ? m[1].trim() : '';
  };
  // Pour BundleId, zsign peut imprimer "ancien -> nouveau" ; on garde le dernier.
  let bundleId = grab('BundleId');
  if (bundleId.includes('->')) bundleId = bundleId.split('->').pop().split(',')[0].trim();
  return {
    appName: grab('AppName'),
    bundleId,
    version: grab('Version') || '1.0',
  };
}

function xmlEscape(s) {
  return String(s).replace(/[<>&'"]/g, (c) =>
    ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' }[c])
  );
}

// Manifeste OTA (plist) attendu par iOS pour une installation itms-services://.
function buildManifest({ ipaUrl, bundleId, version, title }) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>items</key>
  <array>
    <dict>
      <key>assets</key>
      <array>
        <dict>
          <key>kind</key><string>software-package</string>
          <key>url</key><string>${xmlEscape(ipaUrl)}</string>
        </dict>
      </array>
      <key>metadata</key>
      <dict>
        <key>bundle-identifier</key><string>${xmlEscape(bundleId || 'com.app.signed')}</string>
        <key>bundle-version</key><string>${xmlEscape(version || '1.0')}</string>
        <key>kind</key><string>software</string>
        <key>title</key><string>${xmlEscape(title || 'App')}</string>
      </dict>
    </dict>
  </array>
</dict>
</plist>
`;
}

// Base absolue https du service (Render fournit https via X-Forwarded-Proto).
function publicBase(req) {
  const proto = (req.protocol === 'https' || req.get('x-forwarded-proto') === 'https')
    ? 'https' : req.protocol;
  return `${proto}://${req.get('host')}`;
}

const HEX_ID = /^[a-f0-9]{8,32}$/;

// Analyse un fichier .mobileprovision (CMS DER contenant un plist XML en clair).
function parseProvision(buf) {
  const text = buf.toString('latin1');
  const start = text.indexOf('<?xml');
  const end = text.indexOf('</plist>');
  if (start === -1 || end === -1) return null;
  const xml = text.slice(start, end + '</plist>'.length);

  const str = (key) => {
    const m = xml.match(new RegExp('<key>' + key + '</key>\\s*<string>([^<]*)</string>'));
    return m ? m[1].trim() : '';
  };
  const datev = (key) => {
    const m = xml.match(new RegExp('<key>' + key + '</key>\\s*<date>([^<]*)</date>'));
    return m ? m[1].trim() : '';
  };
  const isTrue = (key) =>
    new RegExp('<key>' + key + '</key>\\s*<true\\s*/>').test(xml);

  let devices = [];
  const dm = xml.match(/<key>ProvisionedDevices<\/key>\s*<array>([\s\S]*?)<\/array>/);
  if (dm) {
    devices = (dm[1].match(/<string>([^<]*)<\/string>/g) || [])
      .map((s) => s.replace(/<\/?string>/g, '').trim());
  }

  return {
    name: str('Name'),
    appIdName: str('AppIDName'),
    teamName: str('TeamName'),
    appId: str('application-identifier'),
    creation: datev('CreationDate'),
    expiration: datev('ExpirationDate'),
    provisionsAllDevices: isTrue('ProvisionsAllDevices'),
    getTaskAllow: isTrue('get-task-allow'),
    devices,
    certCount: (xml.match(/<data>/g) || []).length,
  };
}

// Vérifie un p12 avec zsign (-C : validité + révocation OCSP). Best-effort.
function checkCert(p12Path, password) {
  return new Promise((resolve) => {
    const args = ['-C', '-k', p12Path];
    if (password) args.push('-p', password);
    execFile(ZSIGN_BIN, args, { timeout: 30000 }, (error, stdout, stderr) => {
      const out = sanitizeLog((stdout || '') + '\n' + (stderr || ''));
      let status = 'inconnu';
      if (/revoked|révoqué/i.test(out)) status = 'révoqué';
      else if (/expired|expiré/i.test(out)) status = 'expiré';
      else if (/valid|good|ok/i.test(out) && !error) status = 'valide';
      else if (/password|mac verify|decrypt/i.test(out)) status = 'mot de passe P12 incorrect';
      resolve({ status, log: out });
    });
  });
}

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------
app.use(express.static(path.join(__dirname, 'public')));

// Sert l'IPA signé conservé temporairement (pour download direct + OTA).
app.get('/f/:id/app.ipa', (req, res) => {
  const id = req.params.id;
  if (!HEX_ID.test(id)) return res.status(400).send('id invalide');
  const dir = path.join(WORK_ROOT, id);
  const ipa = path.join(dir, 'signed.ipa');
  if (!fs.existsSync(ipa)) return res.status(404).send('Lien expiré ou introuvable.');
  let name = 'app-signed.ipa';
  try { name = JSON.parse(fs.readFileSync(path.join(dir, 'meta.json'))).name || name; } catch (_) {}
  res.setHeader('Content-Type', 'application/octet-stream');
  res.download(ipa, name);
});

// Sert le manifeste OTA (plist) pour l'installation itms-services://.
app.get('/f/:id/manifest.plist', (req, res) => {
  const id = req.params.id;
  if (!HEX_ID.test(id)) return res.status(400).send('id invalide');
  const dir = path.join(WORK_ROOT, id);
  let meta;
  try { meta = JSON.parse(fs.readFileSync(path.join(dir, 'meta.json'))); } catch (_) {
    return res.status(404).send('Lien expiré ou introuvable.');
  }
  if (!fs.existsSync(path.join(dir, 'signed.ipa'))) {
    return res.status(404).send('Lien expiré ou introuvable.');
  }
  const xml = buildManifest({
    ipaUrl: `${publicBase(req)}/f/${id}/app.ipa`,
    bundleId: meta.bundleId,
    version: meta.version,
    title: meta.appName || meta.name,
  });
  res.setHeader('Content-Type', 'application/xml; charset=utf-8');
  res.send(xml);
});

app.get('/api/health', (req, res) => {
  execFile(ZSIGN_BIN, ['-v'], { timeout: 10000 }, (error, stdout, stderr) => {
    res.json({
      ok: !error,
      zsign: (stdout || stderr || '').trim() || (error ? 'introuvable' : 'ok'),
    });
  });
});

// Diagnostic : analyse un profil de provisioning (+ éventuellement un p12).
app.post(
  '/api/inspect',
  (req, res, next) => {
    upload.fields([
      { name: 'p12', maxCount: 1 },
      { name: 'mobileprovision', maxCount: 1 },
    ])(req, res, (err) => {
      if (err) {
        cleanup(req.jobDir);
        return res.status(400).json({ ok: false, error: `Erreur d'upload : ${err.message}` });
      }
      next();
    });
  },
  async (req, res) => {
    const jobDir = req.jobDir;
    const files = req.files || {};
    const body = req.body || {};
    try {
      const provFile = files.mobileprovision && files.mobileprovision[0];
      const p12File = files.p12 && files.p12[0];
      if (!provFile) throw new Error('Ajoutez au moins le profil .mobileprovision.');

      const prov = parseProvision(await fsp.readFile(provFile.path));
      if (!prov) throw new Error('Profil illisible (fichier .mobileprovision invalide ?).');

      const now = Date.now();
      const expMs = prov.expiration ? Date.parse(prov.expiration) : NaN;
      const expired = !isNaN(expMs) && expMs < now;
      const type = prov.provisionsAllDevices
        ? 'entreprise'
        : (prov.getTaskAllow ? 'développement' : 'ad-hoc');

      let cert = null;
      if (p12File) {
        cert = await checkCert(p12File.path, (body.password || '').toString());
      }

      await cleanup(jobDir);
      res.json({
        ok: true,
        profile: {
          name: prov.name,
          appIdName: prov.appIdName,
          teamName: prov.teamName,
          appId: prov.appId,
          type,
          expiration: prov.expiration,
          expired,
          deviceCount: prov.provisionsAllDevices ? null : prov.devices.length,
          devices: prov.devices,
        },
        cert,
      });
    } catch (e) {
      await cleanup(jobDir);
      res.status(400).json({ ok: false, error: e.message || 'Erreur inconnue.' });
    }
  }
);

app.post(
  '/api/sign',
  (req, res, next) => {
    upload.fields([
      { name: 'ipa', maxCount: 1 },
      { name: 'p12', maxCount: 1 },
      { name: 'mobileprovision', maxCount: 1 },
    ])(req, res, (err) => {
      if (err) {
        cleanup(req.jobDir);
        const msg =
          err.code === 'LIMIT_FILE_SIZE'
            ? `Fichier trop volumineux (limite ${MAX_UPLOAD_MB} Mo).`
            : `Erreur d'upload : ${err.message}`;
        return res.status(400).json({ ok: false, error: msg });
      }
      next();
    });
  },
  async (req, res) => {
    const jobDir = req.jobDir;
    const files = req.files || {};
    const body = req.body || {};
    const password = (body.password || '').toString();
    const bundleId = (body.bundleId || '').toString().trim();
    const bundleName = (body.bundleName || '').toString().trim();

    try {
      const ipaFile = files.ipa && files.ipa[0];
      const p12File = files.p12 && files.p12[0];
      const provFile = files.mobileprovision && files.mobileprovision[0];

      if (!ipaFile) throw new Error('Fichier IPA/TIPA manquant.');
      if (!p12File) throw new Error('Certificat .p12 manquant.');
      if (!provFile) throw new Error('Profil .mobileprovision manquant.');

      const outName =
        path.basename(ipaFile.originalname, path.extname(ipaFile.originalname)) +
        '-signed.ipa';
      const outPath = path.join(jobDir, 'signed.ipa');

      const args = [
        '-k', p12File.path,
        '-p', password,
        '-m', provFile.path,
        '-o', outPath,
      ];
      if (bundleId) args.push('-b', bundleId);
      if (bundleName) args.push('-n', bundleName);
      args.push(ipaFile.path);

      const { error, stdout, stderr } = await runZsign(args, jobDir);

      const produced = fs.existsSync(outPath) && fs.statSync(outPath).size > 0;
      if (error || !produced) {
        const log = sanitizeLog(stdout + '\n' + stderr);
        let hint = "Échec de la signature. Consultez le détail ci-dessous.";
        if (/provision/i.test(log)) {
          hint = "Profil de provisioning invalide, manquant ou ne correspondant pas au certificat.";
        } else if (/password|mac verify|decrypt|pkcs12|p12|private key|read cert/i.test(log)) {
          hint = 'Mot de passe du P12 incorrect, ou certificat illisible.';
        } else if (/mach-?o|not a valid|unsupported|parse/i.test(log)) {
          hint = "Le fichier fourni ne semble pas être une app iOS (.ipa/.tipa) valide.";
        }
        await cleanup(jobDir);
        return res.status(422).json({ ok: false, error: hint, log });
      }

      // Signature OK : on extrait les métadonnées et on conserve l'IPA quelques
      // minutes pour permettre l'installation directe sur iPhone (OTA).
      const meta = parseAppMeta(stdout);
      const record = {
        name: outName,
        appName: meta.appName,
        bundleId: meta.bundleId,
        version: meta.version,
        createdAt: Date.now(),
      };
      try {
        await fsp.writeFile(path.join(jobDir, 'meta.json'), JSON.stringify(record));
      } catch (_) {}

      const base = publicBase(req);
      const id = req.jobId;
      const manifestUrl = `${base}/f/${id}/manifest.plist`;
      res.json({
        ok: true,
        id,
        name: outName,
        appName: meta.appName,
        bundleId: meta.bundleId,
        version: meta.version,
        downloadUrl: `/f/${id}/app.ipa`,
        manifestUrl,
        // Lien qu'iOS/Safari comprend pour installer sans ordinateur.
        installUrl: `itms-services://?action=download-manifest&url=${encodeURIComponent(manifestUrl)}`,
        expiresInMin: RETENTION_MIN,
        https: base.startsWith('https'),
      });
      // NB : le dossier n'est PAS supprimé ici ; il expire via le balayage périodique.
    } catch (e) {
      await cleanup(jobDir);
      res.status(400).json({ ok: false, error: e.message || 'Erreur inconnue.' });
    }
  }
);

// Balayage périodique : purge les IPA signés au-delà de la durée de rétention.
setInterval(async () => {
  try {
    const entries = await fsp.readdir(WORK_ROOT);
    const now = Date.now();
    for (const name of entries) {
      const p = path.join(WORK_ROOT, name);
      try {
        const st = await fsp.stat(p);
        if (now - st.mtimeMs > RETENTION_MIN * 60 * 1000) await cleanup(p);
      } catch (_) {}
    }
  } catch (_) {}
}, 5 * 60 * 1000).unref();

app.listen(PORT, () => {
  console.log(`IPA Signer à l'écoute sur le port ${PORT}`);
});
