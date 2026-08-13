# PROMPT MAÎTRE ULTRA-DÉTAILLÉ — « Arcane Legends » Édition Premium

> Document unique d'environ 20 000 mots : c'est à la fois le prompt à donner à une IA
> de développement et le cahier des charges complet du jeu (game design document).
> Tout ce qui suit fait partie du prompt : copier-coller l'INTÉGRALITÉ du document.


## SOMMAIRE ET MODE D'EMPLOI DU DOCUMENT

**Comment utiliser ce prompt.** Copier l'intégralité du document (Parties I
et II) dans l'outil de développement. La Partie I (Sections 0 à 18) décrit
QUOI construire : vision, systèmes, monde, contenus, interface, tutoriel,
architecture, données, sécurité, tests, livraison. La Partie II (Annexes D
à X) décrit COMMENT : recettes 3D pièce par pièce, plan de tests cas par
cas, jalons de construction, FAQ des ambiguïtés, spécification du stub de
test, gabarits d'écrans, parcours d'acceptation, anti-triche, style de
code, gabarit de README, spécification de Config, algorithme du build,
budgets de progression, mixage, localisation, recettes Studio,
journalisation, conformité Roblox, définition de « fini », et trente
pièges connus avec leurs parades. En cas de conflit apparent entre deux
passages : le plus spécifique l'emporte ; à spécificité égale, la Partie I
l'emporte ; et les invariants de la Section 0 l'emportent sur tout.

**Partie I — Cahier des charges.**
Section 0 : contrat de réalisation, résumé, piliers. Section 1 : boucle de
jeu minute par minute. Section 2 : économie et formules. Section 3 : le
monde zone par zone. Section 4 : les quatorze familiers. Section 5 :
ascensions. Section 6 : PvP d'arène. Section 7 : monétisation. Section 8 :
manifeste d'assets 3D, sons, icônes. Section 9 : interface écran par
écran. Section 10 : tutoriel. Section 11 : accessibilité tout âge.
Section 12 : architecture technique. Section 13 : données, sauvegarde,
fichier place. Section 14 : sécurité serveur. Section 15 : performances.
Section 16 : tests. Section 17 : livraison. Section 18 : roadmap.

**Partie II — Dossier d'exécution.**
Annexe A : tous les textes français. B : checklist d'acceptation. C :
variantes du prompt. D : recettes des fallbacks 3D. E : plan de tests
détaillé. F : douze jalons de construction. G : FAQ de conception (20
réponses). H : spécification du stub de test. I : glossaire. J : gabarits
d'écrans. K : parcours joueurs types. L : catalogue anti-triche. M : style
de code. N : gabarit du README. O : spécification de Config. P :
algorithme du build .rbxlx. Q : budget de progression. R : mixage sonore.
S : localisation future. T : recettes Studio. U : journalisation. V :
conformité Roblox. W : définition de « fini ». X : trente pièges Roblox.
Y : guide du moddeur (sept recettes de modification). Z : kit de
publication de la page du jeu.

---

## SECTION 0 — CONTRAT DE RÉALISATION (à lire avant tout)

Construis un jeu Roblox COMPLET, FONCTIONNEL et FINI appelé « Arcane Legends »,
un simulateur de sorcier à thème fantasy/magie. Ce document est ton unique source
de vérité. Règles de lecture :

1. **Aucune référence au sport, au fitness ou à la musculation**, ni dans le
   gameplay, ni dans les noms, ni dans les textes, ni dans les commentaires de
   code. Le lexique du jeu est exclusivement magique : mana, cristaux, sorciers,
   sorts, ascensions, familiers, sanctuaire.
2. **Travaille en autonomie totale : zéro question.** Chaque ambiguïté se
   résout par la lecture du présent document ; s'il reste un trou, choisis
   l'option la plus simple et la plus standard du genre « simulateur Roblox »,
   et documente ta décision dans un fichier DECISIONS.md livré avec le code.
3. **Rien n'est optionnel.** Chaque section chiffrée (coûts, multiplicateurs,
   probabilités, dimensions, textes) est NORMATIVE : la valeur écrite ici est
   la valeur dans le code. L'équilibrage vit dans un unique module Config.
4. **Si un code v1 du jeu existe déjà** (mêmes systèmes, sans les assets ni le
   tutoriel), pars de lui et upgrade-le SANS régresser sur ses garanties
   (serveur autoritaire, sauvegarde robuste, achats idempotents, tests).
5. **Livraison** = code source organisé + fichier place .rbxlx prêt à ouvrir +
   README + journal des décisions + rapport. Le détail exact est en Section 17.
6. **Public visé : tout âge (8 ans et +).** Chaque choix d'interface, de texte
   et de gameplay doit rester lisible, bienveillant et conforme aux règles
   Roblox pour un public familial. Aucun contenu effrayant, aucune punition.

### 0.1 Résumé du jeu en dix lignes

Le joueur incarne un apprenti sorcier. En cliquant sur des cristaux magiques,
il canalise de la Mana, la monnaie principale. La Mana débloque cinq zones de
plus en plus puissantes (multiplicateurs x1 à x625). En canalisant, il trouve
parfois des Gemmes, la monnaie rare, qui servent à ouvrir des œufs contenant
des familiers : chaque familier équipé multiplie les gains de Mana. Quand sa
Mana devient énorme, le joueur peut l'« Ascensionner » : il sacrifie TOUTE sa
Mana contre un bonus permanent de +50 % et des auras de prestige. Une arène
optionnelle permet des duels de sorts amicaux (on n'y perd rien) qui
rapportent des Gemmes. Un classement mondial persistant couronne les dix plus
grands canaliseurs de Mana. La monétisation accélère la progression (x2, auto,
zone VIP, familier mythique) sans jamais plafonner plus haut que le jeu gratuit.

### 0.2 Les cinq piliers de conception

- **Pilier 1 — La boucle avant tout.** Cliquer un cristal doit être agréable à
  la première seconde comme à la dixième heure : feedback visuel (texte
  flottant, éclat), sonore (carillon), numérique (compteur qui « pop »).
- **Pilier 2 — Toujours savoir quoi faire.** Un enfant de 8 ans qui ne lit pas
  les pavés doit progresser : tutoriel guidé par flèche, widget « Prochain
  objectif » permanent, une seule consigne courte à la fois.
- **Pilier 3 — Le serveur est la seule vérité.** Le client affiche et demande ;
  distances, cooldowns, fonds, zones, tirages, achats : tout est validé côté
  serveur. Un client modifié ne peut RIEN obtenir d'indu.
- **Pilier 4 — Beau sans dépendance.** Les vrais assets 3D passent par un
  manifeste d'IDs avec repli procédural automatique : le jeu est joli avec
  zéro ID renseigné, magnifique avec les IDs. Il ne casse JAMAIS.
- **Pilier 5 — Ne jamais perdre le joueur.** Sauvegardes robustes avec retry,
  session temporaire jamais écrasante, achats jamais consommés sans
  persistance possible, aucune perte en duel, aucune impasse de progression.

---

## SECTION 1 — LA BOUCLE DE JEU, MINUTE PAR MINUTE

### 1.1 La canalisation (action de base)

Le joueur interagit avec un **cristal de mana** de trois façons équivalentes :
un clic de souris (PC), un tap (mobile/tablette), ou la touche **E** à
proximité (ProximityPrompt). Les trois déclenchent la même action serveur.

Déroulé d'une canalisation réussie :
1. Le client interagit avec le cristal (ClickDetector ou ProximityPrompt,
   portée d'interaction : 14 studs).
2. Le serveur valide : le profil du joueur est chargé ; son personnage existe ;
   la distance personnage-cristal est ≤ 14 + 6 studs (marge de latence, le
   serveur reste seul juge) ; le cooldown personnel de 0,35 s est écoulé ; la
   zone du cristal est possédée (ou l'accès VIP est détenu pour les cristaux
   du Sanctuaire).
3. Le serveur calcule le gain (formule en 2.1), crédite la Mana, tire la
   chance de Gemmes (5 %), pousse le nouveau snapshot au client.
4. Le client affiche : texte flottant « +X mana » au-dessus du cristal (monte
   de 4 studs en 0,9 s en s'estompant), éclat de particules de la couleur du
   cristal, carillon sonore, « pop » du compteur de Mana dans le HUD.

Échecs silencieux (aucun message, la demande est simplement ignorée) :
cooldown non écoulé, distance excessive, profil non chargé. Échec expliqué
(notification) : zone non possédée — « Zone verrouillée ! Débloque “{zone}” à
son totem d'entrée. », limitée à une par 2 secondes pour ne jamais spammer.

### 1.2 Le rythme d'une première session (référence de test)

Cette chronologie est un CRITÈRE D'ACCEPTATION : si un testeur ne vit pas à
peu près ceci, l'équilibrage ou le tutoriel est cassé.

- **Minute 0-1 :** apparition au lobby, accueil du mage Maître Arcanis,
  tutoriel étape 1 : suivre la flèche verte jusqu'au premier cristal de la
  Prairie Arcanique, canaliser 5 fois (gain : 1 mana par clic, +10 offertes).
- **Minute 1-3 :** étape 2 : la gemme garantie tombe, popup « Les gemmes
  achètent des œufs ! », 50 gemmes offertes ; étape 3 : ouverture du menu
  Familiers, achat de l'Œuf Novice, cinématique d'éclosion, premier familier
  équipé automatiquement (multiplicateur x1,2 à x2,2).
- **Minute 3-8 :** farm de la Prairie (~2 mana/clic avec familier), objectif
  affiché « Débloque la Forêt Enchantée — X / 250 mana ». Déblocage vers la
  minute 6-8, étape 4 validée, félicitations du mage, +25 gemmes (étape 5).
- **Minute 8-15 :** la Forêt multiplie les gains par 5 (~10 mana/clic). Le
  widget vise les Cryptes (5 000). Le joueur découvre seul l'arène (panneau
  « on ne perd rien ! ») et le panneau de classement.
- **Minute 15-30 :** Cryptes débloquées (x25), premier objectif d'Ascension
  affiché à l'approche des 10 000 mana. Première Ascension vers la minute
  25-30 : aura bleutée, +50 % permanent, tout recommence plus vite. Le cycle
  addictif est en place.

### 1.3 Progression longue (référence d'équilibrage)

- Volcan Runique (100 000 mana) : atteignable en 1-2 heures de jeu actif avec
  2-3 ascensions et un familier Rare ou Épique.
- Dimension Astrale (2 000 000 mana) : objectif de moyen terme, ~5-10 heures,
  ou nettement moins avec gamepasses et familiers Légendaires.
- Palier d'aura maximal (10 ascensions, coût cumulé ~3,5 millions de mana
  sacrifiée) : objectif de prestige de longue haleine.
- Le classement mondial (mana totale gagnée EN JOUANT, la mana achetée ne
  compte pas) n'a pas de plafond : la compétition est infinie.

---

## SECTION 2 — ÉCONOMIE ET FORMULES (NORMATIF)

Toutes les constantes de cette section vivent dans `ReplicatedStorage/Config.lua`,
l'UNIQUE module d'équilibrage. Aucun nombre d'équilibrage en dur ailleurs.

### 2.1 Formule du gain de mana

À chaque canalisation validée :

```
gain = base × multZone × multFamiliers × multAscensions × multGamepass
base            = 1
multZone        = multiplicateur de la zone du cristal (x1, x5, x25, x125, x625, x150 VIP)
multFamiliers   = 1 + Σ (multiplicateur_familier_équipé − 1)      [3 équipés max]
multAscensions  = 1 + 0,5 × nombre_d_ascensions
multGamepass    = 2 si gamepass « x2 Mana », sinon 1
gain final      = arrondi entier (floor(gain + 0,5)), minimum 1
```

L'ordre de multiplication ci-dessus est l'ordre du code (l'associativité
flottante compte aux frontières exactes de 0,5 : le test doit reproduire le
même ordre). Exemples de contrôle OBLIGATOIRES dans les tests :

| Situation | Calcul | Gain |
|---|---|---|
| Débutant, Prairie | 1×1×1×1×1 | 1 |
| Forêt + Lueur (x1,2) | 1×5×1,2×1×1 = 6 | 6 |
| Cryptes + 3 familiers (1,4/1,7/2,2) + 2 ascensions | 1×25×3,3×2×1 = 165 | 165 |
| Endgame : Astrale + (3,5/3,2/3,2) + 10 asc. + pass | 1×625×7,9×6×2 = 59 250 | 59 250 |

### 2.2 Les deux monnaies

**Mana (monnaie principale).** Gagnée par canalisation uniquement (+ le Pack
de Départ). Dépensée pour : débloquer les zones, payer les Ascensions. La
mana COURANTE est ce qu'on dépense ; la mana TOTALE gagnée en jouant est le
score de classement (persistée séparément, jamais décrémentée, et la mana
achetée en Robux n'y entre PAS).

**Gemmes (monnaie rare).** Sources : 5 % de chance par canalisation (tirage
serveur : 1 à 3 gemmes, uniforme), victoire en duel (+25), cadeaux du
tutoriel (50 puis 25), achats Robux (100 / 1 200), Pack de Départ (200).
Puits : les œufs (50 / 500 / 5 000). Le gamepass « x2 Gemmes » double les
gains DE JEU (canalisation, duels) mais jamais les achats en Robux ni les
cadeaux du tutoriel.

### 2.3 Coûts des zones et retour sur investissement

| Zone | Coût | Mult. | Clics pour rentabiliser (sans bonus) |
|---|---|---|---|
| 1. Prairie Arcanique | gratuite | x1 | — |
| 2. Forêt Enchantée | 250 | x5 | ~63 clics à x4 de gain marginal |
| 3. Cryptes Oubliées | 5 000 | x25 | ~250 clics |
| 4. Volcan Runique | 100 000 | x125 | ~1 000 clics |
| 5. Dimension Astrale | 2 000 000 | x625 | ~4 000 clics |

La progression des coûts (×20, ×20, ×40) creuse volontairement l'écart : les
zones 4 et 5 ne sont atteignables confortablement qu'avec familiers et
ascensions, ce qui donne un but à chaque système. L'achat est SÉQUENTIEL :
la zone n exige la zone n−1 (message : « Débloque d'abord “{zone n−1}” ! »).

### 2.4 Coût des Ascensions

```
coût(n) = 10 000 × 4^n        (n = ascensions déjà accomplies)
n=0 → 10 000 ; n=1 → 40 000 ; n=2 → 160 000 ; n=3 → 640 000 ;
n=4 → 2 560 000 ; n=5 → 10 240 000 ; ... (aucun plafond)
```

L'Ascension consomme TOUTE la mana courante (pas seulement le coût). Elle ne
touche à rien d'autre : zones, familiers, gemmes et statistiques restent.
Bonus : +50 % de mana par ascension, cumulatif linéaire (10 ascensions = x6).
Auras de prestige aux paliers 1, 3, 6 et 10 (détail en Section 5).

### 2.5 Dégâts en duel

```
dégâts = clamp(5 + mana_courante^0,35 ; 5 ; 50)
mana 0 → 5 ; mana 1 000 → ~16,2 ; mana 100 000 → 50 (plafonné)
```

Le plafond de 50 garantit qu'aucun joueur ne « one-shot » personne (santé
Roblox standard : 100, donc minimum 2 sorts), et le plancher de 5 permet à un
débutant de participer. La mana n'est PAS consommée par les sorts.

### 2.6 Garde-fous économiques (NON NÉGOCIABLES)

- Aucune transaction ne peut rendre un solde négatif : toute dépense vérifie
  le solde AVANT de déduire, dans la même frame serveur (pas de yield entre
  vérification et déduction).
- Tous les crédits/débits passent par les deux fonctions uniques du module
  Economy (AddMana/SpendMana, AddGems/SpendGems) : aucun service ne touche
  directement aux champs du profil monnaie.
- Le tirage d'œuf débite d'abord, tire ensuite ; si le tirage échoue (pool
  mal configuré), il REMBOURSE et journalise un warning.
- La mana du Pack de Départ est créditée avec l'indicateur « hors mana
  totale » : elle est jouable mais invisible au classement.

---

## SECTION 3 — LE MONDE (map 100 % générée par script)

### 3.1 Vue d'ensemble et disposition

Le monde est un plateau continu d'environ 760 × 640 studs posé sur un grand
sol d'herbe (le « SolMonde », top à Y = 0). Aucun asset n'est importé à la
main : TOUT est construit par le module MapBuilder au démarrage du serveur,
et une copie statique de cette map est PRÉ-CUITE dans le fichier .rbxlx pour
que l'ouverture dans Studio montre le jeu (Section 13.6). Disposition :

```
                 [Zone 1] [Zone 2] [Zone 3] [Zone 4] [Zone 5]   (z = −150)
                     (alignées d'ouest en est, x = −220 … +220)

   [Sanctuaire VIP]          [ LOBBY ]              [ Arène ]
   (île volante à x=−190,    (centre 0,0)           (x=+190, z=+40)
    y=+40, z=+40)
                        [Panneau classement] (z=+48)
```

- Le **lobby** (110 × 110, marbre lavande clair) contient : le spawn (pad
  néon violet), la fontaine arcanique centrale (sphère ForceField bleutée
  + particules + titre « Arcane Legends »), le PNJ Maître Arcanis près du
  spawn, le pad doré vers le Sanctuaire VIP (coin nord-est), et le panneau
  physique du classement au sud.
- Les **cinq zones** (90 × 90 chacune, espacées de 110 studs) sont
  physiquement OUVERTES : on s'y promène librement, seul le GAIN est
  verrouillé côté serveur tant que la zone n'est pas achetée à son totem.
- L'**arène** est close par des murailles avec une seule entrée côté lobby.
- Le **Sanctuaire VIP** flotte à 40 studs d'altitude : uniquement accessible
  par téléportation (pad du lobby, vérification du gamepass côté serveur).

### 3.2 Fiche de zone — gabarit commun

Chaque zone comporte : un sol thématique (matériau + couleur dédiés), un
anneau de 6 cristaux (rayon 28 studs autour du centre, socles en pierre),
un totem d'achat à l'entrée sud (sauf zone 1, gratuite), un panneau flottant
« {emoji} {Nom} (x{mult}) » à 15 studs au-dessus du centre, des props
d'ambiance posés par PRNG déterministe (même graine = même map partout), et
un émetteur de particules d'ambiance couvrant la zone. Les cristaux d'une
zone partagent une couleur signature déclinée du sol au néon.

### 3.3 Zone 1 — Prairie Arcanique (gratuite, x1) 🌿

Ambiance : prairie de conte, herbe verte tendre (matériau Grass, RGB 124 200
108), cristaux vert lumineux (170 255 140). Props : 5 arbres ronds (tronc
cylindrique bois, feuillage sphérique), 8 fleurs lumineuses (tige fine +
corolle néon violette qui éclaire la nuit), 3 pierres runiques (blocs
d'ardoise gravés d'une rune néon cyan). Particules d'ambiance : lucioles
jaune-vert, lentes. Son d'ambiance (manifeste) : oiseaux de prairie. C'est la
zone-tutoriel : elle doit rester dégagée au centre pour que la flèche verte
et les cristaux soient évidents.

### 3.4 Zone 2 — Forêt Enchantée (250 mana, x5) 🌳

Ambiance : sous-bois féérique plus sombre (Grass, RGB 52 122 82), cristaux
vert d'eau (90 255 190). Props : 5 saules enchantés (troncs épais, triple
feuillage retombant), 6 champignons géants luminescents (pied crème, chapeau
néon turquoise éclairant), 4 fleurs lumineuses reprises de la Prairie.
Particules : spores turquoise en suspension. Son : grillons nocturnes. Le
totem de cette zone est la CIBLE de l'étape 4 du tutoriel : il doit être
visible depuis le lobby (orbe lumineuse au sommet).

### 3.5 Zone 3 — Cryptes Oubliées (5 000 mana, x25) 🪦

Ambiance : nécropole mystérieuse mais PAS effrayante (public 8+) : pierre
gris-mauve (Slate, RGB 110 108 128), cristaux améthyste (180 160 255).
Props : 5 colonnes brisées (fût cylindrique + chapiteau renversé), 6 stèles
arrondies penchées, 4 braseros de feu VERT ÉMERAUDE (vasque métallique,
flamme néon, halo lumineux) — le feu vert est féérique, pas macabre.
Particules : volutes violettes. Son : nappe grave et douce de souterrain.

### 3.6 Zone 4 — Volcan Runique (100 000 mana, x125) 🌋

Ambiance : caldeira de basalte (Basalt, RGB 130 60 44), cristaux orange lave
(255 120 60). Props : 7 pics de basalte anthracite inclinés (braises néon au
sommet), 4 mares de lave (disques néon orange, halo chaud, braises montantes).
Particules : cendres et étincelles oranges. Son : grondement de lave feutré.
Interdit : geysers qui blessent, sol qui brûle — le danger est décoratif,
JAMAIS punitif.

### 3.7 Zone 5 — Dimension Astrale (2 000 000 mana, x625) 🌌

Ambiance : plateau de glace stellaire (Glacier, RGB 70 60 140), cristaux
bleu-ciel galactique (150 200 255). Props : 5 îlots volants (plaques
minérales mauves flottant à ~8 studs, éclat néon dessus), 3 anneaux
planétaires (grands anneaux néon translucides inclinés autour d'un cœur de
glace). Particules : poussière d'étoiles blanche-bleutée, lente et féerique.
Son : nappe spatiale éthérée. C'est la vitrine du endgame : la zone doit être
visible de loin et donner ENVIE.

### 3.8 L'Arène des Duels ⚔️

Sol pavé sombre (Cobblestone, 70 45 60), murailles d'ardoise de 12 studs
crénelées, entrée unique de 14 studs côté ouest (vers le lobby), torches aux
quatre coins (flammes orange, lumière chaude), deux statues de sorciers
encadrant l'entrée (socle, robe, tête, chapeau, orbe violette lumineuse).
Deux panneaux flottants : au centre « ⚔️ Arène des Duels — Clique sur un
adversaire pour lancer un sort ! » ; à l'entrée, le panneau rassurant
OBLIGATOIRE : « Ici on s'entraîne entre sorciers : on ne perd RIEN ! ».
La zone de validité des duels est le rectangle exact du sol de l'arène.

### 3.9 Le Sanctuaire VIP 👑

Île volante de marbre doré (240 200 70) bordée d'un liseré néon, à 40 studs
d'altitude. 8 cristaux dorés (255 230 120) en anneau de rayon 22, x150
chacun. Accès : pad doré du lobby (sous un portail-arche de marbre doré à
vortex de particules) → téléportation SI le serveur confirme le gamepass ;
sinon notification + ouverture de l'invite d'achat Roblox. Pad de retour
violet au bord sud de l'île. Panneau « 👑 Sanctuaire VIP (x150) ».

### 3.10 Le panneau de classement 🏆

Stèle d'ardoise de 18 × 14 studs au sud du lobby, encadrée de deux montants
néon dorés, SurfaceGui face au lobby : bandeau-titre violet « 🏆 Top 10 —
Mana totale », 10 lignes alternées (fond sombre/plus sombre) avec : médaille
(🥇🥈🥉 puis #4…#10), vignette d'avatar (GetUserThumbnailAsync, pcall +
cache, case vide si échec), pseudo, score formaté (« 12,4M mana »).
Rafraîchissement toutes les 60 s (Section 13).

### 3.11 Éclairage et post-traitement

Crépuscule permanent féérique : ClockTime 17,5, Brightness 2, Ambient
(90 85 120), OutdoorAmbient (120 110 150). Atmosphère : densité 0,35, teinte
lavande (180 170 220), léger haze. BloomEffect doux (intensité 0,6, seuil
1,1) pour faire rayonner tous les néons. ColorCorrectionEffect global nommé
(saturation +0,08, contraste +0,04) dont la TEINTE est modulée CÔTÉ CLIENT
selon la zone où se trouve le joueur (vert d'eau en Forêt, ambre au Volcan,
bleuté en Astrale, doré au VIP — transitions douces en ~2 s). Technologie
d'éclairage : « Future » si disponible, sinon défaut.

---

## SECTION 4 — LES FAMILIERS (système complet + 14 fiches)

### 4.1 Règles du système

- Un familier possède : un identifiant technique (string ASCII), un nom
  français d'affichage, une rareté, un multiplicateur de mana, un emoji de
  secours pour l'interface, et un slot de mesh dans le manifeste d'assets.
- **Multiplicateur d'équipe = 1 + Σ (multiplicateur − 1)** sur les familiers
  ÉQUIPÉS uniquement (3 maximum). Exemples : un seul x1,2 → 1,2 ; trois
  x3,2 → 1 + 2,2×3 = 7,6. L'inventaire, lui, est illimité.
- Les doublons sont conservés (chaque exemplaire a son uid persistant, clé
  STRING pour la contrainte JSON des DataStores). Pas de fusion ni de
  recyclage en v2 (listé en roadmap, Section 18).
- Le premier familier obtenu s'équipe automatiquement s'il reste une place.
- Équiper/retirer est instantané, gratuit, sans confirmation (action
  parfaitement réversible → zéro friction).

### 4.2 Raretés

| Rareté | Couleur (RGB) | Rôle |
|---|---|---|
| Commun | 180 180 180 (argent) | volume des tirages, progression douce |
| Rare | 80 150 255 (bleu) | premier « waouh », crête néon sur le fallback |
| Épique | 180 90 255 (violet) | cap de mi-partie, cornes néon + traînée de particules |
| Légendaire | 255 190 60 (or) | chasse au trésor, couronne néon + traînée |
| Mythique | 255 70 90 (rubis) | exclusifs monétisation, halo annulaire + traînée |

À partir d'Épique, le familier émet une traînée de particules de sa couleur
de rareté (taux faible : 6/s) : le prestige se voit de loin.

### 4.3 Les quatorze familiers (fiches normatives)

**Œuf Novice (50 gemmes)**
1. **Lueur** ✨ — Commun, x1,2, poids 50. Petite boule de lumière pâle,
   l'ami de tout débutant. Fallback : sphère argentée, yeux ronds.
2. **Feu Follet** 🔥 — Commun, x1,25, poids 30. Flamme espiègle bleutée.
3. **Salamandre Bleue** 🦎 — Rare, x1,4, poids 15. Reptile magique d'eau,
   crête néon.
4. **Golem Runique** 🗿 — Épique, x1,7, poids 4. Golem de pierre gravé de
   runes, cornes néon claires.
5. **Phénix Mineur** 🐦 — Légendaire, x2,2, poids 1 (1 % !). Oisillon de feu
   doré, couronne néon. C'est LE jackpot du début de partie.

**Œuf Mystique (500 gemmes)**
6. **Esprit Sylvestre** 🍃 — Commun, x1,3, poids 45. Esprit de feuilles.
7. **Gardien de Cristal** 💠 — Rare, x1,5, poids 35. Éclat de cristal animé.
8. **Chimère d'Onyx** 🐺 — Épique, x1,9, poids 15. Louve d'obsidienne.
9. **Dragon d'Améthyste** 🐉 — Légendaire, x2,6, poids 5. Dragonnet violet.

**Œuf Céleste (5 000 gemmes)**
10. **Djinn des Sables** 🌪️ — Rare, x1,7, poids 50. Tourbillon doré malicieux.
11. **Licorne Astrale** 🦄 — Épique, x2,2, poids 35. Licorne constellée.
12. **Titan Stellaire** ⭐ — Légendaire, x3,2, poids 15. Colosse d'étoiles,
    le meilleur familier obtenable SANS Robux : le jeu gratuit garde un
    plafond de rêve accessible.

**Exclusifs monétisation (jamais dans les œufs)**
13. **Apprenti du Néant** 🌀 — Épique, x1,6. Exclusif Pack de Départ,
    spirale violette aux yeux curieux : un compagnon d'accueil, PAS un
    avantage décisif (plus faible que le Golem x1,7 trouvable à 4 %).
14. **Seigneur du Vide** 👁️ — Mythique, x3,5. Exclusif gamepass : œil
    cosmique auréolé d'un halo rubis. Le plus puissant du jeu (x3,5 contre
    x3,2 gratuit) : un ACCÉLÉRATEUR de +9 %, pas une domination.

