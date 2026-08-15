package dev.poc.ui.screen;

import dev.poc.ui.anim.Animations;
import dev.poc.ui.anim.Animations.Spring;
import dev.poc.ui.anim.Animations.Tween;
import dev.poc.ui.render.UiRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menu principal du client : liste de versions à gauche, panneau de lancement à droite, cartes
 * animées.
 *
 * <p>Modèle de rendu <b>retenu</b> et non immédiat, à l'inverse de l'UI de Minecraft. En mode
 * immédiat, chaque widget est reconstruit à chaque frame et ne peut donc pas porter d'état
 * d'animation : c'est la raison structurelle pour laquelle les interfaces vanilla et la plupart
 * des mods ne peuvent pas animer proprement un survol. Ici, chaque carte possède ses propres
 * ressorts, qui vivent entre les frames.
 *
 * <p>Chaque carte porte trois animations indépendantes, ce qui produit le mouvement composé
 * caractéristique des UI modernes plutôt qu'un simple changement de couleur :
 * élévation (ombre), décalage horizontal, et intensité de surbrillance.
 */
public final class MainMenu {

    /** Une entrée de version dans la liste. */
    public static final class VersionCard {
        final String versionId;
        final String subtitle;
        final boolean installed;

        final Spring hoverLift = Spring.snappy(0f);      // 0 → 1 au survol
        final Spring pressScale = Spring.snappy(1f);     // enfoncement
        final Tween entrance = new Tween(0f).curve(Animations.Easing::easeOutBack);

        VersionCard(String versionId, String subtitle, boolean installed) {
            this.versionId = versionId;
            this.subtitle = subtitle;
            this.installed = installed;
        }
    }

    // Palette : sombre, faible saturation, un seul accent. La lisibilité d'un HUD superposé au
    // jeu impose des fonds très opaques — un panneau translucide devient illisible sur un ciel
    // clair ou sur de la neige.
    private static final int BG_PANEL      = 0xF01A1B1F;
    private static final int BG_CARD       = 0xFF232429;
    private static final int BG_CARD_HOVER = 0xFF2E3037;
    private static final int ACCENT        = 0xFF5B8DEF;
    private static final int TEXT_DIM      = 0xFF8A8D96;

    private final List<VersionCard> cards = new ArrayList<>();
    private final Tween screenFade = new Tween(0f).curve(Animations.Easing::easeOutExpo);
    private Consumer<String> onLaunch = v -> {};
    private int hoveredIndex = -1;
    private int selectedIndex = 0;
    private float elapsed;

    public MainMenu() {
        screenFade.to(1f, 0.35f);
    }

    public MainMenu addVersion(String id, String subtitle, boolean installed) {
        cards.add(new VersionCard(id, subtitle, installed));
        return this;
    }

    public void setLaunchHandler(Consumer<String> handler) { this.onLaunch = handler; }

    public void onMouseMove(double mx, double my, int width, int height) {
        hoveredIndex = indexAt(mx, my, width, height);
    }

    public void onClick(double mx, double my, int width, int height) {
        int idx = indexAt(mx, my, width, height);
        if (idx >= 0) {
            selectedIndex = idx;
            cards.get(idx).pressScale.snapTo(0.96f);   // départ instantané, retour élastique
            onLaunch.accept(cards.get(idx).versionId);
        }
    }

    private int indexAt(double mx, double my, int width, int height) {
        float listX = 40f;
        float listW = width * 0.34f;
        float y = 120f;
        for (int i = 0; i < cards.size(); i++) {
            float h = 64f;
            if (mx >= listX && mx <= listX + listW && my >= y && my <= y + h) return i;
            y += h + 10f;
        }
        return -1;
    }

    public void update(float dt) {
        elapsed += dt;
        screenFade.update(dt);
        for (int i = 0; i < cards.size(); i++) {
            VersionCard c = cards.get(i);
            // Entrée en cascade : chaque carte démarre décalée de 45 ms.
            if (elapsed >= Animations.staggerDelay(i, 0.045f, 0.5f)) {
                c.entrance.to(1f, 0.4f);
            }
            c.entrance.update(dt);
            c.hoverLift.target(i == hoveredIndex ? 1f : 0f);
            c.hoverLift.update(dt);
            c.pressScale.target(1f);
            c.pressScale.update(dt);
        }
    }

