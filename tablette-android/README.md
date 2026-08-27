# 📱 FreePlay Tablette — l'édition tablette Android

Transforme une tablette Android en "box" Freebox : **chaînes TV Free (OQEE)**,
**Netflix / Disney+ / Prime Video** (et rien d'autre), **jeux Steam en
streaming**, optimisations fluidité et Wi-Fi. Le tout **sans rien flasher** et
**100 % réversible**.

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