### 4.4 Les œufs et leurs probabilités (affichées en jeu !)

La transparence des probabilités est OBLIGATOIRE : le panneau d'achat de
chaque œuf affiche la liste complète des familiers avec leur pourcentage
exact, calculé depuis les poids de Config (jamais de valeurs en dur).

| Œuf | Coût | Contenu (poids → %) |
|---|---|---|
| Novice | 50 💎 | Lueur 50 %, Feu Follet 30 %, Salamandre 15 %, Golem 4 %, Phénix 1 % |
| Mystique | 500 💎 | Sylvestre 45 %, Gardien 35 %, Chimère 15 %, Dragon 5 % |
| Céleste | 5 000 💎 | Djinn 50 %, Licorne 35 %, Titan 15 % |

Le TIRAGE est exclusivement serveur (tirage pondéré sur les poids, générateur
aléatoire serveur). Le client demande « ouvrir l'œuf n°X » et reçoit le
résultat ; il ne connaît jamais le tirage à l'avance.

### 4.5 Visuel des familiers dans le monde

Chaque familier équipé apparaît près de son maître : mesh du manifeste si
renseigné, sinon fallback procédural (sphère de la couleur de rareté + deux
yeux sombres + accessoire de rareté). Trois emplacements : épaule gauche
(−2,6 ; +1,6 ; +1,8), épaule droite (symétrique), au-dessus-arrière
(0 ; +2,6 ; +2,8). Le suivi utilise des CONTRAINTES physiques
(AlignPosition/AlignOrientation vers des attachments du personnage,
réactivité ~14) : le familier « traîne » légèrement derrière le mouvement,
effet organique voulu. Le flottement sinusoïdal (±0,35 stud, ~2,2 rad/s,
déphasé par slot et par joueur) est animé CÔTÉ CLIENT sur les attachments :
zéro coût réseau, zéro boucle serveur. Étiquette flottante : « {emoji}
{Nom} » dans la couleur de rareté, visible à 60 studs max.

---

## SECTION 5 — LES ASCENSIONS (prestige)

### 5.1 Fonctionnement

L'Ascension est le mécanisme de prestige : sacrifier TOUTE sa mana courante
(le coût minimal est 10 000 × 4^n, mais TOUT est pris) contre +50 % de mana
permanent. Elle se déclenche depuis l'écran « Ascension », protégé par une
confirmation en DEUX temps (Section 10.6) car c'est la seule action
« destructrice » du jeu — et elle doit être comprise, jamais subie.

### 5.2 Les auras de prestige (paliers 1 / 3 / 6 / 10)

L'aura est attachée au torse du personnage, recréée à chaque réapparition,
et remplacée (jamais cumulée) au palier suivant :

| Palier | Nom d'ambiance | Visuel |
|---|---|---|
| 1 ascension | Brume bleutée | particules bleu pâle, discrètes (8/s) |
| 3 ascensions | Volutes violettes | spirales mauves plus denses (14/s) |
| 6 ascensions | Flammes dorées | gerbe dorée richement lumineuse (22/s) |
| 10 ascensions | Tempête écarlate | tourbillon rubis (32/s) + ANNEAU AU SOL (gerbe horizontale rasante, 20/s) |

L'aura est le badge social du jeu : on doit reconnaître un vétéran au premier
regard, sans ouvrir aucun menu.

### 5.3 Écran d'Ascension — logique

L'écran affiche TOUJOURS, recalculé en direct depuis le snapshot :
« Ascensions accomplies : n », « Bonus permanent actuel : +n×50 % », le coût
de la prochaine ascension, un comparatif AVANT → APRÈS du multiplicateur
global (ex. « x3,30 → x3,80 »), une barre de progression vers le prochain
palier d'aura (« Prochaine aura : 3 ascensions — 1/3 »), et le rappel en
toutes lettres : « TOUTE ta mana est sacrifiée ». Le bouton est grisé avec le
montant manquant tant que le coût n'est pas atteint.

---

## SECTION 6 — LE PVP D'ARÈNE (optionnel, sans perte)

### 6.1 Principes pour tout âge

Le duel est un mini-jeu d'adresse OPTIONNEL : aucune perte pour le vaincu
(ni mana, ni gemmes, ni familier — il réapparaît simplement au lobby),
récompense pour le vainqueur (+25 gemmes, doublées par le gamepass x2
Gemmes). Aucune insulte d'interface : le message de défaite est
encourageant (« Duel perdu contre {X}... Reviens plus fort ! »).

### 6.2 Déroulé technique d'un sort

1. Le client clique/tape sur un adversaire : raycast caméra (300 studs,
   personnage local exclu) ; si la cible est un joueur ≠ soi, envoi de la
   demande CastSpell(cible) + son de lancement local.
2. Le serveur valide TOUT : profil chargé ; cible = vrai joueur connecté ≠
   lanceur ; les DEUX personnages ont un HumanoidRootPart ; les DEUX
   positions sont dans le rectangle de l'arène ; distance ≤ 45 studs ;
   cooldown personnel 0,6 s écoulé ; cible vivante.
