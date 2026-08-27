# 🎮📺 Jouer sur la télé (Android TV) — la bonne méthode

Tu voulais **une application sur l'Android TV qui affiche l'écran de la
tablette**, pour jouer sur grand écran. J'ai creusé la question : cette
solution existe, mais elle ne marchera pas pour du jeu, et il y a beaucoup
mieux. Voici les faits, puis la méthode qui marche.

---

## ❌ Pourquoi le miroir d'écran ne convient pas au jeu

### 1. C'est beaucoup trop lent

| Méthode | Retard entre ta manette et l'image |
|---|---|
| Miracast | **jusqu'à 250 ms** (plafond de la certification officielle) |
| Google Cast / Chromecast (miroir) | **0,5 à 4 secondes** en pratique |
| **Steam Link sur bon réseau** | **14–16 ms** |
| **Moonlight** | quelques millisecondes |

250 ms, c'est un quart de seconde entre le moment où tu appuies et le moment
où ça bouge : injouable, même pour un jeu lent. Le miroir d'écran est conçu
pour des photos et des diaporamas, pas pour du jeu.

### 2. Ta tablette ne peut probablement pas émettre

- **Miracast a été retiré d'Android** à partir de la version 6.0. Ce qui
  subsiste, ce sont les implémentations maison des grandes marques (Samsung
  Smart View, LG, Sony…). Une tablette générique Allwinner n'en a pas.
- **Google Cast** (« Diffuser l'écran ») fait partie des services Google Play.
  Sur une tablette non certifiée, l'option est souvent absente ou ne
  fonctionne pas.

### 3. Le câble est impossible sur cette tablette

C'est définitif, c'est dans les spécifications du processeur **Allwinner
A133** : ses seules sorties d'affichage sont MIPI-DSI, LVDS et RGB — des
connexions internes vers la dalle. **Aucune sortie HDMI ni DisplayPort.** Son
USB est en 2.0, ce qui exclut aussi le mode DisplayPort par USB-C. Un
adaptateur USB-C → HDMI n'affichera rien du tout : ce n'est pas une question
de réglage, le signal n'existe pas dans la puce.

---

## ✅ La bonne méthode : installer les jeux sur l'Android TV

Ton Android TV est un vrai appareil Android, bien plus puissant que la
tablette pour ça. Au lieu de faire transiter l'image par la tablette,
installe directement sur la TV :

| Application | Ce que ça fait | Nom exact sur le Play Store |
|---|---|---|
| **Steam Link** | Tes jeux Steam depuis ton PC, jusqu'en 4K | `com.valvesoftware.steamlink` |
| **Moonlight** | Idem, libre et gratuit, souvent plus fluide (PC NVIDIA) | `com.limelight` |
| **GeForce NOW** | Jeux en cloud, sans PC (abonnement) | `com.nvidia.geforcenow` |

**Comment faire** : sur l'Android TV, ouvre le Play Store, cherche
« Steam Link », installe. Appaire la manette **sur la TV** (Réglages →
Télécommandes et accessoires → Ajouter un accessoire). C'est tout.

Conseil de Valve : **câble Ethernet des deux côtés** (PC et Android TV) pour
la meilleure latence. À défaut, Wi-Fi 5 GHz des deux côtés.

Résultat : image en 4K, latence de l'ordre de 15 ms, et la tablette reste
libre — elle sert de télécommande, de second écran ou de TV d'appoint dans une
autre pièce.

---

## 🔁 Et si tu veux quand même le miroir (hors jeu)

C'est utile pour montrer des photos, un site web ou une application qui n'a
pas de version TV. Deux cas :

### Ton Android TV sait déjà recevoir

Android TV et Google TV embarquent **Chromecast d'origine** : aucune
application à installer sur la TV. Depuis la tablette, si l'option existe :
Paramètres → Appareils connectés → « Diffuser », ou l'application Google Home.

### Sinon : AirScreen

**AirScreen** (`com.ionitech.airscreen`) est le récepteur le plus complet sur
Android TV : il accepte Google Cast, AirPlay, Miracast et DLNA. Gratuit, avec
publicités et, d'après les tests disponibles, une limite d'environ 30 minutes
de miroir par jour dans la version gratuite ; un abonnement lève la limite.

⚠️ Deux pièges :
- La plupart des applications « Miracast » du Play Store sont des
  **émetteurs**, pas des récepteurs : installées sur la TV, elles ne servent à
  rien. Vérifie le nom exact du développeur (Ionitech pour AirScreen).
- Recevoir du Miracast exige du matériel Wi-Fi Direct dans la TV : si ta TV ne
  l'a pas, aucune application ne le remplacera. Teste la version gratuite
  avant de payer quoi que ce soit.

---

## 📱 Le rôle idéal de la tablette dans tout ça

Elle ne sert pas d'intermédiaire vers la TV — elle a ses propres usages :

- **TV d'appoint** (cuisine, chambre) avec OQEE et les applis de streaming ;
- **Télécommande** : l'application Google TV transforme la tablette en
  télécommande pour l'Android TV ;
- **Second écran** : le guide TV ou un match pendant qu'un film passe sur la
  télé ;
- **Console d'appoint** avec Steam Link + manette, en itinérance dans la
  maison — la latence Wi-Fi y est bien plus acceptable que via un double
  relais tablette → TV.

## Sources

- Spécifications Allwinner A133 :
  [fiche technique constructeur](https://www.allwinnertech.com/uploads/pdf/20210803142431e6.pdf)
- Retrait de Miracast d'Android :
  [note technique Mersive](https://documentation.mersive.com/content/pdf/miracastperformancetechnote.pdf)
- Latence Miracast (plafond certifié) :
  [spécification Wi-Fi Alliance v2.3](https://www.wi-fi.org/system/files/Miracast_Specification_v2.3.pdf)
- Google Cast et services Google Play :
  [documentation Google Cast](https://developers.google.com/cast/docs/android_sender)
- [Steam Link sur Google Play](https://play.google.com/store/apps/details?id=com.valvesoftware.steamlink)
- [Moonlight sur Google Play](https://play.google.com/store/apps/details?id=com.limelight)
- [AirScreen sur Google Play](https://play.google.com/store/apps/details?id=com.ionitech.airscreen)
