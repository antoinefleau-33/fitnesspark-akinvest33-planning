# 2. Interface utilisateur et rendu

Cible : une UI façon Lunar/Badlion — sombre, épurée, animée à 240 fps sans coût mesurable sur le
jeu.

## OpenGL, pas Vulkan

La question mérite d'être tranchée tout de suite, parce qu'elle conditionne tout le reste.

Minecraft utilise OpenGL. Le client partage son contexte. Introduire Vulkan imposerait soit un
second device et une interop GL/VK (`VK_KHR_external_memory`, synchronisation manuelle par
sémaphores exportés — fragile, mal supporté selon les pilotes), soit de porter le rendu du jeu
lui-même, ce qui est un projet à part entière.

**OpenGL 3.3 core** est le bon choix : disponible partout, compatible avec le pipeline du jeu de
1.8.9 à 1.20.1, et largement suffisant pour une UI 2D. Le gain de Vulkan sur du dessin d'interface
est nul — on est limité par le nombre de draw calls, pas par le pilote.

## Un seul draw call pour toute l'interface

`UiRenderer` fonctionne par **instanciation**. Chaque primitive est une instance de 20 floats
poussée dans un VBO ; à la fin de la frame, un unique `glDrawArraysInstanced` dessine le tout.

Une UI de ce type comporte facilement 300 quads. En mode immédiat façon Minecraft vanilla, c'est
300 changements d'état et autant de draw calls, soit environ 2 ms de temps CPU pilote gaspillées
par frame. Ici, quelques microsecondes.

Détail qui compte : le VBO d'instances est *orphelined* (`glBufferData` avec la même taille avant
chaque `glBufferSubData`), pour que le pilote n'attende pas la fin de la frame en cours. Sans cela,
le stall CPU/GPU devient visible dès un millier d'instances.

## Formes analytiques (SDF)

La forme n'est pas de la géométrie, c'est une **distance signée** évaluée dans le fragment shader :

```glsl
float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}
```

Trois bénéfices :

- **Antialiasing exact d'un pixel à toute échelle**, via `fwidth(d)` qui donne la largeur d'un
  pixel dans l'espace de la SDF. Aucun MSAA, aucun changement à l'échelle GUI 1× → 4×.
- **Bordures et ombres portées quasi gratuites** : la même distance, décalée et élargie. Environ
  quatre instructions supplémentaires.
- **Rayons indépendants par coin**, sélectionnés par deux `mix` sans branchement.

Piège à connaître : il faut dilater le quad pour laisser la place au flou de l'ombre, sinon celle-ci
est coupée net au bord de la géométrie. C'est l'artefact le plus courant en UI SDF.

## Ressorts plutôt que courbes

Deux modèles cohabitent dans `Animations`, et le choix n'est pas cosmétique.

- **Courbes de durée** (`Tween`) pour ce qui a un début et une fin connus : apparition d'écran,
  fondu d'overlay, entrée en cascade.
- **Ressorts** (`Spring`) pour tout ce qui réagit à l'utilisateur : survol, enfoncement, glissement.

Un ressort accepte un changement de cible **en cours de course** en conservant sa vélocité. Une
courbe de durée, elle, redémarre à zéro : c'est exactement ce qui produit cette sensation de
saccade quand on balaie rapidement une liste de boutons. Aucun réglage de courbe ne corrige ça — il
faut changer de modèle.

L'intégration est **semi-implicite** (vélocité mise à jour avant la position), stable pour des
raideurs élevées là où l'Euler explicite diverge à bas framerate. Le pas est en outre sous-divisé à
1/120 s : un `dt` de 200 ms (fenêtre déplacée, GC long) ferait exploser n'importe quel intégrateur
explicite. Sur un client qui peut chuter de 240 à 20 fps pendant un chargement de chunks, ce n'est
pas théorique.

## Retenu, pas immédiat

`MainMenu` conserve ses widgets entre les frames, chacun portant ses propres ressorts. C'est la
raison structurelle pour laquelle les interfaces vanilla et la plupart des mods ne peuvent pas
animer proprement un survol : en mode immédiat, le widget est reconstruit à chaque frame et ne peut
porter aucun état d'animation.

Chaque carte compose trois animations indépendantes — élévation (ombre), décalage horizontal,
surbrillance — ce qui donne le mouvement composé caractéristique des UI modernes plutôt qu'un simple
changement de couleur. Point clé : elles se composent sans se réinitialiser, donc une carte survolée
pendant son apparition suit les deux mouvements sans à-coup.

## Texte : MSDF

Volontairement hors du POC de rendu, parce que le choix mérite d'être explicite.

- **Bitmap façon vanilla** : flou au-delà de 2×.
- **Rasterisation dynamique** (`stb_truetype`, fourni avec LWJGL) : un atlas par taille.
- **MSDF** — atlas généré hors ligne par `msdf-atlas-gen`, échantillonné dans le shader par
  `median(r,g,b)` puis `smoothstep` avec `fwidth`. Un seul atlas, net de 8 px à 200 px, coins des
  glyphes préservés (c'est ce que le *multi-channel* apporte sur une SDF simple).

**NanoVG** (livré avec LWJGL) est l'option pragmatique pour démarrer : texte, chemins vectoriels et
dégradés en quelques appels. Batching moins bon que `UiRenderer`, mais parfaitement suffisant tant
que l'UI n'est pas le goulet.

## Flou d'arrière-plan

Pour l'effet de panneau translucide sur le jeu : **dual-kawase**. Chaîne de downsample avec un
noyau à 5 taps, puis upsample avec un noyau à 8 taps, 3 à 4 niveaux. Qualité proche d'un gaussien
bien plus large, pour une fraction du coût, et adapté à une exécution par frame.

Attention cependant à la lisibilité : un panneau translucide superposé au jeu devient illisible sur
un ciel clair ou sur de la neige. D'où la palette du POC, avec des fonds très opaques
(`0xF0` d'alpha) et un seul accent.

## Échelle et HiDPI

Séparer taille logique de fenêtre et taille du framebuffer. Les confondre donne une UI deux fois
trop petite sur écran Retina. `Window` expose les deux ainsi que le `contentScale` ; toute la mise
en page raisonne en pixels logiques, seul le viewport GL utilise les dimensions du framebuffer.
