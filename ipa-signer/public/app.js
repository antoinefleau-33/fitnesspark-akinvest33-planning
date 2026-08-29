'use strict';

const form = document.getElementById('sign-form');
const submitBtn = document.getElementById('submit-btn');
const statusBox = document.getElementById('status');

// ---- Formatage taille ----
function human(bytes) {
  if (bytes < 1024) return bytes + ' o';
  const units = ['Ko', 'Mo', 'Go'];
  let i = -1;
  do { bytes /= 1024; i++; } while (bytes >= 1024 && i < units.length - 1);
  return bytes.toFixed(1) + ' ' + units[i];
}

// ---- Zones de dépôt (drag & drop + clic) ----
document.querySelectorAll('.drop').forEach((zone) => {
  const inputId = zone.dataset.input;
  const input = document.getElementById(inputId);
  const inner = zone.querySelector('.drop-inner');
  const fileBox = zone.querySelector('.drop-file');
  const accept = (zone.dataset.accept || '').split(',').map((s) => s.trim().toLowerCase());

  function matchesAccept(name) {
    if (!accept.length) return true;
    const lower = name.toLowerCase();
    return accept.some((ext) => lower.endsWith(ext));
  }

  function render() {
    const file = input.files[0];
    if (file) {
      zone.classList.add('filled');
      inner.hidden = true;
      fileBox.hidden = false;
      fileBox.innerHTML = '';
      const check = document.createElement('span');
      check.className = 'check';
      check.textContent = '✓';
      const name = document.createElement('span');
      name.className = 'fname';
      name.textContent = file.name;
      const size = document.createElement('span');
      size.className = 'fsize';
      size.textContent = '· ' + human(file.size);
      const clear = document.createElement('button');
      clear.type = 'button';
      clear.className = 'clear';
      clear.textContent = '×';
      clear.title = 'Retirer';
      clear.addEventListener('click', (e) => {
        e.stopPropagation();
        input.value = '';
        render();
      });
      fileBox.append(check, name, size, clear);
    } else {
      zone.classList.remove('filled');
      inner.hidden = false;
      fileBox.hidden = true;
      fileBox.innerHTML = '';
    }
  }

  zone.addEventListener('click', () => input.click());
  input.addEventListener('change', render);

  ['dragenter', 'dragover'].forEach((ev) =>
    zone.addEventListener(ev, (e) => { e.preventDefault(); zone.classList.add('dragover'); })
  );
  ['dragleave', 'dragend'].forEach((ev) =>
    zone.addEventListener(ev, () => zone.classList.remove('dragover'))
  );
  zone.addEventListener('drop', (e) => {
    e.preventDefault();
    zone.classList.remove('dragover');
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (!file) return;
    if (!matchesAccept(file.name)) {
      setStatus('err', `Type de fichier inattendu pour ce champ (${accept.join(', ')}).`);
      return;
    }
    const dt = new DataTransfer();
    dt.items.add(file);
    input.files = dt.files;
    render();
  });
});

// ---- Afficher / masquer le mot de passe ----
document.getElementById('pw-toggle').addEventListener('click', () => {
  const pw = document.getElementById('password');
  pw.type = pw.type === 'password' ? 'text' : 'password';
});

// ---- Status helper ----
function setStatus(kind, message, log) {
  statusBox.hidden = false;
  statusBox.className = 'status ' + kind;
  statusBox.innerHTML = '';
  const p = document.createElement('div');
  p.textContent = message;
  statusBox.appendChild(p);
  if (log) {
    const pre = document.createElement('pre');
    pre.textContent = log;
    statusBox.appendChild(pre);
  }
}

// ---- Détection navigateur iOS non-Safari (l'OTA n'y marche pas) ----
function iosNonSafari() {
  const ua = navigator.userAgent;
  const isIOS = /iPhone|iPad|iPod/.test(ua) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const isSafari = /Safari/.test(ua) && !/(CriOS|FxiOS|EdgiOS|OPiOS|Chrome)/.test(ua);
  return isIOS && !isSafari;
}

