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
  xhr.responseType = 'blob';

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

    const ct = xhr.getResponseHeader('Content-Type') || '';
    if (xhr.status === 200 && ct.indexOf('application/json') === -1) {
      // Succès : on a reçu l'IPA signé.
      const blob = xhr.response;
      const url = URL.createObjectURL(blob);
      const base = ipa.name.replace(/\.(ipa|tipa)$/i, '');
      const a = document.createElement('a');
      a.href = url;
      a.download = base + '-signed.ipa';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
      setStatus('ok', '✅ IPA signé avec succès — le téléchargement a démarré. Vous pouvez maintenant l’installer.');
      return;
    }

    // Erreur : le corps est du JSON, même en responseType blob.
    const reader = new FileReader();
    reader.onload = () => {
      let data = {};
      try { data = JSON.parse(reader.result); } catch (_) {}
      setStatus('err', '❌ ' + (data.error || `Échec (code ${xhr.status}).`), data.log);
    };
    reader.readAsText(xhr.response);
  };

  xhr.onerror = () => {
    submitBtn.disabled = false;
    submitBtn.classList.remove('loading');
    setStatus('err', 'Erreur réseau pendant l’envoi. Réessayez.');
  };

  xhr.send(fd);
});