3. Dégâts = clamp(5 + mana^0,35 ; 5 ; 50) appliqués via TakeDamage.
4. Visuels serveur (répliqués à tous) : projectile violet néon (sphère 1,4
   stud, traînée de particules, vitesse 90 studs/s, interpolé sur ~0,5 s)
   puis onde de choc au sol (anneau néon qui s'étend sur 0,32 s).
5. La victime reçoit l'événement d'impact : secousse de caméra locale
   (0,8 d'intensité, décroissance exponentielle ×0,86 par frame ; 0,35 si
   « réduire les effets ») + son d'impact.

### 6.3 Attribution de la victoire

Le serveur retient le DERNIER attaquant de chaque joueur (fenêtre de 10 s).
Si un joueur meurt dans cette fenêtre : le vainqueur gagne 25 gemmes
(notification « Duel remporté contre {X} ! +25 gemmes 💎 »), le vaincu reçoit
le message d'encouragement. Sortir de l'arène n'annule pas la fenêtre de 10 s
(anti-fuite) mais aucun sort ne peut plus être lancé hors de l'arène.
Auto-attaque impossible, joueurs hors arène intouchables, spam au-delà du
cooldown ignoré en silence.

---

## SECTION 7 — MONÉTISATION (accélération + statut, jamais de domination)

### 7.1 Philosophie normative

Le jeu est « pay-to-accelerate » : l'argent achète du TEMPS et du STATUT,
jamais un plafond inaccessible aux joueurs gratuits. Preuves chiffrées à
préserver dans tout ré-équilibrage : le meilleur familier payant (x3,5) ne
dépasse le meilleur gratuit (x3,2) que de 9 % ; les dégâts PvP sont plafonnés
à 50 pour tous ; le classement ne compte pas la mana achetée ; la zone VIP
(x150) reste en dessous du Volcan (x125) et loin de l'Astrale (x625) — c'est
un salon privé confortable, pas une zone de domination.

### 7.2 Les cinq gamepasses (fiches)

1. **x2 Mana** — Double toute la mana canalisée. L'achat de confort n°1,
   affiché en premier dans la boutique. Effet immédiat, aucun contenu exclusif.
2. **x2 Gemmes** — Double les gemmes GAGNÉES EN JOUANT (canalisation, duels).
   Ne double ni les achats en Robux ni les cadeaux du tutoriel.
3. **Auto-Canalisation** — Le bouton « Auto » du HUD devient fonctionnel :
   toutes les 0,6 s, le serveur canalise le cristal AUTORISÉ le plus proche
   (jamais un cristal d'une zone non possédée : l'auto ne spamme jamais
   d'erreurs, elle cible intelligemment). État persistant entre sessions.
   Sans le pass, le bouton ouvre l'invite d'achat.
4. **Sanctuaire VIP** — Accès à l'île dorée et ses 8 cristaux x150 + statut
   social (on VOIT qui se téléporte). Vérifié serveur à CHAQUE téléportation
   et à CHAQUE canalisation de cristal VIP.
5. **Seigneur du Vide** — Le familier mythique x3,5, accordé une seule fois
   (marqueur persistant « VoidLordGranted »), automatiquement re-vérifié à
   chaque connexion (si le pass est détecté et le familier jamais accordé,
   l'octroi se fait, même si l'achat a eu lieu hors du jeu).

### 7.3 Les trois developer products (fiches)

1. **Pack de Départ** (UNIQUE par joueur) — 5 000 mana (hors classement) +
   200 gemmes + le familier exclusif Apprenti du Néant (x1,6, équipé si
   place). L'interface masque le pack dès qu'il est possédé. En cas d'achat
   forcé en double (hors interface) : mana et gemmes recréditées pour ne pas
   léser l'acheteur, mais JAMAIS de second familier. Badge « OFFRE UNIQUE ».
2. **100 Gemmes** — le petit appoint (deux Œufs Novices).
3. **1 200 Gemmes** — le coffre « meilleure valeur » (badge « MEILLEURE
   OFFRE ») : de quoi ouvrir deux Œufs Mystiques et deux Novices.

### 7.4 Exigences transactionnelles (NON NÉGOCIABLES)

- **ProcessReceipt IDEMPOTENT** : chaque reçu porte un PurchaseId unique,
  historisé en clé STRING dans le profil AVANT de rendre PurchaseGranted.
  Un reçu déjà vu → PurchaseGranted immédiat SANS re-créditer. Un joueur
  déconnecté, un profil non chargé, une session temporaire (Section 15.3),
  un ProductId inconnu → NotProcessedYet (Roblox représentera le reçu).
  Après crédit : sauvegarde immédiate en tâche de fond (best effort).
- **Cache des gamepasses** : interrogé au login (3 tentatives espacées de
  2 s puis 4 s en cas d'erreur d'API — un hoquet Marketplace ne doit JAMAIS
  priver un acheteur de ses avantages), rafraîchi en direct par l'événement
  d'achat en jeu (PromptGamePassPurchaseFinished), avec application
  immédiate des effets (l'octroi du Seigneur du Vide ATTEND le chargement du
  profil, borné à 30 s, pour éviter la course au login).
- **Tous les IDs à 0** dans Config avec le commentaire exact
  `-- TODO_UTILISATEUR : ID du gamepass` (ou du developer product). Un ID à
  0 = fonctionnalité proprement désactivée : bouton « non configuré », zéro
  erreur console, le reste du jeu tourne normalement.

---

## SECTION 8 — VRAIS ASSETS 3D : LE MANIFESTE (règle d'or)

### 8.1 Le contrat du manifeste

PERSONNE — ni humain pressé, ni IA — n'invente d'ID d'asset Roblox : un ID
faux, c'est un mesh invisible ou une erreur console. Donc :

- Un UNIQUE module `ReplicatedStorage/AssetManifest.lua` référence TOUS les
  assets externes du jeu : meshes, sons, icônes. Chaque entrée : `{ MeshId =
  0, TextureId = 0, Scale = 1, Notes = "mots-clés Creator Store" }` avec le
  commentaire `-- TODO_UTILISATEUR`.
- Un module `AssetLoader` centralise le chargement : ID renseigné → création
  runtime via **Part + SpecialMesh** (SpecialMesh.MeshId est modifiable à
  l'exécution, contrairement à MeshPart.MeshId) protégée par pcall ; ID à 0
  ou échec → appel du CONSTRUCTEUR DE REPLI fourni par l'appelant. Le repli
  n'est pas un cube gris : chaque fallback est un modèle stylisé en parts
  (Sections 3 et 4) digne d'être montré.
- Prechargement : au démarrage client, tous les IDs renseignés sont passés à
  ContentProvider:PreloadAsync (pcall, en tâche de fond).
- Le README livre la « liste de courses » : pour chaque slot, les mots-clés
  de recherche sur create.roblox.com/store, avec le rappel de vérifier la
  LICENCE d'utilisation de chaque asset avant usage.
- Alternative documentée : import de .fbx personnels via l'Asset Manager de
  Studio, puis coller les IDs obtenus dans le manifeste.

### 8.2 Slots de meshes (39 fiches normatives)

Cristaux (6) — un par zone + VIP ; recherche « low poly crystal » + couleur :
`crystal_zone1` (vert), `crystal_zone2` (émeraude), `crystal_zone3`
(améthyste), `crystal_zone4` (orange lave), `crystal_zone5` (bleu galaxie),
`crystal_vip` (doré). Échelle cible ~3×6×3 studs, base pointue plantée dans
un socle de pierre fourni par le jeu.

Familiers (14) — un par fiche de la Section 4.3 : `pet_lueur` (« wisp spirit
pet »), `pet_feufollet` (« flame spirit »), `pet_salamandre` (« salamander
lizard »), `pet_golem` (« rock golem »), `pet_phenix` (« phoenix bird »),
`pet_sylvestre` (« leaf forest spirit »), `pet_gardien` (« crystal guardian »),
`pet_chimere` (« onyx wolf »), `pet_dragon` (« amethyst dragon »),
`pet_djinn` (« genie djinn »), `pet_licorne` (« unicorn »), `pet_titan`
(« star titan colossus »), `pet_apprentineant` (« void apprentice »),
`pet_seigneurvide` (« void lord eye boss »). Gabarit ~2 studs, style low
poly mignon, PAS réaliste (cohérence tout-âge).

Œufs (3) — `egg_novice` (simple), `egg_mystique` (runes), `egg_celeste`
(galaxie) ; recherche « low poly egg ». Gabarit ~2×2,6×2.

Props de zones (11) — `prop_tree_prairie` (« low poly round tree »),
`prop_flower_prairie` (« glowing flower »), `prop_rock_prairie` (« rune
stone »), `prop_mushroom_foret` (« giant glowing mushroom »),
`prop_willow_foret` (« willow tree »), `prop_column_crypte` (« broken
column ruin »), `prop_tomb_crypte` (« tombstone »), `prop_brazier_crypte`
(« brazier fire bowl »), `prop_basalt_volcan` (« basalt rock spike »),
`prop_geyser_volcan` (« lava geyser rock »), `prop_island_astral`
(« floating island »), `prop_ring_astral` (« planet ring hoop »).

Structures (5) — `prop_arena_torch` (« medieval torch »),
`prop_arena_statue` (« wizard statue »), `prop_totem` (« magic totem
pillar »), `prop_vip_portal` (« magic portal arch gold »), `npc_mage`
(« wizard npc low poly », ou un rig R15 habillé — voir Notes du slot).

### 8.3 Slots de sons (21 fiches)

Tous joués CÔTÉ CLIENT uniquement (respect des réglages Musique/Effets du
joueur ; un serveur ne doit jamais imposer un son) ; ID à 0 → silence propre,
jamais d'erreur. Volumes par défaut entre parenthèses.

Interface : `ui_hover` (0,25 ; « ui hover tick soft »), `ui_click` (0,4 ;
« ui click pop »), `error` (0,4 ; « ui error soft buzz »).
Gameplay : `channel` (0,5 ; « magic chime pickup »), `gem` (0,6 ; « gem
sparkle collect »), `tutorial_step` (0,7 ; « quest complete ding »),
`purchase` (0,7 ; « success chime »), `rebirth` (0,8 ; « ascension power
up »), `victory` (0,8 ; « victory jingle »).
Éclosion : `hatch_common` (0,6 ; « egg crack pop »), `hatch_rare` (0,7 ;
« magic reveal shimmer »), `hatch_legendary` (0,8 ; « epic fanfare short » —
utilisé pour Légendaire ET Mythique).
Duel : `spell_cast` (0,6 ; « magic missile whoosh »), `spell_impact` (0,6 ;
« magic impact hit »).
Ambiances en boucle : `music_lobby` (0,25 ; « fantasy adventure loop »),
`ambience_zone1` (0,3 ; oiseaux de prairie), `ambience_zone2` (grillons
nocturnes), `ambience_zone3` (nappe de crypte douce), `ambience_zone4`
(grondement de lave), `ambience_zone5` (nappe spatiale éthérée),
`ambience_vip` (chœur céleste doux). Le client fond-enchaîne l'ambiance de
la zone courante (détection par position toutes les 0,5 s, volumes lissés).

### 8.4 Slots d'icônes (15 fiches)

ImageLabel si l'ID est renseigné, sinon l'EMOJI de secours (l'interface est
complète sans aucun ID) : `mana` ⚡, `gem` 💎, `pet` 🐾, `egg` 🥚, `shop` 🛒,
`rebirth` ✨, `auto` 🔁, `leaderboard` 🏆, `settings` ⚙️, `help` ❓,
`close` ✖, `arrow` ➤, `multiplier` 🔥, `objective` 🎯. Recherche : « {mot}
icon flat » sur le Creator Store, préférer des icônes plates lisibles à 24 px.

---

## SECTION 9 — L'INTERFACE (niveau « grands simulateurs », écran par écran)

### 9.1 Le système de design (module UIKit unique)

TOUTE l'interface est générée par script à partir d'un unique module de
composants `ReplicatedStorage/UIKit.lua`. Aucune fenêtre grise rectangulaire
brute : chaque surface applique le même langage visuel.

Palette normative (RGB) : fond de panneau 42 34 74 ; panneau sombre
30 24 54 ; panneau clair 58 47 98 ; contour 20 15 38 ; texte 255 252 255 ;
texte secondaire 196 190 224 ; accent violet 158 110 255 ; mana 90 190 255 ;
gemme 255 110 235 ; or 255 205 90 ; vert validation 90 205 120 ; rouge refus
235 90 100 ; bleu boutique 90 140 235 ; orange ascension 240 150 70.

Règles de construction communes : coins arrondis 12 px (UICorner) ;
contour épais 2,5 px sombre (UIStroke) ; dégradé vertical subtil du haut vers
une version 28 % plus sombre (UIGradient) ; police FredokaOne pour les
titres/boutons, GothamBold pour le corps, Gotham pour les descriptions ;
texte important TOUJOURS avec contour contextuel 2 px sombre (lisibilité sur
tout fond). Boutons : grossissent de 4 px au survol (0,12 s) avec un tic
sonore, s'enfoncent de 4 px au clic puis rebondissent (easing Back, 0,18 s)
avec un pop sonore. Tous les sons d'interface respectent le réglage
« Effets » (Section 9.8) et se taisent proprement si leurs IDs sont à 0.

### 9.2 Le HUD permanent

- **Pilules de monnaies** (haut-centre, côte à côte) : capsule sombre
  arrondie de 170 × 44 px contenant une pastille d'icône colorée (mana ⚡
  fond bleu, gemme 💎 fond rose) et la valeur formatée en abrégé français
  (1,2 k / 3,4 M / 2,1 Md). À CHAQUE gain : la pastille « pop » (grossit de
  24 % et revient en 0,25 s, easing Back) — le regard est attiré sans lire.
- **Pilule multiplicateur** : « 🔥 x{multiplicateur global:.2f} » ; son
  info-bulle décompose le calcul (« zone × familiers × ascensions × pass »).
- **Rang mondial** : si le joueur est dans le top 10 diffusé par le serveur,
  une pastille dorée « 🏆 Rang #N mondial ! » apparaît sous les pilules.
- **Colonne de boutons ronds** (gauche-centre, 64 px, icône + libellé
  au-dessous) : Boutique 🛒, Familiers 🐾, Ascension ✨, Auto 🔁 (sa couleur
  reflète l'état : vert = actif, gris = inactif, avec le texte « Auto : ON /
  OFF »), Réglages ⚙️, Aide ❓. Sur mobile, ces boutons gagnent 20 % de
  taille et s'espacent (cibles tactiles ≥ 44 px, zone du pouce respectée).
- **Widget « Prochain objectif »** (haut-gauche) : carte compacte affichant
  UNE SEULE prochaine étape calculée automatiquement, avec barre de
  progression et bouton 🎯 pour activer/désactiver la flèche 3D vers la
  cible. Ordre de calcul de l'objectif : tutoriel en cours → l'étape du
  tutoriel ; sinon première zone non achetée → « Débloque {zone} —
  {mana}/{coût} » ; sinon prochaine ascension → « Prochaine Ascension —
  {mana}/{coût} » ; sinon prochain palier d'aura ; sinon « Grimpe au
  classement mondial ! ». Le joueur ne doit JAMAIS voir un widget vide.
- **Notifications** (pile haut-droite, 320 px de large) : toasts arrondis
  colorés par type — info (bleu-gris), succès (vert), erreur (rouge doux),
  gemme (violet-rose) — fondu d'entrée 0,25 s, vie 4 s, fondu de sortie
  0,4 s, maximum raisonnable à l'écran (les plus anciens partent d'abord).
  Une erreur joue le son `error`, jamais de buzzer agressif.
- **Textes volants de gain** : « +X mana » (bleu) et « +N gemmes ! » (rose)
  s'élèvent au-dessus du cristal canalisé (BillboardGui local, 4 studs en
  0,9 s, fondu complet). En mode « réduire les effets », un seul texte à la
  fois.

### 9.3 La Boutique (fenêtre à onglets)

Fenêtre modale centrée 620 × 460 px (s'adapte si l'écran est plus petit :
largeur max = écran − 40 px), titre « 🛒 Boutique Arcanique », croix rouge en
haut-droite, ouverture animée (surgit de 95 % à 100 % en 0,25 s easing Back).
Trois ONGLETS : « Passes », « Gemmes », « Packs ».

- **Onglet Passes** : cinq cartes empilées (icône, nom, description en une
  phrase, bouton). États du bouton : « Acheter » (violet, ouvre l'invite
  Roblox PromptGamePassPurchase) ; « Possédé ✓ » (vert, inactif) ;
  « Indisponible » (gris, si ID = 0, avec l'info-bulle « pas encore
  configuré »). À l'achat confirmé en jeu, la carte passe en « Possédé ✓ »
  EN DIRECT (le serveur pousse le nouveau snapshot).
- **Onglet Gemmes** : deux cartes (100 💎, 1 200 💎) avec badge « MEILLEURE
  OFFRE » sur la seconde ; bouton → PromptProductPurchase.
- **Onglet Packs** : la carte unique du Pack de Départ, badge « OFFRE
  UNIQUE », liste des 3 contenus avec leurs icônes ; carte MASQUÉE une fois
  possédée (l'onglet affiche alors « Tu as déjà tout ! Merci 💜 »).

### 9.4 La fenêtre Familiers

Deux blocs : les ŒUFS en haut, l'INVENTAIRE en dessous.

- **Bloc œufs** : trois cartes côte à côte (Novice / Mystique / Céleste) :
  aperçu 3D de l'œuf en ViewportFrame (rotation lente), nom, coût en gemmes,
  bouton « Ouvrir » (grisé + montant manquant si fonds insuffisants), et le
  TABLEAU DES CHANCES : chaque familier possible avec pastille de couleur de
  rareté, nom et pourcentage exact (« Phénix Mineur — 1 % »). La transparence
  est une exigence, pas une option.
- **Bloc inventaire** : en-tête « Inventaire — n/3 équipés » ; grille de
  cartes 150 × 170 px : fond teinté de la rareté, aperçu 3D du familier en
  ViewportFrame (rotation lente 0,5 rad/s), nom, badge de rareté, « mana
  x{mult} », bordure dorée épaisse + coche si équipé. UN CLIC sur la carte
  équipe/retire immédiatement (feedback sonore + re-tri). Tri : équipés
  d'abord, puis rareté décroissante, puis ancienneté. Si l'inventaire est
  vide : illustration 🥚 et « Aucun familier : ouvre un œuf pour
  commencer ! ». La liste ne se reconstruit que quand la fenêtre est visible
  (jamais de travail caché à chaque gain de mana).

### 9.5 La cinématique d'éclosion (obligatoire)

Au retour du serveur après « Ouvrir » :
1. Overlay plein écran semi-opaque (fond 30 24 54 à 40 %), le reste de
   l'interface se fige derrière.
2. L'œuf apparaît en ViewportFrame central et TREMBLE en crescendo pendant
   1,6 s (oscillations d'amplitude croissante), craquement sonore.
3. Flash blanc 0,15 s → l'œuf est remplacé par la CARTE du familier : aperçu
   3D en rotation, nom, rareté, multiplicateur, le tout sur un fond de
   RAYONS ROTATIFS de la couleur de rareté (2 tours/min).
4. Son selon la rareté : `hatch_common` (Commun/Rare), `hatch_rare`
   (Épique), `hatch_legendary` (Légendaire/Mythique, avec en plus une pluie
   de confettis de particules).
5. Bouton unique « Génial ! » (+ mention « équipé automatiquement » si c'est
   le cas). UN CLIC N'IMPORTE OÙ PASSE TOUTE LA CINÉMATIQUE (skip
   instantané) : le respect du temps du joueur prime sur le spectacle.

### 9.6 L'écran d'Ascension

Contenu (Section 5.3) présenté en carte unique très lisible + le bouton.
CONFIRMATION EN DEUX TEMPS obligatoire : premier clic → le bouton devient
« Sûr ? TOUTE ta mana sera sacrifiée ! » (orange vif) pendant 4 s ; second
clic dans ce délai → l'Ascension part ; sinon retour à l'état normal. À la
réussite : son `rebirth`, notification de félicitations, éclat de particules
sur le personnage (l'aura changée est déjà visible), fermeture de l'écran.

### 9.7 Le panneau d'aide « ❓ Comment jouer »

Cinq pages, UNE idée par page, navigation par grosses flèches ‹ › et points
de pagination, gros émoji illustratif + UNE phrase :
1. ✨ « Clique sur les cristaux pour gagner de la mana ! »
2. 🌳 « Débloque des zones : elles donnent BEAUCOUP plus de mana ! »
3. 🐾 « Ouvre des œufs : les familiers multiplient tes gains ! »
4. 🌟 « L'Ascension échange ta mana contre un pouvoir PERMANENT ! »
5. ⚔️ « L'arène : des duels amicaux, on n'y perd rien ! »
Accessible à tout moment via le bouton ❓ du HUD.

### 9.8 Les Réglages ⚙️

Trois interrupteurs OUI/NON persistés dans le profil (appliqués côté
client) : « Musique 🎵 », « Effets sonores 🔊 », « Réduire les effets ✨ »
(coupe les particules d'ambiance et allège les animations — pour mobiles et
petites configs). Plus le bouton « Revoir le tutoriel » qui relance le
parcours guidé depuis l'accueil (sans re-donner les récompenses déjà
versées en cadeaux majeurs). Et la mention de version du jeu en petit.

### 9.9 Responsive et mobile

L'interface s'adapte à trois gabarits : téléphone (< 700 px de large),
tablette, PC. Règles : les fenêtres ne dépassent jamais l'écran (marges
20 px) ; TextScaled partout avec tailles minimales lisibles ; cibles
tactiles ≥ 44 px ; le HUD mobile compacte les pilules (icône + valeur, sans
libellé) ; IgnoreGuiInset géré (jamais de contenu sous la barre Roblox) ;
l'action « canaliser » fonctionne au tap direct sur cristal (ClickDetector
natif tactile) sans bouton dédié.

---

## SECTION 10 — LE TUTORIEL (textes exacts et logique serveur)

### 10.1 Principes

Le tutoriel doit permettre à un enfant de 8 ans qui NE LIT PAS les pavés de
comprendre le jeu : une seule consigne à la fois, douze mots maximum par
consigne, une flèche 3D vers la cible, une récompense à chaque étape, un son
de « quête accomplie » à chaque progression. Il est passable à tout instant
(bouton « Passer ▸ » toujours visible), ne se rejoue JAMAIS tout seul
(progression persistée dans le profil : TutorialStep de 0 à 6, 6 = terminé),
et reste rejouable volontairement depuis les Réglages.

RÈGLE D'OR DE SÉCURITÉ : le client AFFICHE le tutoriel, le serveur DÉCIDE.
Les transitions d'étapes liées au gameplay (canaliser, gemme, œuf, zone) sont
déclenchées par les services serveur eux-mêmes ; le client ne peut demander
que deux choses : avancer un DIALOGUE (autorisé uniquement aux étapes 0 et
5), passer ou rejouer. Toutes les récompenses sont créditées côté serveur.

### 10.2 Les étapes (contenu normatif)

**Étape 0 — Accueil.** Le mage Maître Arcanis (PNJ du lobby, surligné,
flèche verte) parle dans une bulle en bas d'écran (portrait 🧙, nom, texte,
gros bouton vert « Continuer ! ») :
« Bienvenue, apprenti ! La mana, c'est ta puissance magique. Suis la flèche
verte ! » → clic sur Continuer → étape 1.