// ---- Affiche le résultat de signature (installation OTA + téléchargement) ----
function showResult(data) {
  statusBox.hidden = false;
  statusBox.className = 'status ok result';
  statusBox.innerHTML = '';

  const title = document.createElement('div');
  title.className = 'result-title';
  title.textContent = '✅ ' + (data.appName ? data.appName + ' — signé !' : 'IPA signé avec succès !');
  statusBox.appendChild(title);

  if (data.bundleId || data.version) {
    const info = document.createElement('div');
    info.className = 'result-info';
    info.textContent =
      (data.bundleId ? data.bundleId : '') +
      (data.version ? '  ·  v' + data.version : '');
    statusBox.appendChild(info);
  }

  // Bouton principal : installation directe sur iPhone (OTA).
  const install = document.createElement('a');
  install.className = 'btn-install';
  install.href = data.installUrl;
  install.innerHTML = '📲 Installer sur cet iPhone';
  statusBox.appendChild(install);

  const help = document.createElement('p');
  help.className = 'result-hint';
  help.innerHTML =
    'Depuis l’<b>iPhone</b>, ouvre cette page dans <b>Safari</b> et touche le bouton ci-dessus : ' +
    'iOS proposera d’installer l’app. Accepte la fenêtre « Installer&nbsp;? ».';
  statusBox.appendChild(help);

  if (!data.https) {
    const warn = document.createElement('p');
    warn.className = 'result-warn';
    warn.innerHTML = '⚠️ L’installation OTA exige le <b>HTTPS</b>. En local (http) le bouton ne fonctionnera pas — déploie le site (Render) pour l’utiliser.';
    statusBox.appendChild(warn);
  } else if (iosNonSafari()) {
    const warn = document.createElement('p');
    warn.className = 'result-warn';
    warn.innerHTML = '⚠️ Tu n’es pas dans <b>Safari</b>. L’installation OTA ne marche que dans Safari — copie le lien et ouvre-le dans Safari.';
    statusBox.appendChild(warn);
  }

  // Actions secondaires : télécharger le fichier, copier le lien d'installation.
  const row = document.createElement('div');
  row.className = 'result-actions';

  const dl = document.createElement('a');
  dl.className = 'btn-secondary';
  dl.href = data.downloadUrl;
  dl.setAttribute('download', data.name || 'app-signed.ipa');
  dl.textContent = '⬇️ Télécharger l’IPA';
  row.appendChild(dl);

  const copy = document.createElement('button');
  copy.type = 'button';
  copy.className = 'btn-secondary';
  copy.textContent = '🔗 Copier le lien d’installation';
  copy.addEventListener('click', () => {
    const link = location.origin + data.downloadUrl;
    navigator.clipboard?.writeText(link).then(
      () => { copy.textContent = '✓ Lien copié'; setTimeout(() => (copy.textContent = '🔗 Copier le lien d’installation'), 2000); },
      () => { copy.textContent = link; }
    );
  });
  row.appendChild(copy);
  statusBox.appendChild(row);

  // Bloc : installation par câble sur Mac (garde la signature AppleP12 telle quelle).
  const dlAbs = location.origin + data.downloadUrl;
  const cable = document.createElement('details');
  cable.className = 'cable-install';
  cable.innerHTML =
    '<summary>💻 Installer par câble sur Mac (au lieu de l’OTA)</summary>' +
    '<div class="cable-body">' +
      '<p><b>Option simple — Apple Configurator (sans terminal) :</b></p>' +
      '<ol>' +
        '<li>Installe <b>Apple Configurator</b> (gratuit, Mac App Store) et ouvre-le.</li>' +
        '<li>Branche l’iPhone en USB, déverrouille-le, touche <b>« Se fier »</b>.</li>' +
        '<li>Télécharge l’IPA (bouton <b>⬇️</b> ci-dessus).</li>' +
        '<li>Glisse le fichier <code>.ipa</code> sur l’iPhone dans Apple Configurator → il s’installe.</li>' +
      '</ol>' +
      '<p><b>Option terminal — Homebrew :</b></p>' +
      '<pre>brew install ideviceinstaller\n' +
      'idevice_id -l                 # UDID de l’iPhone\n' +
      'curl -L -o app.ipa "' + esc(dlAbs) + '"\n' +
      'ideviceinstaller -i app.ipa</pre>' +
      '<p class="cable-hint">💡 Un script prêt à l’emploi est fourni : <code>tools/install-mac.sh "' + esc(dlAbs) + '"</code>. ' +
      'Par câble, si l’installation échoue, le message d’erreur est bien plus précis qu’en OTA ' +
      '(ex. <code>ApplicationVerificationFailed</code> = certificat révoqué, ou UDID absent du profil).</p>' +
    '</div>';
  statusBox.appendChild(cable);

  const expiry = document.createElement('p');
  expiry.className = 'result-expiry';
  expiry.textContent = '⏳ Lien valable environ ' + (data.expiresInMin || 30) + ' min, puis le fichier est supprimé du serveur.';
  statusBox.appendChild(expiry);

  statusBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// ---- Diagnostic certificat / profil ----
const diagBox = document.getElementById('diag');
const inspectBtn = document.getElementById('inspect-btn');

function esc(s) {
  const d = document.createElement('div');
  d.textContent = s == null ? '' : String(s);
  return d.innerHTML;
}

inspectBtn.addEventListener('click', () => {
  const p12 = document.getElementById('p12').files[0];
  const prov = document.getElementById('mobileprovision').files[0];
  if (!prov) {
    diagBox.hidden = false;
    diagBox.className = 'status err';
    diagBox.textContent = 'Ajoute au moins ton profil .mobileprovision pour le diagnostic.';
    return;
  }
  const fd = new FormData();
  fd.append('mobileprovision', prov);
  if (p12) fd.append('p12', p12);
  fd.append('password', document.getElementById('password').value || '');

  inspectBtn.disabled = true;
  inspectBtn.textContent = '🔍 Analyse…';
  diagBox.hidden = false;
  diagBox.className = 'status info';
  diagBox.textContent = 'Analyse du profil…';

  fetch('/api/inspect', { method: 'POST', body: fd })
    .then((r) => r.json())
    .then((data) => {
      inspectBtn.disabled = false;
      inspectBtn.textContent = '🔍 Vérifier mon certificat / profil';
      if (!data.ok) {
        diagBox.className = 'status err';
        diagBox.textContent = '❌ ' + (data.error || 'Diagnostic impossible.');
        return;
      }
      renderDiag(data);
    })
    .catch(() => {
      inspectBtn.disabled = false;
      inspectBtn.textContent = '🔍 Vérifier mon certificat / profil';
      diagBox.className = 'status err';
      diagBox.textContent = 'Erreur réseau pendant le diagnostic.';
    });
});

function renderDiag(data) {
  const p = data.profile;
  const rows = [];
  rows.push(['Profil', p.name || '—']);
  rows.push(['App', p.appIdName || '—']);
  rows.push(['Bundle ID', p.appId || '—']);
  rows.push(['Équipe', p.teamName || '—']);
  const typeLabel = { entreprise: 'Entreprise', 'ad-hoc': 'Ad-hoc', 'développement': 'Développement' }[p.type] || p.type;
  rows.push(['Type', typeLabel]);
  if (p.expiration) {
    const d = new Date(p.expiration);
    rows.push(['Expiration', d.toLocaleDateString('fr-FR') + (p.expired ? ' — ⚠️ EXPIRÉ' : '')]);
  }
  if (p.deviceCount === null) rows.push(['Appareils', 'Tous (profil entreprise)']);
  else rows.push(['Appareils autorisés', String(p.deviceCount)]);

  let html = '<div class="result-title">🔍 Diagnostic</div>';
  html += '<table class="diag-table">';
  for (const [k, v] of rows) html += `<tr><td>${esc(k)}</td><td>${esc(v)}</td></tr>`;
  html += '</table>';

  // Interprétation orientée « pourquoi l'install échoue ».
  const notes = [];
  if (p.expired) {
    notes.push(['err', '⛔ Le profil est <b>expiré</b> → re-signer est impossible tant que tu n\'as pas un profil valide.']);
  }
  if (p.type === 'entreprise') {
    notes.push(['ok', '✅ Profil <b>entreprise</b> : installable sur n\'importe quel iPhone. Si l\'install échoue quand même (« intégrité non vérifiée »), c\'est presque toujours que le <b>certificat est révoqué</b> par Apple → il faut en racheter un.']);
  } else {
    notes.push(['warn', `⚠️ Profil <b>${esc(typeLabel).toLowerCase()}</b> : il ne marche QUE sur les <b>${p.deviceCount} appareil(s)</b> dont l\'UDID est enregistré dedans. Si l\'<b>UDID de cet iPhone n\'est pas dans la liste</b>, tu obtiens exactement l\'erreur « intégrité non vérifiée ».`]);
    if (p.devices && p.devices.length) {
      notes.push(['info', 'UDID autorisés dans ce profil :<br><code class="udids">' + p.devices.map(esc).join('<br>') + '</code>Compare avec l\'UDID de ton iPhone (voir ci-dessous).']);
    }
    notes.push(['info', '📱 <b>Trouver l\'UDID de cet iPhone</b> : Réglages → Général → Informations → touche « Identifiant » pour le copier, ou branche-le et regarde dans les Réglages de l\'appareil. Il doit figurer dans la liste au-dessus. Sinon, redemande au vendeur (AppleP12) un profil incluant cet UDID.']);
  }
  if (data.cert) {
    const map = { 'valide': 'ok', 'révoqué': 'err', 'expiré': 'err', 'mot de passe P12 incorrect': 'err', 'inconnu': 'info' };
    notes.push([map[data.cert.status] || 'info', 'Certificat P12 : <b>' + esc(data.cert.status) + '</b>' + (data.cert.status === 'révoqué' ? ' → il faut en racheter un.' : '')]);
  } else {
    notes.push(['info', '💡 Ajoute aussi ton <b>.p12</b> et son mot de passe puis relance le diagnostic pour vérifier si le certificat est <b>révoqué</b>.']);
  }

  for (const [kind, text] of notes) html += `<div class="diag-note ${kind}">${text}</div>`;

  diagBox.className = 'status result';
  diagBox.innerHTML = html;
  diagBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// ---- Soumission ----
form.addEventListener('submit', (e) => {
  e.preventDefault();

  const ipa = document.getElementById('ipa').files[0];
  const p12 = document.getElementById('p12').files[0];
  const prov = document.getElementById('mobileprovision').files[0];

  if (!ipa) return setStatus('err', 'Ajoutez d’abord un fichier .ipa / .tipa.');
  if (!p12) return setStatus('err', 'Ajoutez votre certificat .p12.');
  if (!prov) return setStatus('err', 'Ajoutez votre profil .mobileprovision.');

  const fd = new FormData(form);

  submitBtn.disabled = true;
  submitBtn.classList.add('loading');
  setStatus('info', 'Signature en cours… cela peut prendre un moment selon la taille de l’app.');

  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/sign');
  xhr.responseType = 'json';

  xhr.upload.addEventListener('progress', (ev) => {
    if (ev.lengthComputable) {
      const pct = Math.round((ev.loaded / ev.total) * 100);
      if (pct < 100) setStatus('info', `Envoi des fichiers… ${pct}%`);
      else setStatus('info', 'Signature côté serveur en cours…');
    }
  });

  xhr.onload = () => {
    submitBtn.disabled = false;
    submitBtn.classList.remove('loading');

    let data = xhr.response;
    if (typeof data === 'string') { try { data = JSON.parse(data); } catch (_) { data = null; } }
    if (!data) data = {};

    if (xhr.status === 200 && data.ok) {
      showResult(data);
    } else {
      setStatus('err', '❌ ' + (data.error || `Échec (code ${xhr.status}).`), data.log);
    }
  };

  xhr.onerror = () => {
    submitBtn.disabled = false;
    submitBtn.classList.remove('loading');
    setStatus('err', 'Erreur réseau pendant l’envoi. Réessayez.');
  };

  xhr.send(fd);
});
