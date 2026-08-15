# Lanceur Minecraft

Un lanceur Minecraft en Python, avec interface graphique, connexion Microsoft et gestion des mods.

## Installation en une étape

1. Télécharge **[`launcher/Installer.bat`](launcher/Installer.bat)** (bouton *Download raw file*)
2. Place-le où tu veux — Bureau, Documents, peu importe
3. Double-clique dessus

Il télécharge le lanceur et le démarre. **C'est tout, et c'est la dernière fois que tu viens ici :**
les mises à jour suivantes se font depuis le lanceur, dans *Paramètres → Vérifier et mettre à jour*.

Il te faudra aussi :

- **Python** — [python.org/downloads](https://www.python.org/downloads/), en cochant
  *Add Python to PATH* pendant l'installation
- **Java 25** — exigé par Minecraft 26.2
- **Une application Azure gratuite** pour la connexion Microsoft — la procédure est dans
  [`launcher/README.md`](launcher/README.md), section *Azure*

## Ce que fait le lanceur

| | |
|---|---|
| **Jouer** | installe n'importe quelle version, avec ou sans Fabric, et la lance |
| **Mods** | ajoute tes mods, active/désactive, télécharge le pack performance |
| **Compte** | connexion Microsoft, pour que les serveurs en ligne t'acceptent |
| **Réparer** | récupère les fichiers manquants sans tout réinstaller |

Une installation complète de Minecraft 26.2 — 582 Mo, 5057 fichiers — prend une vingtaine de
secondes sur une bonne connexion.

## Contenu du dépôt

```
launcher/    Le lanceur. C'est ce qui t'intéresse.
modpack/     Script qui résout les mods de performance depuis Modrinth.
archive/     Proof-of-concept d'un client Minecraft modulaire en Java.
             Exploration d'architecture, sans rapport avec le lanceur.
```

## Documentation

- [Lanceur — installation, Azure, dépannage](launcher/README.md)
- [Mods de performance — quoi installer et pourquoi](modpack/README.md)
- [POC client Java — architecture](archive/poc-client-java/README.md)
