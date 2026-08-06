# Ferme à pétales roses — schematic DonutSMP

Ferme **100 % automatique** qui transforme de la farine d'os en **pétales roses**, l'item
le plus rentable à farmer en masse sur DonutSMP en 2026.

Module de **6 × 7 × 18 blocs** (largeur × hauteur × longueur), empilable à l'infini
côte à côte.

---

## 1. Pourquoi les pétales roses ?

Le mécanisme est une duplication pure, sans mob, sans lave, sans timing fragile :

> Chaque fois que de la farine d'os est appliquée sur un bloc de pétales roses, le nombre
> de pétales augmente de 1. **S'il y a déjà 4 pétales, le bloc lâche une copie de lui-même.**

Donc : un bloc de pétales à 4, un distributeur rempli de farine d'os braqué dessus, une
horloge redstone → **1 farine d'os = 1 pétale rose**, en boucle, à l'infini, sans replanter.

La ferme est donc un **convertisseur farine d'os → pétales roses**. C'est exactement le
principe des fermes qui tournent sur DonutSMP : les guides 2026 classent les pink petals
comme la ferme au plus haut rendement du serveur.

⚠️ **Vérifie la rentabilité avant de construire** (voir §8) : elle dépend du prix de vente
du pétale rose comparé à ton coût d'acquisition de la farine d'os. Je n'ai pas pu vérifier
les prix `/sell` actuels — les prix bougent et le `/shop` a été retiré le 2 juin 2026.

---

## 2. Fichiers

| Fichier | Usage |
|---|---|
| `pink_petal_farm.litematic` | **Litematica** (mod client) — pose bloc par bloc en survie. C'est celui à utiliser sur DonutSMP. |
| `pink_petal_farm.schem` | **WorldEdit / FAWE** (Sponge v2) — pour coller en creative sur un serveur perso ou en singleplayer. |
| `pink_petal_farm.nbt` | **Bloc de structure** vanilla — pour tester en creative sans mod. |
| `PLAN.md` | Plan ASCII couche par couche, si tu préfères construire à la main. |
| `generate_farm.py` | Le générateur. Modifie les constantes et relance pour changer la taille. |

Les trois fichiers décrivent **exactement la même structure** (vérifié par relecture).

### Importer dans Litematica

1. Copier `pink_petal_farm.litematic` dans `.minecraft/schematics/`
2. En jeu : `M` → *Load Schematics* → sélectionner le fichier → *Load*
3. Placer l'aperçu, puis construire en suivant le fantôme.

> DonutSMP interdit les mods qui posent les blocs à ta place (printer / auto-build).
> Litematica en mode **affichage seul** est un guide visuel, c'est la façon normale de
> l'utiliser. Vérifie la liste de mods autorisés du serveur avant.

### Importer avec WorldEdit (creative/test)

```
//schem load pink_petal_farm
//paste
```

---

## 3. Orientation

Dans les fichiers, l'axe **Z croissant = sud**, **X croissant = est**.

- L'**avant** de la ferme (l'horloge redstone) est au **nord**, en `z = 0`.
- L'**arrière** (les coffres de récolte) est au **sud**, en `z = 17`.
- Les coffres à farine d'os sont sur le toit, à gauche et à droite, en `z = 5-6`.

Tu peux faire pivoter la structure dans Litematica (`Rotation`) sans rien casser.

---

## 4. Liste de matériel (1 module)