**Étape 1 — Canaliser (objectif : 5).** Bannière haut-centre : « ✨ Clique
sur le cristal ! » + barre de progression « n / 5 ». Flèche 3D + surbrillance
sur le cristal de la Prairie LE PLUS PROCHE du joueur (recalculée au
respawn). Chaque canalisation validée serveur incrémente le compteur
persistant. À 5/5 : +10 mana offertes, notification « Bravo, apprenti !
+10 mana offertes ! », son de quête, étape 2.

**Étape 2 — La première gemme.** Bannière : « 💎 Canalise encore : une gemme
arrive ! » (sous-texte : « Les gemmes achètent des œufs ! »). PENDANT CETTE
ÉTAPE UNIQUEMENT, la chance de gemme est forcée à 100 % par le serveur : la
toute prochaine canalisation rapporte 1-3 gemmes. Dès la gemme obtenue :
+50 gemmes offertes (« Voici 50 gemmes pour ton premier œuf ! »), étape 3.

**Étape 3 — Le premier œuf.** Bannière : « 🥚 Ouvre le menu Familiers,
achète un œuf ! » (sous-texte : « 50 gemmes offertes ! »). Pas de flèche
3D : c'est le BOUTON Familiers du HUD qui pulse (halo animé) jusqu'à
l'ouverture de la fenêtre. L'achat de n'importe quel œuf (le Novice est le
seul abordable) déclenche la cinématique d'éclosion puis l'étape 4.

**Étape 4 — Débloquer la Forêt.** Bannière : « 🌳 Va au totem : débloque la
Forêt ! » (sous-texte : « Il te faut 250 mana »). Flèche 3D + surbrillance
sur le TOTEM de la zone 2. Le widget d'objectif affiche la progression de
mana. Au déblocage (validé serveur) : étape 5.

**Étape 5 — Diplôme.** Dialogue final du mage : « Magnifique ! Plus tard :
l'arène (on n'y perd rien !) et l'Ascension pour devenir légendaire. À toi
de jouer ! » → clic sur « Continuer ! » → +25 gemmes, notification « 🎓
Apprenti diplômé ! +25 gemmes ! », confettis de particules sur le
personnage, étape 6 (terminé). Le widget d'objectif prend le relais pour
toujours.

### 10.3 Cas limites (à couvrir par les tests)

Déconnexion en pleine étape → reprise EXACTE à la même étape avec le même
compteur (tout est persisté). Joueur qui ignore le tutoriel et va canaliser
ailleurs → les étapes 1-2 progressent quand même (n'importe quel cristal
autorisé compte). Joueur qui achète la zone 2 pendant l'étape 1 → les étapes
suivantes se valident dans l'ordre dès que leurs conditions arrivent (aucun
blocage). « Passer » → étape 6 immédiate, aucune récompense restante versée.
« Revoir le tutoriel » → retour à l'étape 0, compteur remis à zéro. Un
vétéran d'une sauvegarde antérieure au tutoriel (champ absent) → le champ se
crée à 0 : il voit l'accueil UNE fois (2 clics pour re-diplômer, cadeaux
mineurs re-versés : accepté et documenté).

---

## SECTION 11 — ACCESSIBILITÉ TOUT ÂGE (checklist normative)

