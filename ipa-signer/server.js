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
const PORT = process.env.PORT || 3000;

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

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------
app.use(express.static(path.join(__dirname, 'public')));

app.get('/api/health', (req, res) => {
  execFile(ZSIGN_BIN, ['-v'], { timeout: 10000 }, (error, stdout, stderr) => {
    res.json({
      ok: !error,
      zsign: (stdout || stderr || '').trim() || (error ? 'introuvable' : 'ok'),
    });
  });
});

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

      res.download(outPath, outName, async () => {
        await cleanup(jobDir);
      });
    } catch (e) {
      await cleanup(jobDir);
      res.status(400).json({ ok: false, error: e.message || 'Erreur inconnue.' });
    }
  }
);

// Filet de sécurité : purge les jobs orphelins de plus d'une heure.
setInterval(async () => {
  try {
    const entries = await fsp.readdir(WORK_ROOT);
    const now = Date.now();
    for (const name of entries) {
      const p = path.join(WORK_ROOT, name);
      try {
        const st = await fsp.stat(p);
        if (now - st.mtimeMs > 60 * 60 * 1000) await cleanup(p);
      } catch (_) {}
    }
  } catch (_) {}
}, 15 * 60 * 1000).unref();

app.listen(PORT, () => {
  console.log(`IPA Signer à l'écoute sur le port ${PORT}`);
});
