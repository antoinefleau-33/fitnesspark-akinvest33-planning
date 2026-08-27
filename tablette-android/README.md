# 📱 FreePlay Tablette — l'édition tablette Android

Transforme une tablette Android en "box" Freebox : **interface façon Android
TV**, **chaînes TV Free (OQEE)**, **Netflix / Disney+ / Prime Video** (et rien
d'autre), **jeux Steam en streaming**, **manette toujours connectée**,
optimisations fluidité et Wi-Fi. Le tout **sans rien flasher** et
**100 % réversible**.

## 🚀 Les trois scripts, dans l'ordre

| # | Script | Ce qu'il fait |
|---|---|---|
| 1 | `install-tablette.sh` / `.bat` | Installe les applis (OQEE, Netflix, Disney+, Prime, Steam Link, VLC) et optimise la tablette |
| 2 | `install-interface-tv.sh` / `.bat` | Installe **l'interface FreePlay TV** et rend la **manette permanente** → [INTERFACE-TV.md](INTERFACE-TV.md) |
| — | *(lecture)* | Jouer sur la télé : pourquoi le miroir d'écran ne convient pas, et quoi faire → [JOUER-SUR-LA-TV.md](JOUER-SUR-LA-TV.md) |

---

## ⚠️ Pourquoi on ne remplace PAS l'OS d'une tablette

Sur le PC (voir le [README principal](../README.md)), on installe un vrai OS.
Sur une tablette Android, c'est une mauvaise idée, et voici pourquoi
honnêtement :

1. **La plupart des tablettes ont un bootloader verrouillé** (Samsung récents,
   Amazon, beaucoup de Lenovo/Xiaomi) : impossible d'y mettre un autre
   système. Les ROM alternatives (LineageOS) n'existent que pour une petite
   liste de modèles précis.
2. **Tu perdrais Netflix/Disney+ en bonne qualité.** Flasher une ROM custom
   fait perdre la certification DRM **Widevine L1** : les applis de streaming
   tombent en basse définition (SD). Exactement l'inverse du but recherché.
3. **Ça n'apporterait rien pour le jeu.** Steam et les jeux PC n'existent pas
   sur processeur ARM : aucun OS alternatif ne fera tourner Steam nativement
   sur une tablette. La bonne solution, c'est le **streaming** (voir plus bas).
4. Risques en prime : garantie perdue, tablette « brickée » si ça tourne mal.

**Conclusion : Android EST le bon OS pour une tablette.** Ce qu'on change,
c'est sa configuration — même résultat qu'une box, zéro risque. Et comme rien
n'est flashé, **il n'y a rien à "tester avant" qui soit dangereux** : tout
s'essaie directement et tout s'annule en une commande (`--annuler`).

---

## 🧰 Ce que le script installe et règle

| Fonction | Appli (Play Store officiel) |
|---|---|
| 📺 Chaînes TV Freebox, replay, enregistrements, guide TV | **OQEE by Free** — marche aussi hors de la maison |
| 🎬 Streaming | **Netflix**, **Disney+**, **Prime Video** — uniquement ces trois |
| 🎮 Jeux Steam | **Steam Link** — streame les jeux depuis ton PC (voir plus bas) |
| 🎞️ Lecteur universel | **VLC** — fichiers, clés USB-C, et flux TV local `http://mafreebox.freebox.fr/freeboxtv/playlist.m3u` à la maison |
| 📶 Wi-Fi | **Freebox Connect** — qualité du signal, choix du canal, gestion de la box |

Optimisations appliquées (réversibles) :
- **Fluidité** : animations 2× plus rapides — la tablette paraît nettement plus réactive ;
- **Usage TV** : écran qui ne s'éteint plus au bout d'une minute (30 min) ;
- **Wi-Fi jamais mis en veille** : plus de coupures en plein film ;
- **Nettoyage optionnel** : désactivation (pas suppression) des applis
  préinstallées inutiles (Facebook, OneDrive, LinkedIn…) pour libérer
  mémoire et batterie.

## 🎮 Steam sur tablette : comment ça marche vraiment

Les jeux Steam sont des jeux PC : ils ne peuvent pas *tourner* sur la
tablette, mais ils peuvent s'y *afficher* :

- **Steam Link** (installé par le script) : ton PC fait tourner le jeu, la
  tablette reçoit l'image et renvoie la manette — quasi sans latence sur le
  même réseau Wi-Fi 5 GHz. Ça se marie parfaitement avec le PC FreePlay OS du
  README principal (Steam y est déjà) ou n'importe quel PC gamer.
- Pas de PC ? Le **cloud gaming** (GeForce Now, Xbox Cloud Gaming) fait
  tourner les jeux sur des serveurs — abonnement à part, bonne connexion
  requise.
- Dans les deux cas : **manette Bluetooth** fortement recommandée (Xbox,
  DualShock/DualSense et 8BitDo se connectent très bien à Android).

---

## 🚀 Utilisation

### Prérequis (5 minutes, une seule fois)

1. **Sur la tablette — activer le débogage USB :**
   - Paramètres → À propos de la tablette → appuie **7 fois** sur
     « Numéro de build » → « Vous êtes développeur ! » ;
   - Paramètres → Système → **Options pour les développeurs** → active
     **Débogage USB**.
2. **Sur le PC — avoir `adb` :**
   - Windows : télécharge les [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools),
     dézippe le dossier ;
   - Ubuntu/Debian : `sudo apt install adb` — macOS : `brew install android-platform-tools`.
3. Branche la tablette au PC en USB. À la première commande, la tablette
   affiche « Autoriser le débogage USB ? » → **Autoriser**.

### Lancer la configuration

```bash
# Windows (double-clic possible, depuis le dossier des platform-tools) :
install-tablette.bat

# Linux / macOS :
./install-tablette.sh
```

Le script ouvre chaque appli sur le Play Store **de la tablette** (tu confirmes
chaque installation d'un tap — c'est le circuit officiel Google, pas
d'APK douteux), applique les optimisations, puis propose le nettoyage.

### Tout annuler

```bash
install-tablette.bat --annuler      # Windows
./install-tablette.sh --annuler     # Linux / macOS
```

Réglages d'origine restaurés, applis désactivées réactivées. (Les applis
installées se désinstallent normalement depuis la tablette, si besoin.)

---

## 📋 Ta tablette : puce Allwinner A133 (formes géométriques au démarrage)

Les **formes géométriques colorées au démarrage** sont l'animation de
démarrage standard des tablettes génériques à puce **Allwinner A133**
(4 cœurs Cortex-A53, GPU Mali-G31) : c'est **normal**, pas une panne.
⚠️ Si par contre elle **reste bloquée** dessus plus de 2–3 minutes :
maintiens le bouton power 15 secondes pour forcer un redémarrage ; si ça se
reproduit à chaque fois, démarre en recovery (power + volume haut, tablette
éteinte) et fais « Wipe data / factory reset » (efface tout le contenu).

À savoir honnêtement sur ces tablettes, avant de te lancer :

| Point | Réalité sur A133 | Quoi faire |
|---|---|---|
| Netflix / Disney+ / Prime | La plupart sont **Widevine L3** → qualité SD (480p). Quelques modèles sont L1 (HD) | Vérifie avec l'appli **DRM Info** ; si L3, c'est une limite matérielle définitive |
| Play Store | Certaines ne sont **pas certifiées Google** → Netflix peut être invisible sur le Play Store | Play Store → Paramètres → À propos → « Certification Play Protect ». Si « non certifié », OQEE/VLC marchent quand même |
| Wi-Fi | Beaucoup n'ont que le **2,4 GHz** | Si le réseau 5 GHz de la Freebox n'apparaît pas dans les réglages Wi-Fi, c'est le cas |
| Steam Link | Possible mais limité (Wi-Fi 2,4 GHz + puce modeste) | Colle-toi près de la box et baisse la qualité dans Steam Link → Paramètres → Streaming (Rapide) |
| TV (OQEE), VLC, YouTube | ✅ Ça, ça marche bien | Rien de spécial |
| Changer l'OS / ROM custom | **Non** : aucune ROM alternative n'existe pour ces modèles sans marque ; les firmwares « PhoenixSuit » trouvés sur des forums sont risqués (brick, malwares) et n'apporteraient rien | Garde Android d'origine + ce script |

Réglages bonus conseillés sur A133 (puce modeste) :

```bash
# Animations complètement désactivées (encore plus réactif que le 0.5 du script)
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

Et accepte l'étape « nettoyage » du script : sur 2–3 Go de RAM, chaque appli
désactivée compte. En résumé : cette tablette fera une très bonne **TV
d'appoint OQEE / lecteur VLC / écran Netflix (en SD)** ; pour le streaming de
jeux exigeant, n'en attends pas des miracles.

## 💡 Conseils qualité (4K / HD)

- **Vérifie le DRM de ta tablette** : installe « DRM Info » (Play Store). S'il
  affiche **Widevine L1** → Netflix/Disney+ en HD ; **L3** → limité en SD
  (limite matérielle de la tablette, aucun OS n'y changera rien).
- La résolution max reste celle de **l'écran de la tablette** — la plupart
  sont en 1920×1200 ou 2560×1600, très bien pour la HD/QHD.
- Reste sur le **réseau 5 GHz** de la Freebox (vérifiable dans Freebox
  Connect) et proche de la box pour le streaming de jeux.
- En déplacement, OQEE et les applis de streaming fonctionnent en 4G/5G ou
  sur n'importe quel Wi-Fi.
