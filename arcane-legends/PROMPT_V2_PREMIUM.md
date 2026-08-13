# PROMPT MAÎTRE V2 — Arcane Legends ÉDITION PREMIUM (à copier-coller tel quel)

```
Construis un jeu Roblox COMPLET et fonctionnel appelé « Arcane Legends », un simulateur de sorcier (thème fantasy/magie — AUCUNE référence au sport, au fitness ou à la musculation, ni dans le gameplay, ni dans les noms, ni dans les textes). C'est l'ÉDITION PREMIUM : vrais assets 3D, interface au niveau des simulateurs professionnels (style Pet Simulator / Bubble Gum Simulator), son, lumière et finition soignés. Si un code v1 existe déjà, pars de lui et upgrade-le sans régresser sur ses garanties.

════════════════════════════════════════════
1. BOUCLE DE JEU (inchangée, non négociable)
════════════════════════════════════════════
- Canaliser des cristaux magiques (clic / tap / touche E à proximité) pour gagner de la Mana.
- 5 zones à débloquer avec la Mana : Prairie Arcanique (gratuite, x1), Forêt Enchantée (250, x5), Cryptes Oubliées (5 000, x25), Volcan Runique (100 000, x125), Dimension Astrale (2 000 000, x625). Zones physiquement ouvertes, gain verrouillé côté serveur tant que non achetées (totems ProximityPrompt à l'entrée, progression linéaire).
- Gemmes : 5 % de chance par canalisation (1-3), +25 par victoire en duel.
- Familiers : 3 œufs (50 / 500 / 5 000 gemmes), tirage pondéré CÔTÉ SERVEUR, raretés Commun→Légendaire (+Mythique exclusif), multiplicateurs x1.2 à x3.2 (x3.5 mythique), 3 équipés max, multiplicateur total = 1 + somme des (mult − 1).
- Ascensions : sacrifier TOUTE sa mana, coût = 10 000 × 4^n, +50 % permanent par ascension, auras aux paliers 1/3/6/10.
- PvP : arène dédiée, clic sur un adversaire = sort, dégâts = clamp(5 + Mana^0.35, 5, 50), validés serveur (les deux dans l'arène, portée 45, cooldown 0,6 s), vainqueur +25 gemmes.
- Classement global persistant top 10 (OrderedDataStore sur la mana totale gagnée EN JOUANT — la mana achetée ne compte pas), panneau physique, refresh 60 s.
- Monétisation : 5 gamepasses (x2 Mana, x2 Gemmes, Auto-Canalisation, Sanctuaire VIP x150, familier mythique Seigneur du Vide x3.5) + 3 developer products (Pack de Départ UNIQUE 5 000 mana + 200 gemmes + familier exclusif, 100 Gemmes, 1 200 Gemmes). ProcessReceipt IDEMPOTENT (PurchaseId historisés), cache gamepass rafraîchi via PromptGamePassPurchaseFinished, IDs à 0 avec TODO_UTILISATEUR.

════════════════════════════════════════════
2. VRAIS ASSETS 3D — RÈGLE D'OR DU MANIFESTE
════════════════════════════════════════════
Contrainte de réalité : personne (ni une IA) ne doit inventer des IDs d'assets Roblox — un ID faux = mesh invisible ou erreur. Donc :

a) TOUT asset externe passe par UN SEUL fichier : ReplicatedStorage/AssetManifest.lua.
   Chaque entrée = { MeshId = 0, TextureId = 0, Scale, Offset, Notes = "quoi chercher sur le Creator Store" } avec TODO_UTILISATEUR.
b) FALLBACK OBLIGATOIRE : chaque élément visuel a une version procédurale (parts/unions stylisées) utilisée automatiquement si l'ID vaut 0 ou si le chargement échoue (pcall). Le jeu est TOUJOURS jouable et beau, même avec zéro ID renseigné — les meshes le rendent magnifique, ils ne sont jamais une dépendance.
c) Un utilitaire AssetLoader (ReplicatedStorage) fait : ID valide → MeshPart configuré ; sinon → constructeur de fallback. Précharge tout via ContentProvider:PreloadAsync au chargement.
d) Fournir dans le README la liste de courses exacte : pour chaque slot du manifeste, les mots-clés Creator Store recommandés (ex. « low poly crystal », « fantasy tree pack », « dragon pet ») + le rappel de vérifier la licence d'utilisation.
e) Alternative pro documentée : import Blender/.fbx → « les fichiers .fbx doivent être importés via Avatar Importer / Asset Manager, puis coller les IDs dans le manifeste ».

Assets attendus dans le manifeste (avec fallback procédural stylisé pour chacun) :
- Cristaux de mana (1 variante par zone + VIP) — fallback : cristal multi-facettes en unions de wedges néon.
- Familiers : 1 mesh par familier (14) — fallback : corps sphérique + yeux + accessoire de rareté (couronne légendaire, flammes mythiques).
- Œufs (3 + animation d'éclosion) — fallback : œuf en parts lissées avec motifs.
- Props par zone : Prairie (arbres ronds, fleurs lumineuses, rochers runiques), Forêt (champignons géants luminescents, saules), Cryptes (colonnes brisées, tombes, braseros verts), Volcan (roches basaltiques, coulées de lave néon, geysers), Dimension Astrale (îlots flottants, anneaux planétaires, étoiles) — fallback : versions low-poly en parts.
- Arène : murailles crénelées, torches, statues de sorciers — fallback parts.
- Totems d'achat, portail VIP, panneau de classement sculpté.
- Sons (AssetManifest.Sounds, IDs à 0 + TODO) : clic de canalisation, gain de gemmes, éclosion (commune/rare/légendaire), sort PvP, impact, victoire, achat, ascension, ambiance par zone, musique lobby. Fallback : silence propre (jamais d'erreur).
- Icônes UI (AssetManifest.Icons, IDs image à 0 + TODO) : mana, gemme, familier, œuf, boutique, ascension, auto, classement, réglages. Fallback : émoji/texte Unicode (⚡💎🐾🥚🛒✨).

════════════════════════════════════════════
3. DIRECTION ARTISTIQUE & MONDE
════════════════════════════════════════════
- Style : low-poly fantasy lumineux, palette violet/or/cyan, lisible sur mobile.
- La map doit être VISIBLE EN MODE ÉDITION : le fichier place livré contient la map pré-construite (pas un monde vide + construction au Play). Le script de build de la map reste disponible pour la régénérer, mais l'expérience « j'ouvre le fichier → je vois le jeu » est obligatoire.
- Éclairage : Lighting technology Future si possible, ClockTime crépuscule, Atmosphere colorée, Bloom doux, ColorCorrection par zone (vert forêt, orange volcan, bleu astral), brouillard de profondeur léger.
- Chaque zone = ambiance distincte : sol texturé (MaterialVariants ou matériaux natifs), props du manifeste disposés avec variation (rotation/échelle aléatoires seedées), particules d'ambiance (lucioles, cendres, poussière d'étoiles), sons d'ambiance en SoundGroups avec volume par zone.
- Cristaux : rotation + flottement animés CÔTÉ CLIENT, halo PointLight, éclat de particules à la canalisation, « burst » visuel proportionnel au gain.
- Familiers équipés : suivent le joueur avec flottement sinusoïdal et légère traîne (pas de soudure rigide) ; nom + rareté en billboard stylisé ; traînée de particules aux raretés Épique+.
- PvP : projectile lumineux avec traînée, impact avec onde de choc, screen shake léger côté victime.
- Auras d'ascension : 4 paliers visuellement croissants (brume → volutes → flammes dorées → tempête écarlate + anneau au sol).

════════════════════════════════════════════
4. GUI NIVEAU PROFESSIONNEL (style grands simulateurs)
════════════════════════════════════════════
Système de design unique (module UIKit) : UICorner généreux, UIStroke épais sombre, UIGradient verticaux, ombres portées simulées, polices FredokaOne (titres) / GothamBold (corps), animations Spring/Back via TweenService, sons UI sur hover/clic. AUCUNE fenêtre brute rectangulaire grise.

- HUD : compteurs Mana/Gemmes en pilules avec icône, fond dégradé, animation « pop » à chaque gain + petit texte volant « +X » ; multiplicateur global affiché ; boutons latéraux ronds avec icônes et label, effet d'enfoncement au clic.
- Boutique à ONGLETS (Gamepasses / Gemmes / Packs) : cartes produit avec icône, prix en Robux, badge « POPULAIRE » / « MEILLEURE OFFRE », état Possédé, le Pack de Départ disparaît une fois acheté.
- Familiers : grille de cartes (fond coloré par rareté, mesh du pet en ViewportFrame avec rotation lente, multiplicateur affiché), tri par rareté, clic = équiper/retirer avec feedback, compteur X/3 équipés, panneau des 3 œufs avec aperçu des pets et pourcentages de drop AFFICHÉS (transparence des probabilités).
- ÉCLOSION CINÉMATIQUE : overlay plein écran, œuf en ViewportFrame qui tremble crescendo, flash, révélation de la carte du familier avec rayons rotatifs derrière, couleur/son selon la rareté (fanfare spéciale Légendaire+), skip au clic.
- Ascension : écran dédié avec « avant → après » (multiplicateur actuel vs futur), coût, barre de progression vers le prochain palier d'aura, confirmation en 2 temps.
- Classement : panneau physique dans le monde (SurfaceGui riche : médailles or/argent/bronze, avatars via Players:GetUserThumbnailAsync en pcall) + rangée « ton rang ».
- Notifications toast empilées animées (succès/erreur/gemme/légendaire).
- Réglages : musique on/off, effets on/off, mode « réduire les particules » (mobile).
- 100 % responsive : mobile (boutons plus gros, HUD compacté), tablette, PC. IgnoreGuiInset géré, zones mortes pour le pouce.
- Interface entièrement générée par script, entièrement en FRANÇAIS.

════════════════════════════════════════════
5. TUTORIEL & ACCESSIBILITÉ TOUT ÂGE (le jeu doit être compris par un enfant de 8 ans SANS lire de pavé)
════════════════════════════════════════════
TUTORIEL INTERACTIF au premier lancement (obligatoire, persisté dans le profil : TutorialStep, ne se rejoue jamais, bouton « Passer » toujours visible, rejouable depuis les Réglages) :
- Étape 0 — accueil : un PNJ mage (modèle du manifeste, fallback : personnage en parts avec chapeau) accueille le joueur près du spawn avec une bulle de dialogue courte : « Bienvenue, apprenti ! Suis la flèche ! »
- Étape 1 — canaliser : flèche 3D animée (Beam ou flèche flottante) qui pointe le cristal le plus proche + cristal surligné (Highlight vert pulsant) + consigne à l'écran : « Clique sur le cristal ✨ ». Objectif : canaliser 5 fois. Barre de progression 0/5. Récompense : +10 mana, jingle de réussite, confettis.
- Étape 2 — les gemmes : dès la première gemme gagnée (forcer la chance à 100 % pendant le tutoriel), popup courte : « Les gemmes 💎 servent à acheter des œufs ! »
- Étape 3 — premier familier : offrir 50 gemmes, flèche vers le bouton Familiers, guider l'achat du premier œuf, éclosion cinématique, consigne « Ton familier multiplie ta mana ! »
- Étape 4 — débloquer une zone : flèche vers le totem de la Forêt Enchantée quand le joueur atteint 250 mana ; consigne « Les zones donnent PLUS de mana ! »
- Étape 5 — fin : le mage félicite, mentionne l'arène (« quand tu seras grand et fort ! ») et l'ascension en UNE phrase chacune. Récompense finale : 25 gemmes + titre « Apprenti diplômé » en notification.
- Chaque étape = UNE consigne courte à la fois, jamais deux. Texte ≤ 12 mots par consigne.

WIDGET OBJECTIF permanent (après tutoriel) : petite carte en haut à gauche « Prochain objectif » calculée automatiquement (ex. « Débloque les Cryptes Oubliées — 3 200 / 5 000 mana » avec barre de progression) + flèche 3D activable/désactivable d'un clic. Le joueur sait TOUJOURS quoi faire.

ACCESSIBILITÉ TOUT ÂGE :
- Langage simple partout : phrases courtes, vocabulaire d'enfant, zéro jargon (« mana » expliqué une fois par le mage : « la mana, c'est ta puissance magique ! »). Chiffres toujours abrégés (1,2 k) avec icônes.
- Icône + couleur + forme pour CHAQUE information (jamais la couleur seule — daltonisme) : les raretés ont icône ET couleur ET bordure distinctes.
- Textes généreux et contrastés (TextScaled + tailles minimales), boutons larges, cibles tactiles ≥ 44 px sur mobile.
- Bouton « ? » permanent → panneau « Comment jouer » en 5 pages illustrées (1 dessin + 1 phrase par page : canaliser / zones / familiers / ascension / arène), navigable par grosses flèches.
- Aucune punition : perdre un duel ne coûte rien, aucun contenu effrayant, aucune écriture non adaptée à un public familial (conformité Roblox tout public).
- Info-bulles au survol/appui long sur chaque bouton (une phrase max).
- Feedback systématique : chaque action réussie a un son + une animation ; chaque action impossible affiche POURQUOI en une phrase simple (« Il te faut encore 120 mana ! »).
- L'arène PvP est clairement signalée comme optionnelle et sans risque (panneau à l'entrée : « Ici on s'entraîne entre sorciers, on ne perd rien ! »).

════════════════════════════════════════════
6. EXIGENCES TECHNIQUES (héritées v1, NON NÉGOCIABLES)
════════════════════════════════════════════
- Serveur 100 % autoritaire : distances, cooldowns, fonds, zones, portées, tirages validés serveur ; le client affiche et demande.
- DataStore robuste : retry + backoff, autosave 120 s, save à la déconnexion + BindToClose (itération sûre : copier les clés avant de sauvegarder), échec de chargement → session temporaire JAMAIS sauvegardée ni publiée au classement, clés de tables persistées en string (JSON).
- Monétisation robuste : ProcessReceipt idempotent, NotProcessedYet si profil non chargé ou session temporaire, retry (x3) sur UserOwnsGamePassAsync, attente bornée du profil avant d'appliquer les effets d'un pass au login, mana achetée créditée HORS mana totale de classement.
- Auto-canalisation : ne cible que les cristaux des zones possédées (jamais de spam « zone verrouillée »).
- Architecture : ModuleScripts par service (Config/Util/Remotes/AssetManifest/AssetLoader/UIKit partagés ; DataManager, Economy, MapBuilder, TrainingService, ZoneService, PetService, RebirthService, CombatService, MonetizationService, LeaderboardService, TutorialService, SoundService maison côté serveur) + 1 Script Main + LocalScripts (input, UI, effets, tutoriel). L'état du tutoriel (TutorialStep) et le widget objectif sont VALIDÉS ET PERSISTÉS côté serveur (récompenses d'étapes créditées serveur, jamais le client). AUCUN require croisé entre modules serveur : registre de services injecté par Main (zéro dépendance circulaire). TOUT l'équilibrage dans Config.
- Lua 5.1 pur (pas de +=, pas de continue, pas d'annotations de type) vérifiable par luac -p — les APIs Roblox (typeof, task, ViewportFrame...) restent permises à l'exécution.
- Performances : particules plafonnées, animations UI côté client uniquement, pets/cristaux animés localement (zéro trafic réseau), Debris/nettoyage systématique des effets temporaires.

════════════════════════════════════════════
7. LIVRAISON & VÉRIFICATION
════════════════════════════════════════════
- Tous les .lua organisés par dossier-service + fichier place .rbxlx prêt à ouvrir (scripts EN PLACE dans l'arborescence, map pré-construite visible en édition).
- README : installation pas à pas, liste de courses des assets (mots-clés Creator Store par slot du manifeste + rappel licences), configuration des IDs de monétisation, dépannage « je ne vois rien » (Explorer, Output).
- Journal des décisions + rapport de session.
- Vérification AVANT livraison : luac -p sur chaque fichier ; tests hors-Roblox exécutant le vrai code serveur sur un stub de l'API (profils, économie, tirages, PvP, achats idempotents) ; revue manuelle : remotes cohérents serveur/client, nil-safety Character/HumanoidRootPart, fallbacks d'assets déclenchés quand ID = 0, aucune dépendance circulaire.
- Travaille en autonomie totale : zéro question, décisions documentées.
```

## Variantes rapides
- **Tu as déjà des assets** : colle la liste de tes IDs (meshes/sons/images) à la fin du prompt → ils remplissent le manifeste dès la génération.
- **Autre thème** : remplace le lexique (sorcier/mana/cristaux) et la direction artistique de la section 3 — structure identique.
- **Itérer sur l'existant** : « Pars du code v1 ci-joint et applique la section 2 (assets), 3 (DA) et 4 (GUI) sans toucher aux services serveur. »
