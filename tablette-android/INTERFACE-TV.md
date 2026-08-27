# 🖥️ FreePlay TV — l'interface façon Android TV

Une application d'accueil écrite pour cette tablette : grandes tuiles, heure,
état de la manette, navigation à la manette comme au doigt. Elle remplace
l'écran d'accueil Android d'origine — sans rien flasher, et réversible en une
commande.

![Aperçu de l'organisation de l'écran](#)

```
┌──────────────────────────────────────────────────────────────┐
│  Bonsoir                                    🎮 Manette : ... │
│  21:34  Mercredi 27 août                                     │
│                                                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ 📺         │  │ ▶          │  │ ▶          │              │
│  │            │  │            │  │            │              │
│  │ TV         │  │ Netflix    │  │ Disney+    │   ← la tuile │
│  │ Chaînes…   │  │ Films…     │  │ Marvel…    │     ciblée   │
│  └────────────┘  └────────────┘  └────────────┘     grandit  │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ Prime Video│  │ 🎮 Jeux    │  │ Vidéos     │              │
│  └────────────┘  └────────────┘  └────────────┘              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ Manette    │  │ Toutes les │  │ Réglages   │              │
│  │            │  │ apps       │  │            │              │
│  └────────────┘  └────────────┘  └────────────┘              │
└──────────────────────────────────────────────────────────────┘
```

## Ce que fait l'interface

- **Tuiles aux couleurs des services** (rouge Netflix, bleu Disney+…), avec
  l'icône réelle de l'application ; celle qui a le focus grandit et se
  souligne de blanc, exactement comme sur Android TV.
- **Navigation manette et tactile** : croix directionnelle ou doigt, les deux
  fonctionnent. Le bouton retour ne quitte jamais l'accueil (comportement
  normal d'un écran d'accueil).
- **Heure, date et salutation** (« Bonjour / Bon après-midi / Bonsoir »)
  mises à jour en direct.
- **État de la manette en haut à droite** : le nom de la manette s'affiche en
  vert dès qu'elle est reliée, et disparaît quand elle s'éteint.
- **Applis absentes visibles** : une tuile marquée « À installer » ouvre la
  bonne fiche du Play Store au clic — rien n'est caché.
- **Plein écran permanent** : ni barre de statut ni barre de navigation, la
  tablette ressemble à une box.
- **« Toutes les apps »** liste tout ce qui est installé, par ordre
  alphabétique, navigable à la manette.

## Pourquoi une application maison plutôt qu'un launcher existant

J'ai vérifié les alternatives avant d'écrire celle-ci :

| Launcher | Verdict |
|---|---|
| **FLauncher** | Abandonné : dernière version en avril 2023, l'auteur le déclare lui-même instable |
| **Projectivy** | Bien maintenu, mais réservé aux appareils Android TV (`leanback` obligatoire) : sur tablette il faut le sideloader, et il est pensé pour une télécommande, pas pour le tactile |
| **ATV Launcher Pro** | Payant, et impossible de vérifier son fonctionnement sur tablette sans l'acheter |
| **Wolf Launcher** | Abandonné, retiré du Play Store |

FreePlay TV est déclarée `leanback` **facultatif** : elle s'installe sans
contrainte sur une tablette ordinaire, gère le tactile *et* la manette, et
elle est réglée sur les applis de ce projet (OQEE, Netflix, Disney+, Prime
Video, Steam Link, VLC).

## Installation

Tablette branchée en USB au PC, débogage USB activé (voir
[README.md](README.md), section Prérequis) :

```bash
./install-interface-tv.sh        # Linux / macOS
install-interface-tv.bat         # Windows
```

Le script installe l'application, en fait l'écran d'accueil, applique les
réglages manette, puis l'ouvre pour vérification.

**Tout annuler** — l'accueil d'origine est mémorisé avant toute modification :

```bash
./install-interface-tv.sh --annuler
```

> Pas de risque de bloquer la tablette : si l'accueil venait à disparaître,
> Android bascule automatiquement sur un accueil de secours, et l'ADB permet
> toujours de revenir en arrière.

## 🎮 La manette « tout le temps connectée »

### Ce qui est fait pour ça

Une manette Bluetooth se déconnecte pour trois raisons, toutes traitées :

| Cause de déconnexion | Traitement |
|---|---|
| Android coupe le Bluetooth pour économiser la batterie | Le Bluetooth est forcé à rester allumé, et l'application le rallume s'il tombe (jusqu'à Android 12) |
| La tablette gèle les applis en veille (mode « Doze ») | L'application est mise en liste blanche et tourne en service permanent, donc jamais gelée |
| L'écran s'éteint et la tablette s'endort | Écran maintenu allumé tant que la tablette est branchée, et le processeur reste assez actif pour répondre à la manette |

En pratique : la manette se reconnecte **instantanément** dès qu'on appuie sur
son bouton central, sans repasser par les réglages, et elle ne se coupe plus
en pleine partie ni pendant un film.

### La limite, dite honnêtement

**Aucune application Android ne peut rallumer une manette éteinte.** La
reconnexion est toujours déclenchée par la manette (bouton Xbox / PS / bouton
central). Ce que fait FreePlay TV, c'est garantir que cette reconnexion
aboutisse tout de suite, à tous les coups — et non pas la provoquer.

Deux points dépendant de la version d'Android :
- **Android 13 et plus** réserve le rallumage du Bluetooth à l'utilisateur :
  si tu coupes le Bluetooth à la main, il faut le rallumer à la main.
- La notification permanente « FreePlay TV — Manette prête » est ce qui
  maintient le service en vie : ne la désactive pas.

### Appairer la manette (une seule fois)

1. Manette **éteinte** : maintiens son bouton d'appairage (Xbox : petit bouton
   près du port USB ; PlayStation : `PS` + `Share` ensemble) jusqu'au
   clignotement rapide de la LED.
2. Sur la tablette, ouvre la tuile **Manette** et sélectionne-la dans la liste.

Manettes qui fonctionnent bien sur Android : Xbox Series / Xbox One
Bluetooth, DualShock 4, DualSense, 8BitDo. Ensuite, tout se pilote à la
manette : croix directionnelle pour se déplacer, A/✕ pour valider.

## Modifier les tuiles

Les tuiles sont définies dans un seul fichier :
[`launcher/app/src/main/java/fr/freeplay/tv/Tiles.kt`](launcher/app/src/main/java/fr/freeplay/tv/Tiles.kt).
Chaque entrée porte un titre, un sous-titre, un nom de paquet et deux couleurs
de dégradé. Après modification :

```bash
cd launcher && ./gradlew assembleRelease
```

L'APK sort dans `launcher/app/build/outputs/apk/release/`. GitHub le
recompile aussi automatiquement à chaque modification : onglet **Actions** du
dépôt → dernière exécution → **FreePlayTV-apk**.

## Détails techniques

- Kotlin, `minSdk 21` (Android 5) → `targetSdk 34`, APK de 3 Mo.
- Aucune dépendance Google : fonctionne sur une tablette non certifiée.
- Aucun accès réseau, aucune publicité, aucune collecte de données.
- Signée avec une clé de test : Android la traite comme une application
  installée hors Play Store (« sources inconnues » à autoriser).
- 8 tests automatisés vérifient que les écrans se construisent sans planter,
  que les tuiles s'affichent toutes et que le service manette démarre
  proprement (`./gradlew testReleaseUnitTest`).
