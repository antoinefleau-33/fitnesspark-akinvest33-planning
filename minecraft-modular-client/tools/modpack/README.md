# Pack de performance — Minecraft 26.2 (Fabric)

Liste vérifiée le 15 août 2026 en interrogeant Modrinth en direct.

## Utilisation

```bash
# Voir ce qui est disponible
python3 resolve_mods.py --mc 26.2 --allow-alpha

# Télécharger dans le dossier mods de ton instance
python3 resolve_mods.py --mc 26.2 --allow-alpha --download ~/.minecraft/mods
```

Le script demande à Modrinth quelle est la dernière version compatible de chaque mod, au moment
où tu le lances. C'est fait exprès : une liste de numéros de version écrite en dur est périmée en
quelques semaines. Relance-le après chaque mise à jour de Minecraft, il te dira ce qui a suivi et
ce qui manque encore.

## Ce qui est disponible en 26.2 (20 mods)

### Les quatre qui font la différence

| Mod | Ce que ça change |
|---|---|
| **Sodium** | Réécrit complètement l'affichage du jeu. C'est de loin le plus gros gain, souvent 2 à 4 fois plus de FPS. Si tu n'en installes qu'un, c'est celui-là. |
| **Lithium** | Optimise les calculs du jeu : mobs, redstone, physique. Aucun changement visible, juste moins de saccades. |
| **FerriteCore** | Divise la mémoire utilisée. Utile si tu as 8 Go de RAM ou moins. |
| **ImmediatelyFast** | Accélère tout ce qui est texte, interface et entités à l'écran. |

### Les compléments utiles

| Mod | Ce que ça change |
|---|---|
| **Entity Culling** | Arrête de dessiner les mobs cachés derrière les murs. Très efficace dans les fermes à mobs. |
| **MoreCulling** | Même principe, appliqué aux blocs. |
| **Sodium Extra** | Ajoute des réglages : limiter les particules, couper le brouillard, etc. Chaque option grattée = des FPS. |
| **C2ME** | Charge les morceaux de monde en parallèle. Moins de blocages quand tu explores vite. *(en bêta)* |
| **Krypton** | Optimise le réseau. Surtout utile en multijoueur. |
| **Dynamic FPS** | Met le jeu en veille quand tu passes sur une autre fenêtre. Ton PC respire et la batterie tient. |
| **Let Me Despawn** | Fait disparaître plus vite les mobs dont personne ne s'occupe. |
| **BadOptimizations** | Un paquet de petites optimisations sans risque. |
| **Language Reload** | Démarrage du jeu nettement plus rapide. |
| **RRLS** | Supprime l'écran de rechargement des ressources. *(en bêta)* |
| **FastQuit** | Quitter un monde devient instantané. |
| **Debugify** | Corrige des bugs que Mojang n'a pas corrigés. |

### Cas particuliers

| Mod | À savoir |
|---|---|
| **Nvidium** | Uniquement sur carte NVIDIA, et demande Sodium. Gain énorme quand ça marche. *(en bêta)* |
| **Iris** | Pour les shaders. Attention : les shaders **coûtent** des FPS, ils n'en donnent pas. À installer seulement si tu veux le rendu, pas la performance. |
| **ViaFabricPlus** | Permet de rejoindre des serveurs d'autres versions depuis ton client 26.2. Voir plus bas. |
| **Fabric API** | Obligatoire, presque tous les mods en dépendent. |

## Pas encore compatibles 26.2

À surveiller, ils sortiront probablement dans les semaines qui viennent :
ModernFix, MemoryLeakFix, ThreadTweak, Cull Less Leaves, Exordium, Indium.

**Enhanced Block Entities** est un cas à part : il est bloqué en 1.21.4 et n'est plus maintenu.
Dommage, c'était le mod qui rendait les coffres beaucoup moins coûteux à afficher.

## ViaFabricPlus : ce n'est pas la même chose que notre sélecteur de version

C'est un point important, et facile à confondre.

**ViaFabricPlus traduit le langage réseau.** Ton jeu reste en 26.2 ; le mod traduit à la volée les
messages échangés avec un serveur en 1.8.9 ou en 1.20.1. Tu peux donc jouer sur ces serveurs sans
changer de client.

**Notre sélecteur de version charge vraiment l'autre version du jeu.** Le rendu, les sons, les
mécaniques : tout devient celui de 1.8.9.

En pratique :

| | ViaFabricPlus | Notre sélecteur |
|---|---|---|
| Temps de dev | 0, c'est un mod à installer | plusieurs mois |
| Jouer sur un serveur 1.8.9 | oui | oui |
| Le combat se comporte comme en 1.8.9 | **non**, tu gardes les mécaniques de 26.2 | oui |
| Les blocs récents s'affichent | oui | non |

**Si ton objectif est de jouer sur des serveurs anciens, ViaFabricPlus suffit et te fait gagner des
mois.** Le sélecteur de version ne devient nécessaire que si tu veux le *comportement* exact d'une
ancienne version — typiquement le PvP 1.8.9, où le timing des coups est différent.

## Attention : ces mods ne s'installent pas sur notre client POC

Ils sont faits pour **Fabric**, le chargeur de mods standard. Le client qu'on développe dans ce
dépôt a son propre système de modules, incompatible.

Deux chemins possibles :

1. **Tu veux jouer maintenant, avec un maximum de FPS** → installe Fabric 26.2 + cette liste. Une
   demi-heure, et tu auras de meilleures performances que la plupart des clients payants.
2. **Tu veux construire ton client** → continue le POC, mais sache qu'il faudra réimplémenter
   soi-même l'équivalent de Sodium, ce qui représente à soi seul des années de travail.

Le plus réaliste est de faire les deux séparément : Fabric pour jouer, le POC comme projet
d'apprentissage.

## Réglages en jeu qui rapportent gros

Une fois les mods installés, dans les options :

- **Distance d'affichage** : 8 à 12 chunks. C'est le réglage le plus coûteux du jeu, très loin
  devant les autres.
- **Distance de simulation** : 6 à 8. Séparé de l'affichage, et souvent oublié.
- **VSync** : désactivé si tu veux le maximum de FPS.
- **Nuages** : désactivés.
- **Particules** : minimales.
- **RAM allouée** : 4 à 6 Go. **Ne mets pas 16 Go** — contrairement à ce qu'on lit partout, trop de
  mémoire allonge les pauses du ramasse-miettes et provoque des micro-saccades.
