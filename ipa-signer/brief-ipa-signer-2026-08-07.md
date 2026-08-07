# Brief de cadrage — IPA / TIPA Signer
Date : 2026-08-07

## Contexte (C)
Antoine veut un site web qui permet de signer des fichiers `.ipa` / `.tipa` avec ses **propres** certificats de développeur Apple (fichiers P12 + provisioning) achetés par lui-même sur AppleP12.com ou équivalent. Workflow légitime de sideloading : l'utilisateur paie et fournit ses propres signatures.

## Rôle (R)
Expert technique : concevoir et livrer l'outil complet, fonctionnel.

## Instruction (I)
Construire un site fonctionnel : upload d'un IPA/TIPA + certificat P12 + mot de passe + provisioning, signature réelle côté serveur, téléchargement de l'IPA signé.

## Spécifications (S)
- Signature **réelle** via `zsign` (fonctionne sous Linux, sans Mac).
- Hébergement **gratuit mais fonctionnel** → packagé en Docker, configs de déploiement gratuit fournies (Render / Fly.io / Hugging Face Spaces).
- UI en français, sobre, moderne, mobile-friendly.
- Ligne rouge : uniquement les certificats de l'utilisateur (aucune signature "gratuite" mutualisée, aucun contournement). Les clés privées ne sont jamais loggées et les fichiers sont supprimés après signature.

## Pièces & exemples (P)
Aucune pour l'instant (l'utilisateur fournira son P12 + provisioning au moment de l'usage).

## Évaluation (E)
Succès = déployer le conteneur, uploader un IPA + son P12 + provisioning valides, récupérer un IPA signé installable. Livraison directe (1 cycle), ajustements ensuite si besoin.

## Mode de travail
- Guidage pas à pas : non (je livre l'ensemble, avec une doc de déploiement claire)
- Découpage en étapes : non (build en continu, puis push)