| Bloc | Quantité |
|---|---|
| Pierre (ou n'importe quel bloc plein) | 514 |
| **Entonnoir** | **52** |
| Bloc de mousse | 36 |
| Rail | 32 |
| Poudre de redstone | 28 |
| **Distributeur** | **24** |
| Pétales roses | 24 |
| Coffre | 6 |
| Bloc de redstone | 4 |
| Rail propulseur | 4 |
| Répéteur | 3 |
| Comparateur | 1 |
| **Wagon-entonnoir** (à poser sur les rails) | **1** |

Le gros du coût, c'est les **52 entonnoirs (260 lingots de fer)** et les
**24 distributeurs**. Si tu veux une version pauvre, voir §7.

Les 514 blocs de pierre sont purement structurels : mets ce que tu veux (terre, netherrack…),
sauf sous les pétales où il faut de la **mousse** (ou terre / herbe / podzol).

---

## 5. Comment ça marche (les 4 étages)

```
Y=6   Toit anti-spawn + 2 doubles coffres d'approvisionnement en farine d'os
Y=5   Horloge à entonnoirs → bus redstone → 2 lignes de poudre sur les distributeurs
      + 2 chaînes d'entonnoirs qui distribuent la farine d'os
Y=4   [h][D][p][p][D][h]   ← distributeurs braqués sur les pétales
Y=3   Mousse (support des pétales)
Y=2   Boucle de rails + wagon-entonnoir (collecte à travers la mousse)
Y=1   Fondation + blocs de redstone sous les rails propulseurs + entonnoirs de vidange
Y=0   Fondation + double coffre de récolte
```

**Le cycle :**

1. L'**horloge à entonnoirs** (2 entonnoirs face à face + comparateur) envoie une impulsion
   toutes les ~5 secondes.
2. Le comparateur ne sort qu'un signal de force 1 → un **répéteur le réamplifie à 15** avant
   de le distribuer sur le bus.
3. Le bus alimente les deux lignes de poudre posées **sur** les distributeurs : les 24
   distributeurs tirent une farine d'os en même temps.
4. Chaque bloc de pétales monte à 4 pétales puis **lâche un pétale rose par impulsion**.
5. Les pétales tombent sur la mousse ; le **wagon-entonnoir** qui tourne en dessous les
   aspire à travers le bloc.
6. Au fond de la boucle, deux entonnoirs vident le wagon dans le **double coffre** en `Y=0`.
7. En parallèle, les 2 chaînes d'entonnoirs du toit rechargent en continu les distributeurs
   depuis les coffres à farine d'os.

Tu n'as donc **jamais à toucher aux distributeurs** : tu remplis les coffres du toit, tu
vides le coffre du fond.

---

## 6. Ordre de construction et mise en service

Construis **de bas en haut**, sinon tu ne pourras plus poser le wagon.

1. **Y=0 → Y=1** : la dalle de fondation, les 4 blocs de redstone, les 2 entonnoirs de
   vidange (orientés vers le bas, dans les coffres).
2. **Y=2** : la boucle de rails. Les 4 rails propulseurs doivent être **posés sur les blocs
   de redstone**. Vérifie que la boucle est bien fermée (2 virages au nord, 2 au sud).
3. **Pose le wagon-entonnoir maintenant** sur un rail. Il doit tourner en boucle sans
   s'arrêter — regarde-le faire un tour complet avant de continuer.
4. **Y=3** : la couche de mousse et les murs.
5. **Y=4** : distributeurs, pétales, entonnoirs d'alimentation.
   - Les distributeurs de gauche (`x=1`) sont braqués **vers l'est**, ceux de droite (`x=4`)
     **vers l'ouest** : chacun doit regarder le bloc de pétales à côté de lui.
   - Les entonnoirs `x=0` pointent **vers l'est** (dans le distributeur), ceux en `x=5`
     **vers l'ouest**.
6. **Y=5** : la redstone.
   - Les **répéteurs** doivent avoir leur flèche dirigée **vers le sud** (vers l'arrière de
     la ferme), entrée au nord.
   - Le **comparateur** lit l'entonnoir situé au nord de lui, sortie vers le sud.
   - Si Litematica affiche un répéteur ou le comparateur dans l'autre sens, casse-le et
     repose-le dans le sens décrit ci-dessus — c'est le sens fonctionnel qui compte.
   - Les chaînes d'entonnoirs `x=0` et `x=5` pointent toutes **vers le sud**.
7. **Y=6** : le toit et les 2 doubles coffres d'approvisionnement.
8. **Démarrage** :
   - Mets **6 items** (n'importe quoi : 6 pierres, 6 bâtons…) dans un des deux entonnoirs de
     l'horloge en `z=0`. L'horloge démarre toute seule.
   - Remplis les 2 doubles coffres du toit de **farine d'os**.
   - Les blocs de pétales montent à 4 pétales tout seuls en quelques cycles, puis la
     production commence.
9. **Accès au coffre de récolte** : il est en `Y=0` tout au fond (`z=17`), face au sud.
   Creuse un accès derrière la ferme.

### Régler la vitesse

Le nombre d'items dans l'horloge fixe la période : **période ≈ nb_items × 0,8 s**.

- 6 items → ~4,8 s → **c'est le réglage recommandé** (voir ci-dessous)
- 3 items → ~2,4 s → plus rapide, mais les entonnoirs ne suivent plus
- 12 items → ~9,6 s → si tu veux économiser ta farine d'os

**Pourquoi 6 ?** Une chaîne d'entonnoirs transporte au maximum **2,5 items/s**. Avec 12
distributeurs par côté, il faut au moins `12 / 2,5 = 4,8 s` entre deux impulsions pour que
l'approvisionnement suive. Aller plus vite ne fait que vider le tampon des distributeurs :
le débit moyen reste plafonné.

### Rendement

- **~5 pétales/seconde ≈ 18 000 pétales/heure** par module, en régime continu.
- Consommation : **18 000 farine d'os/heure**, soit **2 000 blocs d'os/heure**.
- Pour aller plus vite : construis un deuxième module collé au premier. Chaque module est
  totalement autonome (sa propre horloge, ses propres coffres).

---

## 7. Variantes

**Version pauvre (sans alimentation automatique)** — économise 48 entonnoirs (240 fer) :
supprime les colonnes `x=0` et `x=5` (entonnoirs en `Y=4` et `Y=5`) et les 4 coffres du
toit. Tu remplis alors les 24 distributeurs à la main. Chaque distributeur tient 9 stacks
(576 farine d'os), soit **13 824 pétales par recharge complète**, environ 45 minutes de
production. Le module fait alors 4 blocs de large.

**Version plus longue** : dans `generate_farm.py`, augmente `SZ` et `ROWS`. Attention, au-delà
de ~12 rangées la poudre de redstone s'éteint (portée 15) : il faut ajouter des répéteurs
dans les lignes `x=1` et `x=4`, et l'approvisionnement par entonnoirs ne suivra plus.
Mieux vaut **multiplier les modules** que rallonger un module.

---

## 8. Rentabilité : à vérifier toi-même

La formule est simple :

```
profit par cycle = prix_vente(1 pétale rose) − coût_achat(1 farine d'os)
```

Avec 18 000 cycles/heure, même 1 coin d'écart fait 18 000 coins/heure. Mais si la farine
d'os te coûte plus cher que le pétale ne se vend, **la ferme te fait perdre de l'argent**.

À vérifier en jeu avant de construire :

1. `/sell` sur une pile de pétales roses → prix unitaire réel.
2. Le prix de la farine d'os / des blocs d'os sur `/ah` et `/orders`.
3. Rappel : 1 bloc d'os = 9 farine d'os, 1 os = 3 farine d'os.

Sites de suivi des prix (à recouper, ce sont des prix d'hôtel des ventes, pas de `/sell`) :
[DonutSMP Finance](https://donutsmp.finance/items) ·
[donutstats.net](https://www.donutstats.net/prices) ·
[donut.build](https://www.donut.build/auction)

Si les pétales ne sont pas rentables au moment où tu lis ça, **le même schéma marche pour
tout ce qui se duplique à la farine d'os** — il suffit de remplacer le bloc de pétales.

### Autres pistes citées par les guides 2026

- **Fermes à kelp / blocs d'os** : classiques, autosuffisantes (pas d'intrant à acheter).
- **Têtes de piglin** : 2 à 4 M de coins pièce, empilables.
- **Commerce avec les villageois armuriers** : forte marge, mais actif, pas AFK.

---

## 9. Avant de construire, sur le serveur

- Vérifie les règles de DonutSMP sur les fermes à redstone et le lag (les serveurs
  limitent souvent les horloges rapides). Le réglage à 4,8 s est volontairement doux.
- Vérifie que Litematica est bien dans les mods autorisés.
- Construis en zone claimée : la ferme contient 260 lingots de fer d'entonnoirs, c'est une
  cible.
- Éclaire les alentours : le toit empêche les spawns **dans** la ferme, pas autour.

---

## Sources

- [DonutSMP Best Farms 2026 — DonutSMP Finance](https://donutsmp.finance/blog/donutsmp-best-farms)
- [DonutSMP Best Money Making Methods (2026) — DonutSMP Finance](https://donutsmp.finance/blog/donutsmp-money-making-methods)
- [Donut SMP Best Money Farming Tier List 2026 — ggwtb](https://ggwtb.com/blog/donut-smp-best-money-farming-tier-list-2026-easy-profit-methods)
- [DonutSMP 2026 Best Ways to Make Money — IGGM](https://www.iggm.com/news/donutsmp-2026-best-ways-to-make-money)
- [Shop — DonutSMP Wiki](https://donutsmp.fandom.com/wiki/Shop) (retrait du `/shop` le 2 juin 2026)
- [Pink Petals — Minecraft Wiki](https://minecraft.fandom.com/wiki/Pink_Petals) (mécanique de la farine d'os)
