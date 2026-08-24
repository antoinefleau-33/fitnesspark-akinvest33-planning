// Proxy serverless : récupère l'URL cible côté serveur pour contourner CORS.
// Le navigateur appelle /api/fetch?url=... en same-origin (aucune restriction CORS).
export default async function handler(req, res) {
  const target = req.query.url;

  if (!target) {
    res.status(400).json({ error: 'Paramètre "url" manquant.' });
    return;
  }

  // N'autoriser que http(s) pour éviter les schémas dangereux (file:, etc.).
  let parsed;
  try {
    parsed = new URL(target);
  } catch {
    res.status(400).json({ error: 'URL invalide.' });
    return;
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    res.status(400).json({ error: 'Seuls http et https sont autorisés.' });
    return;
  }

  try {
    const upstream = await fetch(target, {
      // Un User-Agent standard évite certains blocages basiques.
      headers: { 'User-Agent': 'Mozilla/5.0 (compatible; ResourceAudit/1.0)' },
      redirect: 'follow',
    });

    const html = await upstream.text();

    // On renvoie le HTML brut ; le parsing DOM se fait côté client.
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.status(200).send(html);
  } catch (e) {
    res.status(502).json({ error: 'Échec de récupération : ' + e.message });
  }
}