    public void render(UiRenderer r, int width, int height) {
        float fade = screenFade.value();

        r.rect(0, 0, width, height, 0f, withAlpha(0xFF101114, fade));

        float listX = 40f;
        float listW = width * 0.34f;
        r.rect(listX - 12f, 96f, listW + 24f, height - 140f, 16f,
                withAlpha(BG_PANEL, fade));

        float y = 120f;
        for (int i = 0; i < cards.size(); i++) {
            VersionCard c = cards.get(i);
            float appear = c.entrance.value();
            if (appear <= 0.001f) { y += 74f; continue; }

            float lift = c.hoverLift.value();
            // Le décalage d'entrée et le décalage de survol se composent : une carte survolée
            // pendant son apparition suit les deux mouvements sans à-coup, parce qu'aucune des
            // deux animations ne réinitialise l'autre.
            float slideIn = (1f - appear) * 28f;
            float hoverSlide = lift * 6f;
            float cardX = listX - slideIn + hoverSlide;
            float scale = c.pressScale.value();
            float h = 64f * scale;

            int bg = Animations.lerpColor(BG_CARD, BG_CARD_HOVER, lift);
            float shadowBlur = 6f + lift * 14f;
            float shadowAlpha = (0.25f + lift * 0.35f) * appear * fade;

            r.rect(cardX, y, listW * scale, h, 12f,
                    withAlpha(bg, appear * fade),
                    ACCENT, i == selectedIndex ? 1.5f : 0f,
                    shadowBlur, 3f + lift * 5f, shadowAlpha);

            // Pastille d'état : plein si installé, contour sinon.
            float dot = 8f;
            r.rect(cardX + 16f, y + h / 2f - dot / 2f, dot, dot, dot / 2f,
                    withAlpha(c.installed ? 0xFF4ADE80 : TEXT_DIM, appear * fade));

            // Barre d'accent animée sur la carte sélectionnée.
            if (i == selectedIndex) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(elapsed * 2.4);
                r.rect(cardX, y + 12f, 3f, h - 24f, 1.5f,
                        withAlpha(ACCENT, appear * fade * pulse));
            }

            y += 74f;
        }

        // Panneau de droite : détail de la version sélectionnée.
        float detailX = listX + listW + 40f;
        float detailW = width - detailX - 40f;
        r.rect(detailX, 96f, detailW, height - 140f, 16f, withAlpha(BG_PANEL, fade),
                0x20FFFFFF, 1f, 18f, 6f, 0.3f * fade);

        // Bouton de lancement, avec ombre portée colorée par l'accent.
        float btnW = 200f, btnH = 46f;
        r.rect(detailX + detailW - btnW - 24f, height - 110f, btnW, btnH, 10f,
                withAlpha(ACCENT, fade), 0, 0f, 16f, 6f, 0.45f * fade);

        r.flush();
    }

    private static int withAlpha(int argb, float multiplier) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(multiplier, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Note sur le texte : volontairement absent de ce POC de rendu, car il mérite sa propre
     * décision. La bonne approche pour une UI qui zoome (échelle GUI 1× à 4×) est un atlas
     * <b>MSDF</b> ({@code msdf-atlas-gen} hors ligne), échantillonné dans le fragment shader par
     * {@code median(r,g,b)} puis {@code smoothstep} avec {@code fwidth}. Une police bitmap façon
     * vanilla devient floue au-delà de 2×, et un rasterizeur dynamique (stb_truetype) impose un
     * atlas par taille. NanoVG (fourni avec LWJGL) est l'option pragmatique pour démarrer :
     * texte, chemins vectoriels et dégradés en quelques appels, au prix d'un batching moins bon
     * que celui de {@link UiRenderer}.
     */
    public List<VersionCard> cards() { return List.copyOf(cards); }
}
