package dev.poc.ui.render;

import dev.poc.api.module.GameBridge;
import dev.poc.api.render.WorldRenderer;

import java.util.function.BooleanSupplier;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Implémentation de {@link WorldRenderer}, branchée sur l'événement {@code RenderWorld}.
 *
 * <h2>Ne jamais appeler glEnable/glDisable directement dans Minecraft moderne</h2>
 * C'est le piège le plus coûteux de cette fonctionnalité, et il ne se manifeste pas là où on le
 * provoque. Depuis 1.17, Blaze3D maintient un <b>cache logiciel de l'état GL</b>
 * ({@code com.mojang.blaze3d.systems.RenderSystem}) et évite les appels pilote redondants en
 * comparant à ce cache. Un {@code GL11.glDisable(GL_DEPTH_TEST)} brut modifie l'état réel sans
 * mettre le cache à jour : Blaze3D croit encore le test actif, ne le réactive donc pas quand il en
 * a besoin, et le symptôme apparaît quelques frames plus tard dans du code sans rapport — HUD qui
 * disparaît, entités qui traversent les murs, particules mal triées. Des heures de débogage pour
 * une ligne.
 *
 * <p>La règle : passer par {@code RenderSystem.disableDepthTest()} / {@code enableDepthTest()},
 * qui font l'appel GL <em>et</em> mettent le cache à jour. Idem pour le blending
 * ({@code RenderSystem.enableBlend()}), le depth mask et la couleur du shader. Sur 1.8.9 le même
 * problème existe avec {@code GlStateManager}.
 *
 * <p>Comme ce module ne peut pas dépendre de Minecraft (il vit dans le shell, pas dans le
 * classloader de version), l'accès passe par {@link GlStateBridge}, que l'adaptateur de version
 * implémente avec les appels adéquats. C'est aussi ce qui rend le code portable de 1.8.9 à 1.20.1
 * malgré le changement complet d'API de rendu.
 */
public final class DebugWorldRenderer implements WorldRenderer, AutoCloseable {

    /**
     * Pont vers le gestionnaire d'état du jeu. Implémenté par l'adaptateur :
     * {@code RenderSystem.*} en 1.17+, {@code GlStateManager.*} en 1.8.9–1.16.
     */
    public interface GlStateBridge {
        void pushState();
        void popState();
        void setDepthTest(boolean enabled);
        void setDepthMask(boolean enabled);
        void setBlend(boolean enabled);
        void setCull(boolean enabled);

        /** Bridge neutre pour les tests hors jeu : appels GL bruts. */
        static GlStateBridge raw() {
            return new GlStateBridge() {
                @Override public void pushState() {}
                @Override public void popState() {}
                @Override public void setDepthTest(boolean e) {
                    if (e) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
                }
                @Override public void setDepthMask(boolean e) { glDepthMask(e); }
                @Override public void setBlend(boolean e) {
                    if (e) glEnable(GL_BLEND); else glDisable(GL_BLEND);
                }
                @Override public void setCull(boolean e) {
                    if (e) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
                }
            };
        }
    }

    private final BoxRenderer occluded = new BoxRenderer();
    private final BoxRenderer dimmed = new BoxRenderer();
    private final BoxRenderer through = new BoxRenderer();

    private final GlStateBridge state;
    private final BooleanSupplier singleplayer;
    private final System.Logger log = System.getLogger("debug-render");

    private float[] viewProjection = identity();
    private double camX, camY, camZ;
    private boolean warnedAboutDowngrade;

    /**
     * @param singleplayer évalué à chaque frame — un monde peut être ouvert au LAN en cours de
     *                     partie, donc mettre le résultat en cache serait faux
     */
    public DebugWorldRenderer(GlStateBridge state, BooleanSupplier singleplayer) {
        this.state = state == null ? GlStateBridge.raw() : state;
        this.singleplayer = singleplayer == null ? () -> false : singleplayer;
    }

    /** Appelé par le shell au début de l'événement de rendu monde. */
    public void beginFrame(GameBridge.Camera camera) {
        this.viewProjection = camera.viewProjectionMatrix();
        this.camX = camera.x();
        this.camY = camera.y();
        this.camZ = camera.z();
        occluded.begin();
        dimmed.begin();
        through.begin();
    }

    /**
     * Résolution du mode d'occlusion. {@code THROUGH_WALLS} n'est honoré qu'en solo ; ailleurs il
     * dégrade en double passe atténuée, qui reste parfaitement lisible pour du diagnostic.
     */
    private DepthMode resolve(DepthMode requested) {
        DepthMode effective = DepthMode.resolve(requested, singleplayer.getAsBoolean());
        if (effective != requested && !warnedAboutDowngrade) {
            warnedAboutDowngrade = true;
            log.log(System.Logger.Level.INFO,
                    "THROUGH_WALLS dégradé en OCCLUDED_DIMMED : monde non solo");
        }
        return effective;
    }

    private BoxRenderer targetFor(DepthMode mode) {
        return switch (mode) {
            case OCCLUDED -> occluded;
            case OCCLUDED_DIMMED -> dimmed;
            case THROUGH_WALLS -> through;
        };
    }

    @Override
    public void box(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ,
                    int argb, DepthMode depthMode) {
        BoxRenderer target = targetFor(resolve(depthMode));
        // Passage en coordonnées relatives caméra AVANT la conversion en float : c'est la
        // soustraction en double qui préserve la précision.
        target.addRelative(
                (float) (minX - camX), (float) (minY - camY), (float) (minZ - camZ),
                (float) (maxX - minX), (float) (maxY - minY), (float) (maxZ - minZ),
                argb);
    }

    @Override
    public void line(double x1, double y1, double z1, double x2, double y2, double z2,
                     int argb, DepthMode depthMode) {
        // Une boîte dégénérée sur un axe : évite un second pipeline pour un usage marginal.
        box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), argb, depthMode);
    }

    @Override
    public int pendingPrimitives() {
        return occluded.count() + dimmed.count() + through.count();
    }

    /**
     * Émission et restauration de l'état.
     *
     * <p>Le point important est le {@code finally} : si un draw call lève (shader invalide, VAO
     * corrompu), l'état doit malgré tout être restauré. Restaurer uniquement en chemin nominal
     * laisse le jeu avec le test de profondeur désactivé dès la première erreur — l'écran devient
     * incompréhensible et la cause est invisible dans la stacktrace.
     */
    @Override
    public void flush() {
        if (pendingPrimitives() == 0) return;

        state.pushState();
        try {
            state.setBlend(true);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            // Les lignes n'ont pas de face : le culling les ferait disparaître selon l'angle.
            state.setCull(false);

            state.setDepthTest(true);
            occluded.draw(viewProjection, false, false);
            dimmed.draw(viewProjection, true, false);

            if (through.count() > 0) {
                state.setDepthTest(false);
                through.draw(viewProjection, false, true);
            }
        } finally {
            // Remise dans l'état attendu par le jeu pour la suite de la frame.
            state.setDepthTest(true);
            state.setDepthMask(true);
            state.setCull(true);
            state.setBlend(false);
            glDepthFunc(GL_LEQUAL);
            glUseProgram(0);
            state.popState();
        }
    }

    private static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    @Override
    public void close() {
        occluded.close();
        dimmed.close();
        through.close();
    }
}
