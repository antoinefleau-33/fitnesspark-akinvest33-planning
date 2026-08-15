#!/usr/bin/env python3
"""
Résout une liste de mods contre l'API Modrinth et, en option, les télécharge.

Pourquoi un script plutôt qu'une liste écrite en dur : les numéros de version des mods changent
toutes les semaines, et une liste figée est fausse dans le mois. Ici on demande à Modrinth ce qui
existe réellement pour la version de Minecraft visée, au moment où on lance la commande.

Usage :
    python3 resolve_mods.py --mc 26.2                 # juste vérifier
    python3 resolve_mods.py --mc 26.2 --download mods/  # vérifier et télécharger
    python3 resolve_mods.py --mc 26.2 --allow-alpha    # accepter alpha/beta
"""

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
UA = "poc-modlist/1.0 (proof-of-concept, contact via github)"

# slug Modrinth -> (catégorie, à quoi ça sert concrètement)
MODS = {
    # --- Socle obligatoire ---
    "fabric-api":        ("socle", "Dépendance de presque tous les mods Fabric"),

    # --- Les gros gains ---
    "sodium":            ("rendu", "Réécrit le moteur de rendu. Le plus gros gain de FPS, de loin"),
    "lithium":           ("logique", "Optimise la logique du jeu (mobs, blocs, redstone)"),
    "ferrite-core":      ("mémoire", "Réduit fortement la RAM utilisée"),
    "modernfix":         ("général", "Démarrage plus rapide + correctifs de performance variés"),

    # --- Gains sensibles ---
    "immediatelyfast":   ("rendu", "Accélère le HUD, le texte et les entités"),
    "entityculling":     ("rendu", "Ne dessine pas les mobs cachés derrière les murs"),
    "moreculling":       ("rendu", "Étend le même principe aux blocs"),
    "ebe":               ("rendu", "Coffres/panneaux en blocs normaux — ABANDONNÉ, bloqué en 1.21.4"),
    "sodium-extra":      ("rendu", "Options en plus : limiter particules, brouillard, etc."),
    "c2me-fabric":       ("monde", "Chargement des chunks en parallèle, moins de lag à l'explo"),
    "krypton":           ("réseau", "Optimise le réseau, utile en multijoueur"),
    "dynamic-fps":       ("système", "Baisse les FPS quand la fenêtre n'est pas au premier plan"),
    "lmd":               ("logique", "Fait disparaître les mobs inutiles plus vite"),
    "language-reload":   ("système", "Démarrage et changement de langue nettement plus rapides"),
    "rrls":              ("système", "Supprime l'écran de rechargement des ressources"),

    # --- Gains plus ciblés ---
    "memoryleakfix":     ("mémoire", "Corrige des fuites mémoire de vanilla"),
    "threadtweak":       ("système", "Répartit mieux les threads au démarrage"),
    "badoptimizations":  ("général", "Petites optimisations diverses"),
    "cull-less-leaves":  ("rendu", "Allège le rendu du feuillage"),
    "fastquit":          ("système", "Sortie de monde instantanée"),
    "exordium":          ("rendu", "Rafraîchit le HUD moins souvent que le monde"),
    "nvidium":           ("rendu", "Cartes NVIDIA uniquement : gros gain, demande Sodium"),

    # --- Confort / compatibilité ---
    "viafabricplus":     ("compat", "Rejoindre des serveurs d'autres versions depuis ce client"),
    "iris":              ("rendu", "Shaders. Coûte des FPS, mais beaucoup le veulent"),
    "indium":            ("compat", "Requis si un mod utilise l'API de rendu Fabric avec Sodium"),
    "debugify":          ("général", "Corrige des bugs vanilla non corrigés par Mojang"),
}

STABILITY_RANK = {"release": 0, "beta": 1, "alpha": 2}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def resolve(slug, mc_version, loader, allow_prerelease):
    """Renvoie la meilleure version compatible, ou None."""
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([mc_version]),
        "loaders": json.dumps([loader]),
    })
    try:
        versions = fetch(f"{API}/project/{slug}/version?{query}")
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}"}
    except Exception as e:  # réseau coupé, DNS, etc.
        return {"error": str(e)}

    if not versions:
        return None

    candidates = versions
    if not allow_prerelease:
        stable = [v for v in versions if v.get("version_type") == "release"]
        if stable:
            candidates = stable

    # Modrinth renvoie déjà du plus récent au plus ancien ; on privilégie d'abord la stabilité.
    candidates.sort(key=lambda v: STABILITY_RANK.get(v.get("version_type"), 3))
    best = candidates[0]
    primary = next((f for f in best["files"] if f.get("primary")), best["files"][0])
    return {
        "name": best["version_number"],
        "type": best["version_type"],
        "url": primary["url"],
        "filename": primary["filename"],
        "size_mb": primary["size"] / 1024 / 1024,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", required=True, help="version de Minecraft, ex. 26.2")
    ap.add_argument("--loader", default="fabric")
    ap.add_argument("--download", metavar="DIR", help="télécharger dans ce dossier")
    ap.add_argument("--allow-alpha", action="store_true",
                    help="accepter alpha/beta quand aucune version stable n'existe")
    args = ap.parse_args()

    print(f"Minecraft {args.mc} — chargeur {args.loader}\n")
    ok, missing, failed = [], [], []

    for slug, (category, description) in MODS.items():
        result = resolve(slug, args.mc, args.loader, args.allow_alpha)
        if result is None:
            missing.append((slug, category, description))
            print(f"  [ABSENT ] {slug:26s} pas encore compatible {args.mc}")
        elif "error" in result:
            failed.append((slug, result["error"]))
            print(f"  [ERREUR ] {slug:26s} {result['error']}")
        else:
            tag = "OK     " if result["type"] == "release" else result["type"].upper().ljust(7)
            ok.append((slug, category, description, result))
            print(f"  [{tag}] {slug:26s} {result['name']}")

    print(f"\n{len(ok)} disponibles, {len(missing)} absents, {len(failed)} en erreur")

    if args.download and ok:
        os.makedirs(args.download, exist_ok=True)
        print(f"\nTéléchargement vers {args.download}/")
        for slug, _, _, result in ok:
            target = os.path.join(args.download, result["filename"])
            if os.path.exists(target):
                print(f"  déjà présent : {result['filename']}")
                continue
            req = urllib.request.Request(result["url"], headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r, open(target, "wb") as f:
                f.write(r.read())
            print(f"  {result['filename']} ({result['size_mb']:.1f} Mo)")

    if missing:
        print("\nÀ surveiller (sortiront plus tard) :")
        for slug, _, description in missing:
            print(f"  - {slug} : {description}")

    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
