# Décisions prises en autonomie — nuit du 2026-08-13

## D1 — Branche de travail
- Contexte : le skill nuit-autonome recommande `nuit/AAAA-MM-JJ`, mais la session impose la branche `claude/arcane-legends-roblox-1nyxnf`.
- Choix : branche désignée `claude/arcane-legends-roblox-1nyxnf`. Réversible (simple branche).

## D2 — Emplacement du projet
- Contexte : le repo contient un `index.html` sans rapport avec le jeu.
- Choix : tout le jeu dans un dossier `arcane-legends/` à la racine, `index.html` intact. Réversible.

## D3 — `.session-nuit/` committé
- Contexte : conteneur éphémère ; tout ce qui n'est pas pushé est perdu. Le prompt exige journal des décisions + rapport de session en livrables.
- Choix : committer `.session-nuit/` (pas de .gitignore dessus). Réversible.

(les décisions de game design et techniques sont ajoutées au fil de la nuit ci-dessous)