Langage : phrases courtes, vocabulaire d'enfant, zéro jargon non expliqué
(« mana » est défini par le mage à l'étape 0 : « ta puissance magique »),
JAMAIS de mur de texte (le panneau d'aide : une phrase par page), nombres
toujours abrégés à la française (1,2 k — 3,4 M — 2,1 Md) accompagnés de leur
icône de monnaie.

Lisibilité : icône + couleur + FORME pour chaque information critique — les
raretés se distinguent par la couleur ET le badge texte ET l'accessoire du
familier (jamais la couleur seule : daltonisme) ; texte contrasté avec
contour sombre sur tout fond ; TextScaled avec minima ; boutons larges.

Guidage : le joueur sait TOUJOURS quoi faire (tutoriel puis widget
d'objectif permanent) ; chaque action impossible explique POURQUOI en une
phrase positive (« Il te faut encore 120 mana ! ») ; chaque réussite a un
retour visuel ET sonore ; info-bulle d'une phrase max sur chaque bouton du
HUD (survol PC, appui long mobile).

Bienveillance : AUCUNE punition (pas de perte en duel, pas de mort
punitive, pas de timer stressant, pas d'énergie limitée) ; contenu
familial strict (les Cryptes sont mystérieuses, pas macabres ; le feu des
braseros est vert féérique) ; l'arène est signalée comme optionnelle et sans
risque par un panneau À L'ENTRÉE ; le message de défaite encourage.

Confort : réglages Musique/Effets/Réduire-les-effets persistés ; le mode
réduit s'applique aux particules d'ambiance, à la secousse d'écran (réduite
de moitié) et aux textes volants ; le jeu reste 100 % jouable en silencieux
total (aucune information portée UNIQUEMENT par le son).

---

## SECTION 12 — ARCHITECTURE TECHNIQUE (modules et contrats)

### 12.1 Contrainte de langage

**Lua 5.1 pur, vérifiable par `luac -p`** sur chacun des fichiers : pas de
`+=`, pas de `continue`, pas d'annotations de type Luau, pas de `goto`.
Les API Roblox (task, typeof, Instance…) restent bien sûr utilisées À
L'EXÉCUTION — la contrainte porte sur la SYNTAXE. Interface joueur, textes
et commentaires : en français.

### 12.2 Arborescence des scripts

```
ReplicatedStorage/            (modules partagés serveur+client)
  Config.lua                  ← TOUT l'équilibrage + IDs monétisation
  Util.lua                    ← purs utilitaires (format, clamp, LCG, tirage…)
  Remotes.lua                 ← définition UNIQUE des remotes
  AssetManifest.lua           ← tous les IDs d'assets (TODO_UTILISATEUR)
  AssetLoader.lua             ← chargement + fallbacks + préchargement
  UIKit.lua                   ← système de design (client)
  PetModels.lua               ← modèles 3D familiers/œufs (monde + viewports)
  ArrowGuide.lua              ← flèche 3D + surbrillance (client)
ServerScriptService/
  Main.server.lua             ← unique Script serveur : bootstrap
  DataManager.lua             ← profils, sauvegardes, sessions
  Economy.lua                 ← multiplicateurs, transactions, snapshots
  MapBuilder.lua              ← toute la map (+ props, PNJ, éclairage)
  TrainingService.lua         ← canalisation + auto-canalisation
  ZoneService.lua             ← achat de zones + pads VIP
  PetService.lua              ← œufs, inventaire, équipement, visuels
  RebirthService.lua          ← ascensions + auras
  CombatService.lua           ← duels d'arène + projectiles + victoires
  MonetizationService.lua     ← gamepasses + ProcessReceipt idempotent
  LeaderboardService.lua      ← OrderedDataStore + panneau + diffusion
  TutorialService.lua         ← état du tutoriel + récompenses
StarterPlayer/StarterPlayerScripts/
  UIClient.client.lua         ← toute l'interface (HUD, fenêtres, éclosion…)
  InputClient.client.lua      ← visée PvP clic/tap
  TutorialClient.client.lua   ← dialogues, bannières, flèches du tutoriel
  EffectsClient.client.lua    ← animations, sons/ambiances, secousse, LowFx
```

### 12.3 Le registre de services (zéro dépendance circulaire)

AUCUN module de ServerScriptService ne `require` un autre module de
ServerScriptService. `Main.server.lua` : (1) détruit la map statique du mode
édition, (2) construit la map (`MapBuilder.Build()` → table de poignées),
(3) assemble le REGISTRE `services` (référence de chaque module + poignées
de map), (4) appelle `Init(services)` sur chaque service — le registre est
complet AVANT le premier Init, l'ordre des Init est donc libre et un cycle
est STRUCTURELLEMENT impossible. Les modules gardent le registre dans un
upvalue local et s'appellent entre eux via `services.X` à l'exécution.

### 12.4 Contrats des modules serveur (API normatives)

- **DataManager** : `GetProfile(player)→table|nil`, `CanSave(player)→bool`,
  `SaveProfile(player)→bool`, `SaveAll()`, `UpdateLeaderstats(player)`,
  `Init(services)`. Interdictions : jamais d'écriture pour une session
  temporaire ; itérations sûres (copier les clés avant tout yield).
- **Economy** : `AddMana(player, montant, compterAuClassement?)`,
  `SpendMana(player, montant)→bool`, `AddGems(player, montant,
  appliquerPass?)→montantFinal`, `SpendGems(...)→bool`,
  `GetPetMultiplier/GetRebirthMultiplier/GetPassManaMultiplier/
  GetGlobalMultiplier(player)`, `GetRebirthCost(player)`,
  `BuildSnapshot(player)→table`, `PushSnapshot(player)`.
- **TrainingService** : `Channel(player, cristal)→bool` (toutes les
  validations de 1.1), boucle d'auto-canalisation (ne cible QUE les zones
  autorisées), gestion du toggle Auto (refusé sans gamepass).
- **ZoneService** : achat séquentiel aux totems, téléportations VIP
  (vérification du pass à chaque toucher, anti-rebond 1,5 s).
- **PetService** : `GrantPet(player, petId, équiperSiPlace)→uid`,
  achat/tirage d'œuf, équipement (max 3), reconstruction des visuels.
- **RebirthService** : ascension (validation du coût, sacrifice TOTAL),
  `ApplyAura(player)` (paliers, recréée au respawn).
- **CombatService** : validation complète des sorts (2.5 et 6.2),
  projectile + onde de choc, fenêtre de victoire 10 s, +25 gemmes.
- **MonetizationService** : `OwnsPass(player, clé)→bool` (cache),
  requêtes avec 3 tentatives, effets des passes (octroi unique du familier
  mythique APRÈS chargement du profil), ProcessReceipt idempotent (7.4).
- **LeaderboardService** : publication des scores (sessions temporaires
  EXCLUES), lecture du top 10, rendu du panneau (médailles, avatars),
  diffusion aux clients, période 60 s.
- **TutorialService** : état persistant, hooks (`OnChanneled`,
  `OnEggOpened`, `OnZoneUnlocked`, `ShouldForceGem`), demandes client
  restreintes (dialogues/skip/replay), récompenses serveur.

### 12.5 La table des remotes (contrat client-serveur COMPLET)

Définis UNE SEULE FOIS dans Remotes.lua (le serveur crée, le client attend —
toute divergence de nom est structurellement impossible) :

| Remote | Type | Sens | Charge utile |
|---|---|---|---|
| Notify | Event | S→C | message: string, type: "info"/"succes"/"erreur"/"gemme" |
| ProfileChanged | Event | S→C | snapshot complet (12.6) |
| ChannelResult | Event | S→C | position du cristal, mana gagnée, gemmes gagnées |
| SpellImpact | Event | S→C | position d'impact (secousse+son côté victime) |
| TutorialState | Event | S→C | étape, {Channels, Goal} |
| LeaderboardData | Event | S→C (tous) | liste {UserId, Name, Score} du top 10 |
| CastSpell | Event | C→S | joueur ciblé |
| ToggleAuto | Event | C→S | booléen souhaité |
| TutorialAdvance | Event | C→S | nil (dialogue) / "skip" / "replay" |
| SetSettings | Event | C→S | {Music?, Sfx?, LowFx?} booléens uniquement |
| GetProfile | Function | C→S | () → snapshot |
| BuyEgg | Function | C→S | index d'œuf → ok, familier{Uid,Id,Name,Rarity,Multiplier} ou message |
| SetPetEquipped | Function | C→S | uid, booléen → ok, message |
| DoRebirth | Function | C→S | () → ok, message |

Le serveur TYPE-CHECK chaque argument entrant (type, bornes, existence) et
ignore silencieusement tout appel malformé. La canalisation, elle, ne passe
PAS par un remote : ClickDetector et ProximityPrompt livrent leurs
événements directement au serveur (surface d'attaque minimale), qui re-valide
tout de toute façon.

### 12.6 Le snapshot de profil (poussé au client)

`{ Mana, Gems, Rebirths, TotalMana, Zones (dict "1".."5" → true), Pets
(liste triée {Uid, Id, Name, Rarity, Multiplier, Equipped}), AutoTrain,
StarterPackOwned, Passes (dict clé→bool), PetMultiplier, RebirthMultiplier,
GlobalMultiplier, RebirthCost, CanSave, MaxEquippedPets, TutorialStep,
Settings {Music, Sfx, LowFx} }`. Poussé à chaque changement d'état ; le
client ne calcule JAMAIS un état de gameplay lui-même, il affiche ce
snapshot (les fenêtres lourdes ne se reconstruisent que visibles).

---

## SECTION 13 — DONNÉES, SAUVEGARDE ET FICHIER PLACE

### 13.1 Le schéma de profil (champ par champ, NORMATIF)

Un document par joueur, clé `Joueur_{UserId}`, DataStore
`ArcaneLegends_Profil_v1` :

| Champ | Type | Défaut | Rôle |
|---|---|---|---|
| Version | number | 1 | migrations futures |
| Mana | number | 0 | monnaie courante |
| Gems | number | 0 | monnaie rare |
| Rebirths | number | 0 | ascensions accomplies |
| TotalMana | number | 0 | score de classement (mana JOUÉE uniquement) |
| Zones | dict string→true | {"1"=true} | zones possédées (clés STRING) |
| Pets | dict string→{Id, Equipped} | {} | inventaire (uid = compteur en STRING) |
| PetCounter | number | 0 | générateur d'uid |
| AutoTrain | bool | false | état du toggle auto |
| StarterPackOwned | bool | false | unicité du pack |
| VoidLordGranted | bool | false | unicité du familier mythique |
| Purchases | dict string→true | {} | PurchaseId consommés (idempotence) |
| TutorialStep | number | 0 | 0..6 (6 = terminé) |
| TutorialChannels | number | 0 | progression de l'étape 1 |
| Settings | {Music, Sfx, LowFx} | true/true/false | réglages joueur |

CONTRAINTE JSON DATASTORE : toute clé de dictionnaire persisté est une
STRING (les clés numériques mixtes sont détruites par la sérialisation).
Un `reconcile` complète les champs manquants d'un vieux profil avec les
défauts (migration douce, jamais destructive).

### 13.2 Cycle de sauvegarde

Chargement à l'arrivée (GetAsync avec 3 tentatives, backoff 2 s/4 s/8 s) ;
autosauvegarde périodique toutes les 120 s (itération sur une COPIE des
clés : un joueur qui arrive pendant un yield ne doit jamais casser la
boucle) ; sauvegarde à la déconnexion ; sauvegarde de fermeture via
BindToClose (toutes les sessions en parallèle, attente bornée ~25 s,
comportement spécifique Studio). Écritures via UpdateAsync avec les mêmes
3 tentatives. leaderstats Roblox : « Mana » et « Gemmes » en StringValue
formatées, « Ascensions » en IntValue.

### 13.3 La session temporaire (protection absolue des données)

Si le CHARGEMENT échoue après tous les retries : le joueur reçoit un profil
neuf marqué NON-SAUVEGARDABLE, et il est prévenu clairement (« Connexion aux
sauvegardes impossible : session temporaire, ta progression ne sera PAS
enregistrée. »). Une session temporaire : n'écrit JAMAIS dans le DataStore
(on n'écrase pas de vraies données avec un profil vide) ; ne publie JAMAIS
au classement ; ne CONSOMME JAMAIS un achat Robux (NotProcessedYet → Roblox
représentera le reçu dans une session saine).

### 13.4 Classement persistant

OrderedDataStore `ArcaneLegends_ManaTotale_v1` : à chaque cycle de 60 s, le
serveur publie le TotalMana entier de chaque joueur connecté sauvegardable,
lit le top 10 (GetSortedAsync, pcall — en cas d'échec on garde l'affichage
précédent et on réessaie au cycle suivant), met à jour le panneau physique
et diffuse la liste aux clients.

### 13.5 Résilience générale

Chaque appel d'API à yield (DataStore, Marketplace, thumbnails, noms) est
enveloppé de pcall avec stratégie explicite : retry borné pour l'essentiel
(profils, gamepasses), dégradation silencieuse pour le cosmétique (avatar
absent → case vide ; nom introuvable → « Sorcier #id »). AUCUNE erreur non
gérée en console dans une partie normale : c'est un critère d'acceptation.

### 13.6 Le fichier place .rbxlx et la map pré-cuite

Le livrable inclut `ArcaneLegends.rbxlx`, généré par un script de build
(Python) qui : (1) insère chaque source Lua au bon endroit de l'arborescence
(Script/LocalScript/ModuleScript selon un suffixe de nommage), en XML
échappé (pas de CDATA : les `]]` du Lua sont piégeux) ; (2) exécute
MapBuilder HORS Roblox sur un stub de l'API (mini-implémentation
d'Instance/Vector3/CFrame avec vraies matrices de rotation) pour BAKER la
géométrie de la map en un dossier statique `MapStatique` dans le Workspace
du fichier — ainsi OUVRIR LE FICHIER MONTRE LE JEU, exigence héritée d'un
vrai retour utilisateur (« je l'ai ouvert et il n'y a rien ») ; (3)
s'auto-vérifie : XML bien formé, round-trip exact de chaque source, nombre
de parts bakées > seuil. Comme le bake et le runtime exécutent LE MÊME
MapBuilder avec le MÊME générateur déterministe (LCG maison, Section 3.2),
l'édition et le jeu sont identiques ; au lancement, Main détruit
`MapStatique` et reconstruit la map interactive par-dessus. Les matériaux
sans équivalent certain dans le sérialiseur XML dégradent vers un proche
(Basalt→Slate en édition seulement), documenté.

---

## SECTION 14 — SÉCURITÉ SERVEUR (liste des validations)

Le tableau suivant est un CONTRAT : pour chaque action, les contrôles
serveur à implémenter et tester. « → silencieux » = demande ignorée sans
message (anti-spam), « → message » = notification explicative.

**Canaliser** : profil chargé (→ silencieux) ; personnage et racine présents
(→ silencieux) ; distance ≤ portée+6 (→ silencieux) ; cooldown 0,35 s
(→ silencieux) ; zone possédée (→ message limité 1/2 s) ; VIP → gamepass
(→ silencieux). **Acheter une zone** : zone existante ; non déjà possédée
(→ message « déjà débloquée ») ; zone précédente possédée (→ message) ;
solde suffisant vérifié-et-débité atomiquement (→ message du manquant).
**Ouvrir un œuf** : index numérique valide ; solde ; débit AVANT tirage ;
remboursement si pool invalide. **Équiper** : uid string existant ; limite
3 ; idempotence (retirer un retiré = ok). **Ascension** : coût atteint
(→ message du manquant) ; sacrifice total ; jamais de multi-ascension par
double-clic (l'état est déjà remis à zéro). **Sort PvP** : cible instance
Player réelle ≠ soi ; les deux dans l'arène ; portée 45 ; cooldown 0,6 s ;
cible vivante ; dégâts plafonnés serveur. **Toggle auto** : gamepass
(→ message). **TutorialAdvance** : action ∈ {nil, "skip", "replay"} ;
dialogues seulement aux étapes 0/5. **SetSettings** : table ; seules les 3
clés booléennes connues sont copiées. **Achats** : Section 7.4 intégrale.
**Téléport VIP** : gamepass vérifié à CHAQUE toucher + anti-rebond.

Principes transversaux : le serveur ne fait JAMAIS confiance à une position,
un solde, un uid ou un booléen venu du client sans le re-vérifier ; les
erreurs de type sont ignorées silencieusement (pas d'oracle pour un
attaquant) ; aucune boucle serveur par frame (les animations sont client) ;
les visuels temporaires serveur (projectiles, ondes) sont détruits
systématiquement.

---

## SECTION 15 — PERFORMANCES

Budgets : ZÉRO boucle serveur par frame (les seules boucles serveur sont
l'autosave 120 s, l'auto-canalisation 0,6 s, le classement 60 s) ;
animations (cristaux, familiers, caméra) exclusivement client dans UNE
RenderStepped mutualisée ; particules d'ambiance plafonnées (≤ 8/s par
émetteur de zone) et désactivables (« réduire les effets ») ; fenêtres
lourdes reconstruites uniquement visibles ; textes volants et projectiles à
durée de vie courte et destruction garantie ; sons instanciés à la demande
et détruits après lecture ; préchargement des assets en tâche de fond sans
bloquer l'apparition. La map ~complète vise < 2 500 parts (bake compris).

---

## SECTION 16 — TESTS ET VÉRIFICATION AVANT LIVRAISON

1. **Syntaxe** : `luac -p` sur CHAQUE fichier .lua (Lua 5.1 strict).
2. **Tests logiques hors Roblox** (lua5.1) : conformité de Config au présent
   document (coûts, multiplicateurs, poids, pourcentages affichés),
   formules (gains, coûts d'ascension, dégâts et leurs bornes, formatage
   des nombres, LCG déterministe), distribution du tirage pondéré sur
   20 000 tirages (tolérance large mais réelle).
3. **Tests d'intégration sur stub** : le VRAI code serveur exécuté hors
   Roblox sur une mini-API (instances, signaux, DataStore mémoire à pannes
   simulables, horloge contrôlable) : chargement/reconcile/panne/session
   temporaire/round-trip complet de sauvegarde ; canalisation (cooldown,
   distance, verrou de zone, VIP, gemme forcée du tutoriel) ; achat de
   zones (séquence, soldes) ; œufs (débit, uid string, limite d'équipe) ;
   ascension (sacrifice total, coût suivant, aura) ; PvP (arène, portée,
   cooldown, dégâts exacts, victoire +25) ; ProcessReceipt (idempotence,
   joueur absent, session temporaire, pack dupliqué) ; tutoriel (toutes les
   transitions et récompenses, skip/replay) ; réglages (validation).
4. **Revue manuelle finale** : cohérence de la table des remotes des deux
   côtés ; nil-safety Character/HumanoidRootPart PARTOUT (helpers
   centralisés) ; zéro require croisé serveur ; chaque fallback d'asset
   déclenché quand l'ID = 0 ; textes français relus (accents corrects).
5. **Critères d'acceptation jouables** (à vérifier dans Studio) : la
   chronologie de la Section 1.2 se déroule comme écrite ; aucune erreur
   console en session normale ; le fichier ouvert montre la map ; F5 lance
   le jeu complet en ~1 s.

---

## SECTION 17 — LIVRAISON

Livrer : (1) tous les fichiers .lua organisés selon l'arborescence 12.2 ;
(2) `ArcaneLegends.rbxlx` prêt à ouvrir, map visible en édition, scripts en
place ; (3) le script de build du .rbxlx et les tests, rejouables ; (4) un
README d'installation pas à pas (ouvrir, jouer, publier, activer l'accès
API Studio pour les sauvegardes, renseigner les 8 IDs de monétisation et la
liste de courses d'assets avec mots-clés et rappel des licences, dépannage
« je ne vois rien » : Explorer, Output, ligne rouge) ; (5) DECISIONS.md
(toute décision prise en autonomie : contexte, choix, réversibilité) ;
(6) un rapport de session (ce qui est fait, testé, restant). Interdits de
livraison : IDs inventés, TODO silencieux, fichier non compilable, test
rouge, erreur console connue.

---

## SECTION 18 — ROADMAP POST-LANCEMENT (hors périmètre, pour ne PAS déborder)

Documenter sans implémenter : fusion de familiers en doublons (craft),
familiers « brillants » (variantes 1 %), quêtes journalières (3 objectifs,
gemmes), codes cadeaux, événements saisonniers (zone temporaire), succès/
badges Roblox, boutique d'auras cosmétiques en gemmes, échange entre joueurs
(avec toutes ses précautions), classement hebdomadaire en plus du global,
6e zone « Abîme Runique » (x3125, 50 M de mana).

---

## ANNEXE A — TOUS LES TEXTES FRANÇAIS DU JEU (catalogue normatif)

Notifications de succès : « Zone débloquée : “{zone}” (mana x{mult}) ! » ;
« Ascension {n} accomplie ! Bonus permanent : +{n×50} % de mana. » ;
« Gamepass “{nom}” activé. Merci ! » ; « Pack de Départ reçu : merci pour
ton soutien ! » ; « Le Seigneur du Vide (x3,5) a rejoint tes familiers ! » ;
« ✨ {familier} ({rareté}, x{mult}) rejoint tes familiers ! » ; « Bravo,
apprenti ! +10 mana offertes ! » ; « Voici 50 gemmes pour ton premier
œuf ! » ; « 🎓 Apprenti diplômé ! +25 gemmes ! » ; « Duel remporté contre
{joueur} ! +{gemmes} gemmes. »

Notifications d'information : « “{zone}” est déjà débloquée ! » ; « Duel
perdu contre {joueur}... Reviens plus fort ! » ; « Tutoriel passé. Bonne
aventure ! » ; « Familier équipé ! » ; « Familier retiré. »

Notifications d'erreur (toujours : le POURQUOI + le COMBIEN) : « Pas assez
de mana ! Il te faut {n} mana. » ; « Débloque d'abord “{zone}” ! » ; « Zone
verrouillée ! Débloque “{zone}” à son totem d'entrée. » ; « Pas assez de
gemmes ({n} nécessaires). » ; « Maximum 3 familiers équipés. » ; « Il te
faut {n} mana pour t'élever. » ; « L'auto-canalisation nécessite le gamepass
“Auto-Canalisation”. » ; « Le Sanctuaire VIP est réservé aux détenteurs du
gamepass “Sanctuaire VIP”. » ; « Ce gamepass n'est pas encore configuré (ID
manquant). » ; « Connexion aux sauvegardes impossible : session temporaire,
ta progression ne sera PAS enregistrée. » ; « Le serveur n'a pas répondu,
réessaie. » ; « Profil en cours de chargement... » ; « Requête invalide. » ;
« Cet œuf n'existe pas. » ; « Erreur de tirage, gemmes remboursées. »

Monde : « Arcane Legends » (fontaine) ; « Maître Arcanis » (PNJ) ;
« {emoji} {Zone} (x{mult}) » (panneaux de zones) ; « {Zone}\n{coût} Mana »
(totems, action : « Débloquer ({coût} Mana) ») ; « ⚔️ Arène des Duels —
Clique sur un adversaire pour lancer un sort ! » ; « Ici on s'entraîne entre
sorciers : on ne perd RIEN ! » ; « 👑 Sanctuaire VIP (x150) » ; « Sanctuaire
VIP » / « Retour au lobby » (pads) ; « 🏆 Top 10 — Mana totale » ;
« Canaliser » / « Cristal magique » (prompt E).

Interface : « Boutique Arcanique » ; onglets « Passes / Gemmes / Packs » ;
« Acheter » / « Possédé ✓ » / « Indisponible » ; « MEILLEURE OFFRE » /
« OFFRE UNIQUE » / « POPULAIRE » ; « Tu as déjà tout ! Merci 💜 » ;
« Familiers & Œufs » ; « Inventaire — {n}/3 équipés » ; « Aucun familier :
ouvre un œuf pour commencer ! » ; « Ouvrir » ; « Ascension Arcanique » ;
« Sacrifie TOUTE ta mana pour un pouvoir permanent. » ; « S'élever ! » /
« Sûr ? TOUTE ta mana sera sacrifiée ! » ; « Prochaine aura : {n}
ascensions » ; « Réglages » ; « Musique 🎵 » / « Effets sonores 🔊 » /
« Réduire les effets ✨ » ; « Revoir le tutoriel » ; « Comment jouer » et
ses cinq pages (Section 9.7) ; « Prochain objectif » ; « Auto : ON » /
« Auto : OFF » ; « Passer ▸ » ; « Continuer ! » ; « Génial ! » ; « équipé
automatiquement » ; « 🏆 Rang #{n} mondial ! ».

Dialogues du mage : étape 0 et étape 5, mot pour mot en Section 10.2.

## ANNEXE B — CHECKLIST FINALE D'ACCEPTATION (à cocher avant de livrer)

Boucle : les 3 modes d'interaction cristal fonctionnent ; gains conformes au
tableau 2.1 ; gemmes ~5 % ; textes volants ; pop du HUD. Zones : 5 coûts et
multiplicateurs exacts ; achat séquentiel ; verrou serveur des gains ;
totems clairs. Familiers : 3 œufs, 14 familiers, poids exacts, pourcentages
AFFICHÉS, tirage serveur, limite 3, multiplicateur d'équipe conforme,
cinématique skippable, visuels qui suivent avec flottement. Ascension :
coût 10 000×4^n, sacrifice TOTAL, +50 %/n, auras 1/3/6/10, double
confirmation. PvP : les 7 validations serveur, dégâts bornés 5-50, +25
gemmes, aucune perte au vaincu, panneau rassurant. Classement : top 10
persistant, médailles, avatars en pcall, refresh 60 s, mana achetée exclue,
sessions temporaires exclues. Monétisation : 5 passes + 3 produits, IDs à 0
proprement dégradés, ProcessReceipt idempotent testé, pack unique, retry
gamepass, octroi mythique après chargement. Tutoriel : 6 étapes, textes
exacts, flèche/surbrillance, gemme forcée, récompenses serveur, skip/replay,
persistance. Accessibilité : Section 11 intégralement. Technique : luac -p
100 %, tests verts, zéro require croisé serveur, snapshot complet, remotes
conformes au tableau 12.5, .rbxlx avec map visible, README complet.

## ANNEXE C — VARIANTES RAPIDES DU PROMPT

- **Tu as déjà des assets** : colle tes IDs à la fin (« Remplis le manifeste
  avec : crystal_zone1=123456… ») — ils remplacent les 0 dès la génération.
- **Changer le thème** : remplace le lexique (sorcier/mana/cristaux) et les
  Sections 3 (ambiances) et 4 (bestiaire) ; TOUTE la mécanique, la sécurité
  et l'interface restent valables telles quelles.
- **Durcir/adoucir la monétisation** : ne touche QUE la Section 7 en
  respectant ses invariants chiffrés (accélération, jamais de plafond).
- **Itérer sur l'existant** : « Pars du code ci-joint et applique uniquement
  les Sections {X} » — le document est découpé pour ça.

*Fin du prompt maître. Copier depuis « SECTION 0 » jusqu'ici inclus.*

---
---

# PARTIE II — DOSSIER D'EXÉCUTION (annexes techniques du prompt)

Cette seconde partie fait toujours partie du prompt : elle détaille les
recettes de fabrication, le plan de test exhaustif, l'ordre de construction
et les réponses aux ambiguïtés prévisibles, pour garantir le « zéro
question » de la Section 0.

## ANNEXE D — RECETTES DES FALLBACKS PROCÉDURAUX (pièce par pièce)

Chaque recette liste les parts à assembler quand l'ID du manifeste vaut 0.
Conventions : dimensions en studs (X × Y × Z) ; « soudé » = WeldConstraint à
la pièce principale ; tout est non-collidable sauf mention ; ancré dans le
monde, non-ancré + massless pour les familiers suiveurs.

### D.1 Cristal de mana (fallback commun aux 6 slots)
Part unique 3 × 6 × 3, matériau Néon, couleur signature de la zone, posée
inclinée : rotation (15°, 40° × index du cristal, 10°) — l'index varie
l'orientation, l'anneau paraît naturel. PointLight (portée 14, luminosité
1,5) + émetteur d'étincelles (3/s, taille 0,35, vie 0,8-1,4 s). Socle fourni
hors fallback : dalle d'ardoise 4,5 × 1 × 4,5 sous chaque cristal.

### D.2 Familier générique (fallback des 14 slots)
Corps : boule 1,8 × 1,8 × 1,8, SmoothPlastic, couleur de RARETÉ. Deux yeux :
ellipsoïdes 0,32 × 0,42 × 0,18, presque noirs (25 20 40), posés vers l'avant
(−Z) à ±0,38 en X, +0,25 en Y, soudés. Accessoire par rareté, soudé : Rare →
crête dorsale néon 0,2 × 0,7 × 1 inclinée ; Épique → deux cornes néon crème
0,22 × 0,66 × 0,22 écartées de ±20° ; Légendaire → couronne = cylindre néon
doré 0,3 × 1 × 1 couché au sommet ; Mythique → halo = cylindre néon rubis
0,14 × 2,4 × 2,4 flottant au-dessus, transparence 0,25. Épique et au-delà :
traînée de particules couleur rareté (6/s, taille 0,22). Étiquette
BillboardGui « {emoji} {Nom} » à +1,6 stud, max 60 studs.

### D.3 Œuf (3 slots)
Coquille : boule étirée 2 × 2,6 × 2 SmoothPlastic — teintes : Novice crème
verdâtre (220 235 200), Mystique mauve (190 160 255), Céleste bleu
(140 190 255). Motif : petite boule néon blanche 0,7 × 0,7 × 0,4 semi-
transparente incrustée en haut à droite, soudée.

### D.4 Arbre rond (Prairie) — tronc cylindre 5 × 1,6 (couché puis dressé,
bois brun 105 72 50), feuillage boule 6,5 × 6 × 6,5 Grass vert franc
(90 175 95) posée au sommet. Collision : tronc seulement.

### D.5 Fleur lumineuse — tige 0,25 × 1,6 × 0,25 verte ; corolle boule 0,9
néon mauve clair (190 150 255) + PointLight douce (8, 0,8).

### D.6 Pierre runique — bloc d'ardoise 2,4 × 3,2 × 1,6 légèrement penché
(4°, 0°, −6°) ; rune = plaque néon cyan 0,6 × 1,4 × 0,1 plaquée sur la face
avant, même inclinaison.

### D.7 Champignon géant (Forêt) — pied cylindre 3,4 × 1,2 crème
(226 218 200) ; chapeau demi-boule 3,6 × 2 × 3,6 néon turquoise
(90 255 190) + PointLight (10, 1).

### D.8 Saule enchanté — tronc cylindre 6,5 × 2 brun foncé ; trois boules de
feuillage 4,6 × 3,4 × 4,6 vert sourd (60 140 105) disposées en trèfle
décalé autour du sommet (rayon 1,8, hauteurs alternées) : silhouette
« pleureur » sans mesh.

### D.9 Colonne brisée (Cryptes) — fût cylindre 5,4 × 1,8 pierre claire
(150 148 160) ; chapiteau 2,2 × 0,8 × 2,2 posé de travers au sommet
(9° et 20° de biais) : la ruine se lit immédiatement.

### D.10 Stèle — dalle 2 × 2,6 × 0,5 ardoise penchée de 3° ; sommet arrondi
= cylindre couché de même largeur, soudé au sommet.

### D.11 Brasero — vasque cylindre 1,2 × 2,2 métal sombre sur pied cylindre
1,8 × 0,6 ; flamme boule 1,2 néon VERT ÉMERAUDE (120 255 160) transparence
0,25 + PointLight chaude (14, 1,4) + émetteur 8/s vitesse 2,5.

### D.12 Pic de basalte (Volcan) — monolithe 2,4 × 5,4 × 2,4 anthracite
(45 42 48) Slate incliné (7°, 15°, −6°) ; braise = pépite néon orange
0,8 × 0,5 × 0,8 incrustée près du sommet.

### D.13 Mare de lave — disque cylindre couché 0,4 × 6,5 × 6,5 néon orange
(255 110 40) affleurant le sol + PointLight (16, 1,6) + braises montantes
(5/s, vie 0,8-1,6 s). JAMAIS de dégâts au contact.

### D.14 Îlot astral — plaque 5 × 1,4 × 5 mauve minéral (96 88 170) Glacier,
flottant à ~8 studs (ancré), légèrement incliné ; éclat néon bleu ciel 1 × 2
planté dessus + PointLight.

### D.15 Anneau planétaire — cylindre creux simulé : cylindre néon
translucide 0,4 × 9 × 9 (transparence 0,55) dressé et incliné de 28°,
cœur = boule Glacier 3,2 au centre + PointLight bleutée.

### D.16 Torche d'arène — manche cylindre 4,6 × 0,5 bois ; flamme boule
1 × 1,3 néon orange transparence 0,2 + PointLight (16, 1,5) + émetteur 10/s.

### D.17 Statue de sorcier — socle 3 × 1,2 × 3 ; robe 1,8 × 3,2 × 1,4 ;
tête boule 1,1 ; chapeau cylindre 0,5 × 1,7 couché en bord de chapeau ;
orbe néon violette 0,8 dans la « main » (décalée +1,3 X) + PointLight.
Le tout pierre claire (150 148 160), l'orbe seule est colorée.

### D.18 Maître Arcanis (PNJ) — robe-bloc 2 × 3 × 1,6 violette Fabric
(90 70 180) ; tête boule 1,3 chair claire ; barbe bloc blanc 0,9 × 1 × 0,4
sous le visage ; chapeau : bord cylindre 0,3 × 2,2 + pointe bloc pivoté à
45° au-dessus, violet sombre ; bâton cylindre 4,4 × 0,35 planté à sa droite,
gemme néon turquoise 0,8 au sommet + PointLight + étincelles discrètes.
Étiquette « Maître Arcanis ». Orienté vers le spawn (rotation ~210°).

### D.19 Portail VIP — deux montants marbre doré 1,4 × 10, linteau 11 × 1,4,
voile ForceField 8 × 8,6 × 0,4 doré pâle transparence 0,3 + émetteur de
volutes 12/s : l'arche enjambe le pad doré, tournée de 45° vers le lobby.

### D.20 Projectile de sort — boule néon violette 1,4 (170 110 255) +
PointLight (10) + traînée 40/s vie 0,2-0,4 s ; interpolation rectiligne à
90 studs/s ; à l'arrivée : gonfle à 3 studs, transparence 0,4, disparaît en
0,12 s. Onde de choc : anneau-cylindre néon mauve couché au sol qui grandit
de 2 à ~15 studs de diamètre en 8 pas de 0,04 s en s'estompant.

## ANNEXE E — PLAN DE TESTS DÉTAILLÉ (cas par cas)

Organisation : deux fichiers exécutables hors Roblox avec lua5.1 (logique
pure, puis intégration sur stub d'API) + une checklist Studio manuelle.
Chaque cas liste : action → attendu. Les valeurs sont NORMATIVES.

### E.1 Tests logiques (Config & formules)
1. Les 5 zones existent, coûts (0, 250, 5 000, 100 000, 2 000 000), mults
   (1, 5, 25, 125, 625) ; VIP = 150 ; œufs (50, 500, 5 000).
2. Chaque pool d'œuf ne référence que des familiers existants, poids > 0 ;
   somme des % affichés = 100 (à l'arrondi près, affichage sans tromperie).
3. Mults familiers ∈ [1,2 ; 3,5] ; Seigneur du Vide = 3,5 Mythique ; Titan
   = 3,2 Légendaire (meilleur gratuit) ; écart payant/gratuit ≤ 10 %.
4. formatNumber : 0→«0», 999→«999», 1000→«1.0k», 100000→«100k»,
   2000000→«2.0M», 3,4e9→«3.4Md», négatifs corrects.
5. Tirage pondéré : pool vide→nil ; poids unique→toujours lui ; 20 000
   tirages sur l'Œuf Novice → chaque familier dans ±40 % de son espérance.
6. clamp bornes ; deepCopy indépendante ; LCG : même graine → même série,
   graines ≠ → séries ≠ ; rectangle d'arène : centre/bord dedans, +1 dehors.
7. Coûts d'ascension : n=0→10 000, 1→40 000, 3→640 000, croissance ×4.
8. Dégâts : mana 0→5 ; 1 000→5+1000^0,35 exact ; 1e15→50 ; croissance
   monotone ; MÊME ORDRE de calcul que la production.
9. Mult d'équipe : {}→1 ; {1,2}→1,2 ; {3,2 ; 3,2 ; 3,2}→7,6 ; gain endgame
   du tableau 2.1 → 59 250 exactement.

### E.2 Tests d'intégration — profils et sauvegarde
10. Nouveau joueur → profil au modèle, zone 1 possédée, leaderstats créés,
    snapshot poussé. 11. Profil partiel stocké → reconcile complète sans
    écraser l'existant. 12. Panne de chargement (échecs > retries) →
    session temporaire : CanSave=false, joueur notifié « temporaire »,
    SaveProfile REFUSE d'écrire (compteur d'écritures inchangé).
13. Déconnexion → écriture du profil exact ; reconnexion → mana, zones
    (clés string), familiers, achats, ascensions, tutoriel, réglages
    persistés à l'identique. 14. Panne d'écriture → 3 tentatives, échec
    propre sans exception. 15. BindToClose → toutes les sessions écrites.
16. Autosave pendant qu'un joueur arrive en plein yield → aucune erreur
    d'itération (clés copiées avant la boucle).

### E.3 Tests d'intégration — canalisation et zones
17. Canalisation valide → +1 mana (zone 1), ChannelResult émis. 18. Spam
    immédiat → refus silencieux ; après 0,35 s simulé → accepté. 19. Cristal
    à 110 studs → refus (anti-triche distance). 20. Cristal zone 2 non
    achetée (joueur à portée) → refus + notification « verrouillée »,
    limitée à 1/2 s. 21. Cristal VIP sans pass → refus ; avec pass → gain
    150 × mults exact (même ordre de calcul). 22. Achat zone 2 avec 300
    mana → possédée, solde 50 ; zone 4 sans la 3 → refus « Débloque
    d'abord » ; solde insuffisant → refus + montant. 23. Gain zone 2 après
    achat = ×5. 24. Auto-canalisation : OFF sans pass (toggle refusé +
    notification) ; ON avec pass ; la boucle ne cible JAMAIS un cristal de
    zone non possédée (test : joueur posté en zone verrouillée avec un
    cristal autorisé plus loin → c'est l'autorisé qui est canalisé).

### E.4 Tests d'intégration — familiers et ascension
25. Œuf sans gemmes → refus + message. 26. Achat : 5 000→4 950 gemmes,
    familier du pool, uid STRING, premier familier auto-équipé. 27. Index
    d'œuf invalide (99, "novice") → refus type-check. 28. 4 familiers →
    3 équipés max ; équiper le 4e → refus « Maximum 3 » ; retirer un
    retiré → ok idempotent. 29. GetPetMultiplier = 1 + Σ(mult−1) exact.
30. Ascension à 9 999 mana → refus ; à 250 000 → mana=0 (TOUT sacrifié),
    Rebirths=1, mult 1,5, coût suivant 40 000, aura palier 1 présente sur
    le personnage. 31. Paliers d'aura : 3→style 2, 6→style 3, 10→style 4 +
    anneau au sol ; recréée après respawn simulé.

### E.5 Tests d'intégration — PvP
32. Attaquant hors arène → cible intacte. 33. Les deux dans l'arène,
    mana 1 000 → dégâts exacts clamp(5+1000^0,35). 34. Second sort
    immédiat → ignoré (cooldown) ; après 0,6 s simulé → accepté.
35. Auto-attaque → refus. 36. Cible à 99 studs dans l'arène → refus
    (portée 45). 37. Victime achevée → +25 gemmes à l'attaquant (fenêtre
    10 s), messages des deux côtés ; au-delà de 10 s → aucun crédit.
38. Cible non-Player (table forgée) → refus type-check silencieux.

### E.6 Tests d'intégration — monétisation
39. ProcessReceipt : produit gemmes → crédité SANS pass x2 ; MÊME
    PurchaseId rejoué → PurchaseGranted sans double crédit ; l'historique
    contient la clé STRING. 40. Pack de Départ → +5 000 mana, +200 gemmes,
    +1 familier exclusif, StarterPackOwned ; TotalMana INCHANGÉE (mana
    achetée hors classement) ; pack dupliqué → mana+gemmes recréditées,
    JAMAIS de 2e familier. 41. Joueur absent → NotProcessedYet. 42. Session
    temporaire → NotProcessedYet (achat non consommé). 43. ProductId
    inconnu → NotProcessedYet + warning. 44. Gamepass : cache vide → false ;
    simulation d'achat en jeu → cache à jour + effets appliqués ; VoidLord
    → familier mythique accordé UNE fois (2e activation → aucun doublon).
45. API Marketplace en panne 2 fois puis ok → le pass est bien détecté
    (retry ×3).

### E.7 Tests d'intégration — tutoriel et réglages
46. Nouveau joueur : étape 0 ; TutorialAdvance() → 1. 47. 5 canalisations →
    +10 mana, étape 2, compteur poussé à chaque pas (1/5…). 48. Étape 2 :
    la canalisation suivante force la gemme (chance 100 %) → +50 gemmes
    offertes, étape 3. 49. Achat d'œuf → étape 4. 50. Déblocage zone 2 →
    étape 5 ; TutorialAdvance() → +25 gemmes, étape 6. 51. Rejouer :
    TutorialAdvance("replay") → étape 0, compteur 0 ; skip → étape 6 sans
    récompense. 52. TutorialAdvance depuis l'étape 3 (triche) → ignoré.
53. Déconnexion à l'étape 4 → reprise à 4. 54. SetSettings {Music=false} →
    persisté ; {Music="oui", Autre=1} → clés invalides ignorées, rien ne
    casse. 55. La gemme forcée ne se déclenche QU'À l'étape 2 (à l'étape 6,
    ~5 % standard sur un grand échantillon).

### E.8 Checklist Studio (manuelle, avant publication)
56. Ouvrir le .rbxlx : la map est VISIBLE (lobby, 5 zones, arène, île VIP,
    panneau). 57. Explorer : 20 scripts en place, zéro doublon. 58. F5 : la
    map interactive remplace la statique en ~1 s, Output affiche la ligne
    d'initialisation, ZÉRO erreur rouge. 59. Dérouler la chronologie 1.2
    intégralement au clavier-souris PUIS en émulation tactile. 60. Test à
    2 joueurs (Clients and Servers) : duel complet, classement, familiers
    visibles croisés. 61. Couper le son : tout reste compréhensible.
62. Activer « réduire les effets » : particules d'ambiance stoppées.
63. Vérifier chaque bouton « Indisponible » avec IDs à 0 → aucun crash.

## ANNEXE F — ORDRE DE CONSTRUCTION EN DOUZE JALONS (avec « fini » défini)

Construire dans CET ordre : chaque jalon laisse le projet compilable,
testable et committable (un commit atomique par jalon minimum).

**Jalon 1 — Socle partagé.** Config complet (toutes les constantes du
document), Util (formatage, clamp, copie, tirage pondéré, LCG, aides
nil-safety, itération joueurs), Remotes (les 14 entrées du tableau 12.5).
Fini quand : luac -p passe, les tests logiques E.1 passent.

**Jalon 2 — Données.** DataManager intégral (schéma 13.1, cycle 13.2,
session temporaire 13.3). Fini quand : E.2 passe (pannes simulées incluses).

**Jalon 3 — Économie.** Economy (transactions, multiplicateurs, snapshot,
réglages). Fini quand : les calculs d'E.1/E.4 recoupent la production.

**Jalon 4 — Manifeste et chargeur.** AssetManifest (tous les slots des
Sections 8.2-8.4, IDs à 0), AssetLoader (mesh/son/icône + préchargement),
PetModels (D.2, D.3). Fini quand : chaque `createMesh` avec ID 0 rend le
fallback attendu (test unitaire sur stub).

**Jalon 5 — La map.** MapBuilder intégral (Section 3 + recettes D.4-D.19),
placements LCG, éclairage 3.11. Fini quand : le build sous stub produit
> 400 parts nommées, zones/arène/VIP/panneau présents (test automatisé).

**Jalon 6 — Gameplay noyau.** TrainingService, ZoneService, RebirthService.
Fini quand : E.3 (17-24) et E.4 (30-31) passent.

**Jalon 7 — Familiers.** PetService complet (tirage, équipement, visuels
par contraintes). Fini quand : E.4 (25-29) passe.

**Jalon 8 — PvP.** CombatService complet (validations, projectile, onde,
victoires). Fini quand : E.5 passe.

**Jalon 9 — Monétisation & classement.** MonetizationService (7.4),
LeaderboardService (13.4). Fini quand : E.6 passe.

**Jalon 10 — Tutoriel.** TutorialService + hooks dans Training/Zone/Pet.
Fini quand : E.7 passe.

**Jalon 11 — Client.** UIKit puis UIClient (Section 9 écran par écran),
InputClient, TutorialClient, EffectsClient, ArrowGuide. Fini quand : revue
manuelle de cohérence des remotes + parcours Studio 1.2 complet.

**Jalon 12 — Livraison.** Script de build .rbxlx avec bake de la map
(13.6), README, DECISIONS, rapport, checklist Annexe B cochée. Fini quand :
E.8 est validée et TOUT le dépôt est poussé.

## ANNEXE G — FAQ DE CONCEPTION (les ambiguïtés déjà tranchées)

**G.1 — L'Ascension réinitialise-t-elle les zones ?** Non. Le texte dit
« sacrifier TOUTE sa mana » : lecture littérale, on ne prend QUE la mana.
Les zones, familiers, gemmes restent. (Plus doux, adapté au tout-âge.)

**G.2 — L'achat des zones est-il libre ou séquentiel ?** Séquentiel (la
zone n exige la n−1) : c'est le standard du genre et les coûts exponentiels
le supposent. Le message d'erreur nomme la zone manquante.

**G.3 — La mana du Pack de Départ compte-t-elle au classement ?** NON,
jamais : le classement mesure la mana gagnée EN JOUANT. C'est l'invariant
« accélération, pas domination » appliqué au prestige.

**G.4 — Que se passe-t-il si un joueur achète deux fois le Pack UNIQUE ?**
L'interface l'empêche ; si un achat force le passage quand même, on
recrédite mana et gemmes (l'acheteur n'est pas volé) mais JAMAIS un second
familier exclusif. Documenté en commentaire dans le code.

**G.5 — Un doublon de familier a-t-il une valeur ?** En v2 : il occupe
l'inventaire, point (pas de fusion — c'est en roadmap). Les doublons de
familiers À plus fort multiplicateur restent utiles : on équipe les 3
meilleurs, doublons compris (3 × Titan est LA composition gratuite optimale).

**G.6 — Le PvP consomme-t-il de la mana ?** Non. La mana ne sert qu'aux
zones et aux Ascensions ; en duel elle ne fait que déterminer les dégâts
(plafonnés). Perdre un duel ne coûte RIEN.

**G.7 — Que voit un joueur non-VIP du Sanctuaire ?** L'île au loin, le
portail doré et le pad au lobby. Le pad lui explique le gamepass et ouvre
l'invite d'achat. Aucune frustration cachée : tout est annoncé.

**G.8 — Pourquoi Part+SpecialMesh plutôt que MeshPart pour les assets ?**
Parce que MeshPart.MeshId n'est PAS modifiable à l'exécution : impossible
d'injecter les IDs du manifeste au runtime. SpecialMesh l'est. C'est LE
détail technique qui rend le manifeste-avec-fallback viable.

**G.9 — Pourquoi un LCG maison plutôt que Random.new(graine) pour les
props ?** Parce que le bake du .rbxlx s'exécute HORS Roblox : seul un
générateur écrit en Lua pur garantit la même map en édition et au runtime.

**G.10 — Le tutoriel doit-il bloquer le reste du jeu ?** Non, jamais : tout
reste utilisable pendant le tutoriel. Les étapes se valident même si le
joueur prend de l'avance (il peut acheter la zone 2 avant l'étape 4 : les
étapes se résolvent en cascade dès que leurs conditions sont vraies).

**G.11 — Faut-il un anti-AFK ou un kick d'inactivité ?** Non pour la v2.
L'auto-canalisation est un produit payant assumé ; Roblox gère déjà la
déconnexion des inactifs de longue durée.

**G.12 — Le chat, les emotes, les social features ?** Hors périmètre v2
(chat Roblox natif suffit). Ne rien désactiver, ne rien ajouter.

**G.13 — Où vivent les textes français ?** Les notifications et libellés
sont dans les modules qui les émettent, MAIS tout texte cité en Annexe A
est normatif mot pour mot : la relecture finale compare le jeu à l'annexe.

**G.14 — Combien de joueurs par serveur ?** Défaut Roblox (généralement
50 max, laisser le réglage du place). Tous les systèmes ci-dessus sont
O(joueurs) par cycle, sans boucle par frame serveur : ça tient largement.

**G.15 — Faut-il du StreamingEnabled ?** Non pour cette taille de map
(< 2 500 parts). Le laisser désactivé simplifie flèches, téléportations et
recherche de cibles du tutoriel.

**G.16 — Quid des joueurs revenant d'une v1 sans champs tutoriel ?** Le
reconcile crée TutorialStep=0 : ils verront l'accueil une fois. Deux clics
pour re-diplômer, cadeaux mineurs re-versés : accepté, documenté (le coût
d'une migration fine ne vaut pas 75 gemmes par vétéran).

**G.17 — Peut-on canaliser plusieurs cristaux en même temps ?** Non : le
cooldown serveur de 0,35 s est GLOBAL par joueur, pas par cristal. L'auto
respecte le même cooldown via son intervalle de 0,6 s.

**G.18 — Les familiers d'un joueur gênent-ils les autres ?** Non-collidables
et massless : ils ne poussent rien. Leurs étiquettes disparaissent à 60
studs pour ne pas polluer l'écran des autres.

**G.19 — Le mage a-t-il d'autres dialogues ?** En v2 il n'a que les deux
dialogues du tutoriel + son étiquette. (Roadmap : dialogues d'accueil du
jour, indices de zones.) Interagir avec lui hors tutoriel ne fait rien —
c'est un décor bienveillant, pas un menu caché.

**G.20 — Sur quoi trancher ce que ce document ne couvre pas ?** Sur les
piliers de la Section 0.2, dans cet ordre : boucle agréable > clarté
enfant > serveur autoritaire > beauté sans dépendance > aucune perte. Et
consigner CHAQUE arbitrage dans DECISIONS.md.

## ANNEXE H — LE STUB D'API POUR LES TESTS (spécification)

Pour exécuter le vrai code serveur hors Roblox (Annexe E), implémenter un
mini-runtime Lua 5.1 : Instance.new générique (arbre parent/enfants,
FindFirstChild/WaitForChild/GetChildren/GetDescendants/Destroy/IsA,
attributs Set/GetAttribute, signaux usuels pré-câblés Touched/Triggered/
MouseClick/Died/CharacterAdded) ; Vector3 complet (+, −, ×scalaire,
Magnitude, Unit) ; CFrame avec position ET matrice de rotation (new, Angles,
multiplication — nécessaire au bake) ; Color3/UDim2/Enum-mémoïsé/Random ;
task.spawn en coroutines (les boucles infinies des services se garent sur
un task.wait qui yield ; dans le thread principal, task.wait AVANCE une
horloge factice contrôlable — indispensable pour tester cooldowns et
BindToClose sans blocage) ; game:GetService servant : Players factice
(fabrique de joueurs avec personnage, signaux Added/Removing),
DataStoreService mémoire À PANNES SIMULABLES (compteurs d'appels, N échecs
programmés), MarketplaceService factice (possession programmable, signal
d'achat, ProcessReceipt assignable), RunService/CollectionService/Lighting/
Workspace minimaux. Le module Remotes est REMPLACÉ par des faux
enregistreurs (chaque FireClient est capturé et assertable). Ce stub est un
OUTIL DE TEST : il vit dans tests/, jamais dans le jeu.

## ANNEXE I — GLOSSAIRE (pour lecteurs non-Roblox)

**Studs** : l'unité de distance Roblox (~30 cm visuels). **Part** : brique
3D primitive. **SpecialMesh** : composant qui remplace la forme d'une Part
par un mesh téléchargé. **ProximityPrompt** : invite contextuelle « appuie
sur E ». **ClickDetector** : rend une Part cliquable/tapable.
**RemoteEvent/RemoteFunction** : canaux client↔serveur (sans/avec réponse).
**DataStore / OrderedDataStore** : base clé-valeur persistante / sa variante
triée pour les classements. **Gamepass** : achat permanent ; **developer
product** : achat consommable rejouable ; **ProcessReceipt** : le rappel
serveur qui « encaisse » un produit. **leaderstats** : le petit tableau des
joueurs en haut à droite de Roblox. **BindToClose** : dernier rappel avant
l'arrêt du serveur (sauvegardes !). **ViewportFrame** : fenêtre d'interface
qui affiche un objet 3D. **Billboard/SurfaceGui** : interfaces flottantes /
plaquées sur une surface. **CFrame** : position + orientation.
**Highlight** : surbrillance d'objet. **Luau** : le Lua de Roblox (notre
code s'en tient au sous-ensemble Lua 5.1 vérifiable).

---

*FIN DU PROMPT MAÎTRE ULTRA-DÉTAILLÉ — copier l'intégralité du document,
de la Section 0 à cette ligne incluse.*

---

## ANNEXE J — GABARITS D'ÉCRANS (wireframes texte normatifs)

Repères : `[ ]` bouton, `( )` pastille/icône, `▓` barre de progression,
`—` séparateur. Les proportions sont exprimées pour un écran PC 1280 × 720 ;
en mobile, appliquer la Section 9.9.

### J.1 HUD en jeu (rien d'ouvert)

```
┌──────────────────────────────────────────────────────────────┐
│ ┌Objectif─────────┐   (⚡ 12,4k) (💎 231) (🔥 x3,30)          │
│ │🎯 Débloque les  │        🏆 Rang #7 mondial !     ┌Notifs─┐ │
│ │Cryptes Oubliées │                                 │ toast │ │
│ │▓▓▓▓▓░░░ 3,2k/5k │                                 └───────┘ │
│ └─[Flèche: ON]────┘                                           │
│  (🛒)                                                         │
│  Boutique                                                     │
│  (🐾)                        [ MONDE 3D ]                     │
│  Familiers                                                    │
│  (✨)                                                         │
│  Ascension                                        +2 mana ↑   │
│  (🔁)  ← vert si ON                                (texte     │
│  Auto                                               volant)   │
│  (⚙️) (❓)                                                    │
└──────────────────────────────────────────────────────────────┘
```

### J.2 Fenêtre Boutique (onglet Passes actif)

```
┌─ 🛒 Boutique Arcanique ────────────────────────────── [✖] ─┐
│  [ Passes ]  [ Gemmes ]  [ Packs ]     ← onglets           │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ (⚡) x2 Mana                                          │   │
│ │      Double toute la mana canalisée.     [ Acheter ]  │   │
│ ├──────────────────────────────────────────────────────┤   │
│ │ (💎) x2 Gemmes                          [ Possédé ✓ ] │   │
│ ├──────────────────────────────────────────────────────┤   │
│ │ (🔁) Auto-Canalisation                   [ Acheter ]  │   │
│ ├──────────────────────────────────────────────────────┤   │
│ │ (👑) Sanctuaire VIP                      [ Acheter ]  │   │
│ ├──────────────────────────────────────────────────────┤   │
│ │ (👁️) Seigneur du Vide                [ Indisponible ] │   │
│ └──────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

### J.3 Fenêtre Familiers

```
┌─ 🐾 Familiers & Œufs ──────────────────────────────── [✖] ─┐
│ ┌Œuf Novice──┐ ┌Œuf Mystique─┐ ┌Œuf Céleste──┐             │
│ │  [3D œuf]  │ │   [3D œuf]  │ │   [3D œuf]  │             │
│ │   50 💎    │ │    500 💎   │ │   5 000 💎  │             │
│ │ Lueur 50 % │ │ Sylv. 45 %  │ │ Djinn 50 %  │  ← chances  │
│ │ F.Fol 30 % │ │ Gard. 35 %  │ │ Licor. 35 % │    exactes  │
│ │ Salam 15 % │ │ Chim. 15 %  │ │ Titan 15 %  │             │
│ │ Golem 4 %  │ │ Drag.  5 %  │ │             │             │
│ │ Phénix 1 % │ │             │ │             │             │
│ │ [ Ouvrir ] │ │ [ Ouvrir ]  │ │ [ Ouvrir ]  │             │
│ └────────────┘ └─────────────┘ └─────────────┘             │
│ — Inventaire — 2/3 équipés ——————————————————————           │
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                │
│ │[3D pet]│ │[3D pet]│ │[3D pet]│ │[3D pet]│  ← carte :     │
│ │ Titan  │ │ Dragon │ │ Lueur  │ │ Lueur  │    fond teinté │
│ │ ⭐Lég. │ │ 🐉Lég. │ │ Commun │ │ Commun │    rareté,     │
│ │ x3,2 ✓│ │ x2,6 ✓│ │ x1,2   │ │ x1,2   │    ✓=équipé    │
│ └────────┘ └────────┘ └────────┘ └────────┘                │
└────────────────────────────────────────────────────────────┘
```

### J.4 Cinématique d'éclosion (phase de révélation)

```
┌──────────────── (fond assombri 40 %) ─────────────────┐
│               ╲   rayons rotatifs or   ╱              │
│                 ┌───────────────────┐                 │
│                 │   [3D familier]   │                 │
│                 │   PHÉNIX MINEUR   │                 │
│                 │   ★ Légendaire ★  │                 │
│                 │     mana x2,2     │                 │
│                 │ équipé automatique│                 │
│                 └───[ Génial ! ]────┘                 │
│        (un clic n'importe où passe la scène)          │
└────────────────────────────────────────────────────────┘
```

### J.5 Écran d'Ascension

```
┌─ ✨ Ascension Arcanique ───────────────────────────── [✖] ─┐
│   Ascensions accomplies : 2      Bonus actuel : +100 %     │
│   Multiplicateur global  : x3,30  →  x3,80 après           │
│   Coût : 160k mana   (⚡ tu as 214k)                        │
│   ⚠ TOUTE ta mana sera sacrifiée                           │
│   Prochaine aura : 3 ascensions  ▓▓▓▓▓▓░░░ 2/3             │
│              [        S'élever !        ]                  │
│   (1er clic → « Sûr ? TOUTE ta mana sera sacrifiée ! »)    │
└────────────────────────────────────────────────────────────┘
```

## ANNEXE K — PARCOURS JOUEURS TYPES (user stories d'acceptation)

**K.1 Le petit frère (8 ans, ne lit pas).** Il suit la flèche verte, clique
le cristal qui brille, entend le carillon, voit « +1 » monter. En dix
minutes il a un familier et la Forêt, SANS avoir lu une phrase complète.
Critère : aucun moment où « on ne sait pas quoi faire » ; toutes les cibles
du tutoriel sont surlignées ET fléchées.

**K.2 La joueuse mobile (téléphone, 5 min de pause).** Tape les cristaux au
doigt, boutons assez gros, fenêtres qui tiennent dans l'écran, active
« réduire les effets » pour la batterie. Sa session de 5 minutes fait
progresser l'objectif affiché. Critère : parcours 1.2 rejouable ENTIER en
émulation tactile Studio sans zoom ni scroll horizontal.

**K.3 Le compétiteur.** Vise le classement : farme la mana JOUÉE (il sait
que les packs n'y comptent pas), optimise 3 × Titan, enchaîne les
ascensions. Critère : son rang apparaît dans le HUD quand il entre au top
10 ; le panneau physique se met à jour en ≤ 60 s.

**K.4 Le soutien (dépense volontiers).** Achète x2 Mana + Auto + VIP + le
Pack. Tout s'active EN DIRECT sans reconnexion ; le Seigneur du Vide
apparaît même s'il a acheté le pass depuis le site pendant qu'il jouait
(revérification au login + à l'événement d'achat). Critère : zéro achat
« perdu », zéro double crédit, même en réseau instable (retries).

**K.5 Le vétéran qui revient.** Sa sauvegarde v1 charge : mana, zones,
familiers intacts (reconcile). Il découvre le tutoriel une fois (2 clics),
les nouveaux visuels, et son rang au classement n'a pas bougé. Critère :
AUCUNE perte de données à la migration, testée par round-trip.

**K.6 Le testeur QA malveillant.** Il forge des remotes (types faux, uids
inventés, cibles hors arène), spamme les prompts, se téléporte au VIP sans
pass via exploit de position. TOUT est refusé serveur : au pire il ne se
passe rien, au mieux il reçoit un message poli. Critère : la Section 14
passée au crible, aucun gain d'état côté serveur.

## ANNEXE L — CATALOGUE ANTI-TRICHE (attaque → parade implémentée)

1. Auto-clicker sur cristal → cooldown serveur 0,35 s : gain plafonné, pas
   d'avantage vs un joueur rapide. 2. Téléport-hack vers un cristal
   lointain → la distance est validée SERVEUR au moment du clic ; être
   téléporté n'augmente pas le taux (cooldown global). 3. Fire du remote
   CastSpell en boucle → cooldown 0,6 s + validations d'arène. 4. CastSpell
   sur un joueur au lobby → refus « les deux dans l'arène ». 5. Arguments
   forgés (nombre au lieu de string, table au lieu de Player) → type-check
   silencieux sur CHAQUE remote. 6. Achat d'œuf spammé pendant le yield du
   tirage → le débit précède le tirage, le solde ne peut pas doubler
   (vérifier-débiter atomique). 7. Equip de 4 familiers par uid direct →
   recomptage serveur à chaque demande. 8. « replay » du tutoriel en boucle
   pour refarmer les 50 gemmes → accepté UNE analyse : le replay re-verse
   les petits cadeaux (75 gemmes/cycle de ~2 min)… PARADE : c'est moins
   rentable que de jouer (une Forêt à x5 rapporte plus vite), assumé et
   documenté ; si l'exploitation devenait notable, marquer les récompenses
   déjà versées (roadmap listée). 9. Marquer AutoTrain=true sans pass via
   SetSettings → SetSettings ne copie QUE Music/Sfx/LowFx ; l'auto passe
   par ToggleAuto qui vérifie le pass. 10. Fausse victoire en faisant
   /reset à côté d'un ami → le crédit exige un DERNIER ATTAQUANT réel dans
   les 10 s : le reset volontaire sans sort reçu ne crédite personne.
   L'échange de victoires consenties reste possible (25 gemmes / cooldown
   de mort) : rendement faible, toléré v2, surveillable en roadmap.
11. Exploiter l'invite d'achat pour dupliquer un reçu → PurchaseId
   idempotent. 12. Rejouer TutorialAdvance aux étapes de gameplay → seules
   les étapes 0/5 acceptent un avancement client. 13. Modifier le snapshot
   côté client (mana affichée fausse) → purement cosmétique : toutes les
   dépenses relisent le profil SERVEUR. 14. Détruire localement murs/props
   → n'affecte que son client ; les positions de jeu sont serveur.
15. Speed-hack de déplacement → hors périmètre v2 (aucun gain économique :
   les gains sont au clic validé, pas au déplacement) ; noter en roadmap
   une vérification de vélocité si le besoin apparaît.

## ANNEXE M — STYLE DE CODE (normatif)

En-tête de chaque fichier : bloc de commentaire avec le nom, l'emplacement
Roblox, le rôle en 2-4 lignes, la mention « Lua 5.1 pur ». Nommage :
modules et fonctions publiques en PascalCase (`PetService.GrantPet`),
locales en camelCase, constantes de module en MAJUSCULES
(`PROFILE_TEMPLATE`). Commentaires en français, SANS accents dans le code
serveur si un doute d'encodage existe, MAIS textes joueurs toujours
accentués correctement. Chaque `pcall` a une stratégie explicite commentée
(retry ? dégradation ? silence ?). Aucun nombre magique : tout passe par
Config. Les fonctions dépassant ~40 lignes se découpent. Un service = un
fichier = une responsabilité. Les commentaires expliquent les CONTRAINTES
non évidentes (« clé STRING : contrainte JSON DataStore »), jamais la
paraphrase du code. Pas de code mort, pas de TODO non préfixé
TODO_UTILISATEUR.

## ANNEXE N — GABARIT DU README À LIVRER

Structure imposée : (1) une phrase de présentation + capture conceptuelle ;
(2) « Installation (2 minutes) » : ouvrir le .rbxlx, la map est visible,
F5 pour jouer, publier + activer l'accès API Studio pour sauvegarder ;
(3) « Configurer la monétisation » : les 8 IDs, où les créer, où les
coller, comportement des IDs à 0 ; (4) « Ajouter les vrais assets » : le
manifeste, la liste de courses slot par slot avec mots-clés, le rappel
LICENCES en gras, l'alternative import .fbx ; (5) l'arborescence commentée
du projet ; (6) le tableau de la boucle de jeu (une ligne par système) ;
(7) « Développement » : régénérer le .rbxlx, lancer les tests, vérifier la
syntaxe ; (8) « Garanties techniques » : la liste de la Section 0.2 + 13.3 ;
(9) « Dépannage » : je ne vois rien (Explorer/Output), pas de sauvegarde
(API Studio), boutons Indisponible (IDs à 0), performances (réduire les
effets). Ton : direct, tutoiement, phrases courtes, zéro jargon inexpliqué.


## ANNEXE O — SPÉCIFICATION DE CONFIG.LUA (clé par clé)

Le module Config expose EXACTEMENT ces tables (toute valeur citée ailleurs
dans le document s'y retrouve ; rien d'équilibrable en dehors) :

`Config.GameName` = "Arcane Legends". `Config.Training` : BaseManaPerChannel
1 ; ChannelRange 14 ; ChannelCooldown 0,35 ; AutoInterval 0,6 ; GemChance
0,05 ; GemMin 1 ; GemMax 3. `Config.Zones[1..5]` : Name, Cost, Multiplier,
Color {r,g,b}, CrystalColor {r,g,b} (valeurs des Sections 2.3 et 3.3-3.7).
`Config.ZoneCount` 5 ; `Config.CrystalsPerZone` 6. `Config.VIPZone` : Name,
Multiplier 150, Color, CrystalColor, CrystalCount 8. `Config.Map` : LobbySize
110×110 ; ZoneSize 90×90 ; ZoneSpacing 110 ; ZoneRowZ −150 ; ArenaCenter
(190, 40) ; ArenaSize 80×80 ; VIPCenter (−190, 40) ; VIPSize 70×70 ;
VIPHeight 40 ; LeaderboardPos (0, 48). `Config.Gems.DuelWin` 25.
`Config.Rarities` : cinq entrées {Display, Color, Order 1..5}.
`Config.Pets` : quatorze entrées {Name, Rarity, Multiplier, Emoji} (fiches
4.3). `Config.Eggs[1..3]` : {Id, Name, Cost, Pool {petId=poids}} (4.4).
`Config.MaxEquippedPets` 3. `Config.Rebirth` : BaseCost 10 000 ; CostFactor
4 ; BonusPerRebirth 0,5 ; AuraTiers {1,3,6,10}. `Config.Combat` : Range 45 ;
Cooldown 0,6 ; MinDamage 5 ; MaxDamage 50 ; ManaExponent 0,35 ;
ProjectileSpeed 90. `Config.GamePasses` : cinq entrées {Id 0, Name, Desc}
avec TODO_UTILISATEUR. `Config.Products` : trois entrées {Id 0, Name, Desc,
champs de contenu : Mana/Gems/Pet/Unique}. `Config.Data` : StoreName
"ArcaneLegends_Profil_v1" ; OrderedStoreName "ArcaneLegends_ManaTotale_v1" ;
AutosaveInterval 120 ; MaxRetries 3 ; RetryBaseDelay 2.
`Config.Leaderboard` : RefreshInterval 60 ; TopN 10. `Config.Tutorial` :
ChannelGoal 5 ; Step1Mana 10 ; GiftGems 50 ; FinalGems 25 ; DoneStep 6.
`Config.DefaultSettings` : Music true ; Sfx true ; LowFx false.
`Config.ZoneEmojis` : 🌿🌳🪦🌋🌌. Changer une valeur ici NE DOIT exiger
aucune autre modification pour rester cohérent (les % d'œufs, coûts
affichés, objectifs, etc. sont calculés depuis Config partout).

## ANNEXE P — LE SCRIPT DE BUILD DU .RBXLX (algorithme détaillé)

Un script Python (`build_rbxlx.py`) au niveau du projet, sans dépendance
exotique (stdlib uniquement), exécutable par `python3 build_rbxlx.py` :

1. **Collecte des sources** : parcourt `src/ReplicatedStorage`,
   `src/ServerScriptService`, `src/StarterPlayerScripts`. Convention de
   classe par suffixe : `*.server.lua` → Script ; `*.client.lua` →
   LocalScript ; `*.lua` → ModuleScript. Le nom d'instance = nom de fichier
   sans suffixe.
2. **Bake de la map** : lance `lua5.1 tools/bake_map.lua`, qui charge le
   stub (Annexe H), remplace Remotes par des faux, require le VRAI
   MapBuilder, appelle Build(), parcourt l'arbre produit et émet en JSON la
   liste plate des BaseParts : classe (Part/SpawnLocation), nom, taille,
   CFrame (12 nombres : position + matrice), couleur, matériau (nom
   d'enum), transparence, forme (Ball/Block/Cylinder), collision, et les
   SpecialMesh éventuels (MeshId, Scale). Tout élément non-BasePart
   (lumières, particules, GUIs, prompts) est IGNORÉ au bake : ils
   renaissent au runtime.
3. **Émission XML** : document `<roblox version="4">` avec les services :
   Workspace (contenant le dossier `MapStatique` bâti depuis le JSON),
   ReplicatedStorage/ServerScriptService/StarterPlayer>StarterPlayerScripts
   (les scripts), Lighting/StarterGui/SoundService/Players vides. Chaque
   source est insérée en `ProtectedString` XML-ÉCHAPPÉE (& < >) — jamais de
   CDATA (les `]]` du Lua). Référents uniques séquentiels. Sérialisation
   des propriétés : `bool`, `float`, `token` (tables de correspondance des
   enums Material/Shape/SurfaceType — pour un matériau absent de la table,
   dégrader vers le proche visuel documenté), `Vector3` (name="size"),
   `CoordinateFrame` (X,Y,Z,R00..R22), `Color3uint8` (0xFFrrggbb décimal),
   `Content` pour les MeshId.
4. **Auto-vérification bloquante** : re-parser le fichier produit (XML bien
   formé) ; round-trip EXACT de chaque source (octet pour octet) ; nombre
   de scripts attendu ; nombre de parts bakées > seuil (sinon échec).
   Afficher un résumé (« 20 scripts, 1 900 parts, 640 Ko »).
5. **Reproductibilité** : deux exécutions successives produisent le même
   fichier (le LCG de la map est semé en dur ; aucune horloge, aucun
   aléatoire système dans le build).

## ANNEXE Q — BUDGET DE PROGRESSION (tables de référence pour l'équilibrage)

Hypothèse : ~50 canalisations actives par minute (clic confortable), sans
gamepass. « MPM » = mana par minute.

| Palier de jeu | Multiplicateurs typiques | MPM | Objectif suivant | Délai ressenti |
|---|---|---|---|---|
| Découverte (zone 1) | ×1, sans familier | ~50 | Forêt (250) | ~4 min (tutoriel compris) |
| Forêt + 1er familier | ×5 × ~1,3 | ~325 | Cryptes (5 000) | ~12 min |
| Cryptes + 2-3 familiers | ×25 × ~1,8 | ~2 250 | 1ʳᵉ Ascension (10 000) | ~5 min |
| Post-asc. ×1,5 | ×25 × 1,8 × 1,5 | ~3 375 | Volcan (100 000) | ~25 min cumulés |
| Volcan + Épiques | ×125 × ~2,6 × 2 | ~32 500 | Astrale (2 M) | ~1 h cumulée |
| Astrale + Légendaires | ×625 × ~5 × 3+ | ~470 000+ | ascensions en série | libre |

Règles de retouche : si un délai ressenti dépasse ~2× la table, baisser le
coût suivant OU enrichir les poids d'œufs — JAMAIS en gonflant la base par
clic (le « +1 » de départ est un ancrage psychologique). Les gamepasses
divisent grossièrement ces délais par 2 à 4 : c'est l'accélération promise,
et la table ci-dessus reste la référence du joueur gratuit.

Gemmes : à 5 % × 2 de moyenne, ~5 gemmes/min de jeu actif → Œuf Novice
≈ 10 min, Mystique ≈ 1 h 40, Céleste ≈ 16 h de farm OU les duels (25/
victoire) OU la boutique : chaque source de gemmes doit rester visible dans
l'interface pour que ce grind long ait toujours une alternative perçue.

## ANNEXE R — MIXAGE SONORE (règles fines)

Bus logiques côté client : Musique (music_lobby, volume cible 0,25),
Ambiances (0,3 max, UNE seule active, fondu-enchaîné par lissage ~35 % par
demi-seconde), Effets (one-shots aux volumes du manifeste). Le réglage
« Musique » ne coupe QUE le bus musique ; « Effets sonores » coupe effets
ET ambiances. Anti-fatigue : le carillon de canalisation ne se superpose
pas à lui-même plus de ~3 fois par seconde (réutiliser l'instance si elle
joue déjà) ; la fanfare Légendaire duck la musique à 30 % pendant 2 s puis
remonte. Aucun son > 0,8 de volume. Zéro son au chargement avant la
première interaction visuelle (pas de sursaut d'arrivée).

## ANNEXE S — LOCALISATION FUTURE (préparation sans surcoût)

La v2 est 100 % française. Pour préparer une traduction SANS la faire :
tous les textes joueurs passent par les constantes/formats déjà centralisés
(Annexe A) ; ne JAMAIS concaténer des fragments grammaticaux dépendant de
l'ordre des mots (utiliser des gabarits complets « Duel remporté contre
{X} ! ») ; les nombres passent par l'unique formateur ; les emojis restent
hors des chaînes traduisibles quand ils sont décoratifs. Roadmap : passer
l'Annexe A dans LocalizationService.


## ANNEXE T — SCÉNARIOS DE RECETTE STUDIO (scripts de test manuel)

### T.1 Recette « première expérience » (~12 minutes, solo)
Ouvrir le .rbxlx SANS jouer : vérifier lobby, 5 zones décorées, arène, île
VIP, panneau, mage — puis Explorer (20 scripts) et Output vide. F5. Chrono
en main, dérouler la Section 1.2 : accueil du mage (texte exact), flèche
verte → cristal, 5 clics comptés sur la barre (1/5…5/5), +10 mana, gemme
garantie au clic suivant, +50 gemmes, bouton Familiers qui pulse, achat
Œuf Novice, cinématique (la passer au 2ᵉ œuf pour tester le skip),
familier qui suit avec flottement + étiquette, direction totem Forêt à 250
mana, déblocage, dialogue final, +25 gemmes, confettis. Vérifier ensuite :
widget objectif → Cryptes ; notification « zone verrouillée » en cliquant
un cristal des Cryptes (UNE seule par 2 s en spammant) ; E fonctionne comme
le clic ; leaderstats formatées ; AUCUNE erreur rouge sur toute la recette.

### T.2 Recette « systèmes avancés » (~15 minutes, solo + triche d'admin)
Se donner de la mana via la barre de commande serveur (documenter la
commande dans le README développeur) : acheter zones 3-4-5 dans l'ordre
(vérifier le refus de sauter la 4), première Ascension (double
confirmation, mana à ZÉRO, aura visible, coût suivant 40k), enchaîner à 3
ascensions (nouvelle aura). Ouvrir 10 œufs Novices : noter les raretés
(l'esprit de la distribution, pas un test statistique), vérifier tri de
l'inventaire, limite 3 équipés, retrait/équipement instantanés. Réglages :
couper musique puis effets (silence total propre), activer « réduire les
effets » (les lucioles s'arrêtent), « Revoir le tutoriel » → étape 0.
Boutique : chaque bouton à ID 0 affiche « non configuré » sans erreur.
Pads VIP sans pass → message + invite d'achat (ID 0 : message seulement).

### T.3 Recette « multijoueur » (~10 minutes, Clients and Servers, 2 clients)
Client A et B entrent dans l'arène : A clique B (projectile visible des
DEUX côtés, B subit la secousse), cooldown vérifié au spam, B sort de
l'arène → A ne peut plus le toucher, B revient, A l'achève → +25 gemmes
pour A, messages croisés corrects, B réapparaît au lobby SANS perte.
Les familiers de A sont visibles par B (et réciproquement) avec étiquettes.
Le panneau de classement affiche A et B dans l'ordre après ≤ 60 s. A quitte
brutalement (fermer le client) : sa progression est là à la reconnexion.

## ANNEXE U — JOURNALISATION SERVEUR (catalogue normatif des messages)

Préfixes obligatoires par module entre crochets, warnings UNIQUEMENT pour
l'anormal-récupérable, print pour les jalons de vie. Catalogue :
`[Arcane Legends] Serveur initialise : que la magie commence !` (unique
print de démarrage) ; `[DataManager] {op} : tentative {i}/{n} echouee :
{err}` puis `... : session temporaire (aucune sauvegarde ne sera faite.)` ;
`[DataManager] Session temporaire pour {joueur} : sauvegarde refusee
(protection des donnees).` ; `[Monetization] ProductId inconnu : {id}` ;
`[Leaderboard] Lecture du classement impossible (nouvel essai dans 60 s).` ;
`[AssetLoader] Mesh '{slot}' invalide, fallback procedural utilise.`.
Interdits : print de debug résiduel, warning dans le flux nominal (un
joueur sans gamepass n'est PAS une anomalie), stacktrace non capturée.
Un serveur sain qui tourne une heure n'écrit RIEN d'autre que le print de
démarrage et d'éventuels warnings de pannes externes réelles.

## ANNEXE V — CONFORMITÉ ROBLOX (checklist de publication)

Contenu : classé tout-public — aucun contenu effrayant/violent (duels =
« entraînement entre sorciers », aucun sang, disparition en confettis
d'étincelles), aucun texte non-familial, pas de liens externes ni de
demandes d'informations personnelles. Monétisation : les achats font ce que
leurs descriptions annoncent, les probabilités des œufs payés en monnaie
achetable sont AFFICHÉES (Section 4.4 — aligné sur les politiques
loot-box), le Pack « UNIQUE » est réellement unique. Techniques : aucun
service à autorisation spéciale requis autre que les DataStores (activer
l'accès API Studio est documenté) ; pas d'assets audio sans licence (le
manifeste le rappelle slot par slot) ; les gamepasses/products sont créés
sur le MÊME univers que le place publié (piège classique documenté dans le
README). Métadonnées de publication suggérées : nom « Arcane Legends —
Simulateur de Sorcier ⚡ », genre Aventure/Simulateur, descriptif reprenant
la Section 0.1, icône/vignette à créer par l'utilisateur (hors périmètre).

## ANNEXE W — DÉFINITION DE « FINI » (serment de livraison)

Le projet est FINI quand — et seulement quand — les sept phrases suivantes
sont vraies et vérifiées : (1) `luac -p` passe sur chaque fichier livré.
(2) Les suites de tests hors-Roblox passent à 100 %, pannes simulées
comprises. (3) Le fichier place s'ouvre sur une map visible et F5 lance le
jeu complet sans une erreur console. (4) La recette T.1 se déroule
intégralement comme écrite, au clavier ET au tactile. (5) Chaque valeur
chiffrée du jeu correspond au présent document, Config en fait foi.
(6) Un ID de monétisation à zéro ne casse RIEN, et un ID renseigné
s'active sans redémarrage de session pour les gamepasses achetés en jeu.
(7) README, DECISIONS et rapport permettent à un inconnu de comprendre,
lancer, configurer et faire évoluer le projet sans lire le code. Tant
qu'UNE de ces phrases est fausse, le travail continue : re-diagnostiquer,
corriger, re-tester — sans demander la permission de finir le travail.

## ANNEXE X — TRENTE PIÈGES ROBLOX CONNUS (et la parade exigée)

Liste de chausse-trappes réelles, chacune formulée « piège → parade
normative ». Plusieurs proviennent de bugs effectivement rencontrés et
corrigés sur la v1 de ce jeu : les ignorer serait régresser.

1. Itérer `pairs(sessions)` pendant que la sauvegarde yield et qu'un joueur
   arrive → « invalid key to next », l'autosave meurt en silence → copier
   les clés AVANT la boucle. 2. `PlayerAdded` connecté après l'arrivée du
   premier joueur (Studio surtout) → toujours coupler « connecter le
   signal + balayer GetPlayers() existants » via un helper unique.
3. `UserOwnsGamePassAsync` échoue UNE fois au login → l'acheteur perd ses
   avantages toute la session → 3 tentatives espacées, et re-vérification
   à l'événement d'achat. 4. L'octroi d'un familier de gamepass part avant
   la fin du chargement du profil (course au login) → attendre le profil
   (borné 30 s) avant d'appliquer les effets. 5. L'auto-canalisation vise
   le cristal le plus PROCHE même verrouillé → zéro gain + spam de
   notifications → filtrer par zone possédée AVANT le tri par distance.
6. La mana achetée gonfle le classement « mana gagnée » → créditer les
   achats HORS TotalMana (drapeau dédié dans la fonction de crédit).
7. Le panneau SurfaceGui orienté au petit bonheur → la face AVANT d'une
   Part est −Z : calculer l'orientation, ne pas deviner. 8. Capturer
   `Workspace.CurrentCamera` une fois → référence morte après respawn →
   relire la caméra à CHAQUE usage. 9. Reconstruire une grille d'interface
   à chaque tick de mana → ne reconstruire les fenêtres lourdes que
   VISIBLES. 10. `InvokeServer` peut lever (serveur occupé/déconnecté) →
   chaque appel client sous pcall avec message doux « Le serveur n'a pas
   répondu, réessaie. ». 11. `math.clamp`, `+=`, `continue` → Luau
   uniquement : la contrainte Lua 5.1 les interdit (clamp maison dans
   Util). 12. Clés numériques dans une table persistée → massacrées par le
   JSON DataStore → clés STRING partout (zones, uids, purchases).
13. `MeshPart.MeshId` en écriture runtime → interdit par le moteur →
   Part + SpecialMesh (G.8). 14. `Random.new(graine)` pour une map à baker
   hors Roblox → générateur maison en Lua pur (G.9). 15. `Touched` se
   déclenche en rafale (chaque membre du personnage) → anti-rebond par
   joueur (1,5 s) sur les pads. 16. Un ProximityPrompt ET un ClickDetector
   sur le même objet → le prompt intercepte le clic → ClickablePrompt =
   false, le clic revient au ClickDetector. 17. `TextScaled` sur un label
   étroit → texte minuscule → prévoir les minima et tester les longues
   chaînes françaises (« Débloquer (2.0M Mana) »). 18. BillboardGui sans
   MaxDistance → soupe d'étiquettes à l'horizon → borner (60-160 studs
   selon l'objet). 19. Un ViewportFrame n'affiche RIEN sans sa propre
   Camera assignée → créer et cadrer une caméra par viewport, rotation par
   modification du CFrame de l'objet, pas de la caméra. 20. Les sons
   joués côté serveur ignorent les réglages du joueur → TOUT l'audio est
   client (Section 8.3). 21. `game.BindToClose` en Studio re-déclenche des
   yields interminables → branche Studio courte (sauver + attendre 2 s).
22. Oublier `ResetOnSpawn=false` sur les ScreenGui → toute l'interface
   clignote à chaque mort → le poser sur chaque racine d'UI. 23. Les
   particules d'un personnage disparaissent au respawn → re-appliquer
   auras et visuels sur CharacterAdded (léger différé d'assemblage).
24. Un `WaitForChild` sans timeout sur un objet optionnel → thread
   suspendu pour toujours → timeout explicite et branche nil. 25. Le
   `PromptGamePassPurchaseFinished` global sert TOUS les passes → filtrer
   par ID et mettre à jour le cache AVANT d'appliquer les effets. 26. Les
   contraintes (AlignPosition) sur une part ANCRÉE ne font rien → la part
   pilotée doit être non-ancrée, massless, et posséder l'attachment0.
27. Détacher/mettre à jour le leaderboard depuis le client → il est
   SERVEUR (SurfaceGui du monde) ; le client ne reçoit que la diffusion
   d'affichage HUD. 28. Le premier snapshot part avant que le client
   n'écoute → le client DEMANDE aussi son profil au démarrage
   (GetProfile) : double canal, zéro trou. 29. Tester les probabilités
   avec 100 tirages → variance énorme, faux rouges → 20 000 tirages et
   tolérances larges (E.1.5). 30. Croire l'ordre des multiplications
   flottantes interchangeable → aux frontières de 0,5 l'arrondi diverge →
   les tests reproduisent l'ORDRE EXACT du code de production (E.1.8).


## ANNEXE Y — GUIDE DU MODDEUR (recettes de modification courantes)

Le livrable doit rendre ces sept modifications possibles par un débutant
motivé, en suivant uniquement le README — c'est un test de qualité de
l'architecture. Chaque recette liste les fichiers touchés (et EUX SEULS).

**Y.1 Ajouter une 6ᵉ zone (« Abîme Runique », x3125, 50 M).** Dans Config :
ajouter l'entrée [6] de Config.Zones (nom, coût, multiplicateur, deux
couleurs), passer ZoneCount à 6, ajouter l'emoji. Dans MapBuilder : ajouter
le matériau d'ambiance de la zone et, s'il y a lieu, une table de props
(sinon la zone naît sobre mais fonctionnelle). RIEN d'autre : totem,
verrouillage, progression séquentielle, objectifs du widget, tests de
cohérence — tout dérive de Config. Si un 7ᵉ fichier doit être touché pour
que la zone existe, l'architecture a échoué.

**Y.2 Ajouter un familier à un œuf existant.** Config : une entrée dans
Config.Pets (id ASCII, nom français, rareté existante, multiplicateur dans
les bornes, emoji) + son poids dans le Pool de l'œuf. AssetManifest : un
slot pet_{id} avec Notes. Les pourcentages affichés, le tirage, les cartes
d'inventaire et le fallback (couleur de rareté + accessoire) suivent seuls.

**Y.3 Créer un 4ᵉ œuf.** Config.Eggs[4] (id, nom, coût, pool) ; le panneau
des œufs est GÉNÉRÉ depuis Config.Eggs : la carte, ses chances et son
bouton apparaissent sans toucher l'interface. Prévoir le slot egg_{id} au
manifeste.

**Y.4 Changer un prix ou un multiplicateur.** Config uniquement, une ligne.
Relancer les tests logiques : ceux qui vérifient la conformité au présent
document doivent être mis à jour EN MÊME TEMPS (le test est le contrat).
Puis relancer le build du .rbxlx (les étiquettes de totems affichent les
coûts bakés).

**Y.5 Ajouter un gamepass (ex. « x2 Vitesse »).** Config.GamePasses : une
entrée {Id=0, Name, Desc}. MonetizationService n'a RIEN à savoir de plus
(cache et achats sont génériques). L'EFFET, lui, se code au point d'usage :
pour la vitesse, un branchement dans le service concerné qui lit
OwnsPass(joueur, "SpeedBoost") — suivre l'exemple de DoubleMana dans
Economy. La boutique affiche la nouvelle carte automatiquement.

**Y.6 Remplacer un fallback par un vrai mesh.** Trouver l'asset sur le
Creator Store avec les mots-clés du slot, VÉRIFIER sa licence, copier son
ID dans le manifeste, relancer le build. Si le mesh est trop grand/petit :
ajuster le champ Scale du slot, jamais le code. Pour revenir en arrière :
remettre 0.

**Y.7 Traduire le jeu.** Suivre l'Annexe S : toutes les chaînes joueur
vivent aux points listés en Annexe A ; les remplacer une à une, ne jamais
découper une phrase en fragments recomposés. Les tests qui vérifient des
textes exacts sont à mettre à jour dans le même geste.

**Règle transverse du moddeur.** Après TOUTE modification : (1) luac -p sur
les fichiers touchés ; (2) suites de tests ; (3) regénérer le .rbxlx ;
(4) une ligne dans DECISIONS.md si le choix n'était pas trivial. Le README
répète cette règle en encadré final.


## ANNEXE Z — KIT DE PUBLICATION (page du jeu, prête à coller)

Le livrable inclut un fichier PUBLICATION.md contenant, prêts à coller sur
create.roblox.com :

**Nom du jeu.** « Arcane Legends — Simulateur de Sorcier ⚡ » (garder
l'éclair : il signale la boucle « clic-énergie » du genre au premier
regard).

**Description de la page (gabarit normatif).** « ⚡ Deviens le plus grand
sorcier d'Arcane Legends ! ✨ Canalise les cristaux magiques pour gagner de
la Mana. 🌍 Débloque 5 zones enchantées, de la Prairie Arcanique à la
Dimension Astrale. 🐾 Ouvre des œufs et équipe jusqu'à 3 familiers qui
multiplient tes gains. 🌟 Ascensionne pour des bonus permanents et des
auras légendaires. ⚔️ Défie tes amis dans l'arène (on n'y perd jamais
rien !). 🏆 Grimpe dans le top 10 mondial ! — Jeu en français, jouable à
la souris comme au doigt, tutoriel pour bien démarrer. Mises à jour
régulières : dis-nous en commentaire quel familier tu veux voir ! »

**Réglages de la page.** Genre : Aventure (sous-tag simulateur). Appareils :
PC + tablette + téléphone (la console exige un test manette non couvert par
la v2 : la cocher seulement après l'avoir fait). Serveurs : taille par
défaut. Accès payant : non. Tous âges.

**Captures d'écran à faire (3 minimum, dans cet ordre d'accroche).**
1. Le lobby au crépuscule avec la fontaine, un joueur entouré de ses trois
   familiers étiquetés et l'aura dorée : « l'image de rêve » du jeu.
2. La cinématique d'éclosion à l'instant de la révélation d'un Légendaire
   (rayons dorés) : l'écran le plus « juteux » du jeu.
3. L'arène pendant un duel, projectile violet en vol entre deux joueurs.
Cadrer SANS l'interface de debug, en 16:9, luminosité montée d'un cran
(les vignettes Roblox assombrissent).

**Icône (à produire, hors périmètre du code).** Un cristal néon violet
central sur fond de ciel crépusculaire, un familier mignon au premier
plan, le titre en FredokaOne : lisible en 128 × 128. Éviter tout visage
réaliste et tout texte au-delà du titre.

**Notes de patch types (gabarit pour les mises à jour futures).**
« 🆕 v2.1 — L'Œuf des Tempêtes ! Trois nouveaux familiers, un événement
météo dans la Prairie, et vos réglages sont désormais accessibles pendant
la cinématique. 🐛 Corrigé : ... 💜 Merci pour vos idées en commentaires ! »
— toujours : les nouveautés d'abord, les corrections ensuite, un merci
pour finir, aucun jargon.

---

*FIN DU PROMPT MAÎTRE ULTRA-DÉTAILLÉ (Parties I et II). Tout copier, de la
Section 0 à cette ligne.*
