package dev.poc.bediag;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Rendu des boîtes filaires de diagnostic dans l'espace du monde.
 *
 * <h2>Ne jamais appeler glEnable/glDisable directement</h2>
 * C'est le piège le plus coûteux de cette fonctionnalité, et il ne se manifeste pas là où on le
 * provoque. Blaze3D maintient un <b>cache logiciel de l'état OpenGL</b> et évite les appels pilote
 * redondants en comparant à ce cache. Un {@code GL11.glDisable(GL_DEPTH_TEST)} brut change l'état
 * réel sans mettre le cache à jour : Blaze3D croit encore le test actif, ne le réactive donc pas
 * quand il en a besoin, et le symptôme apparaît plusieurs frames plus tard dans du code sans
 * rapport — HUD qui disparaît, entités visibles à travers les murs, particules mal triées.
 *
 * <p>D'où {@code RenderSystem.disableDepthTest()} partout ici : il fait l'appel GL <em>et</em>
 * synchronise le cache.
 *
 * <h2>Coordonnées relatives à la caméra</h2>
 * Les positions sont exprimées relativement à la caméra avant d'être converties en {@code float}.
 * Un {@code float} a 24 bits de mantisse : à x = 1 000 000, deux valeurs consécutives sont
 * distantes de ~0,06 bloc, et les boîtes tremblent visiblement loin du spawn. La soustraction doit
 * se faire en {@code double}.
 */
public final class DiagRenderer {

    private DiagRenderer() {}

    /**
     * @param requestedMode mode demandé par l'utilisateur ; il est repassé par
     *                      {@link DepthMode#resolve} avant usage
     */
    public static void renderBoxes(MatrixStack matrices, Vec3d camera,
                                   List<BeSnapshot> snapshots, BeFilter filter,
                                   DepthMode requestedMode, int maxBoxes) {

        MinecraftClient client = MinecraftClient.getInstance();
        DepthMode mode = DepthMode.resolve(requestedMode, isStrictSingleplayer(client));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Les lignes n'ont pas de face : le culling les ferait disparaître selon l'angle de vue.
        RenderSystem.disableCull();
        RenderSystem.lineWidth(1.5f);

        try {
            if (mode == DepthMode.THROUGH_WALLS) {
                RenderSystem.disableDepthTest();
                drawPass(matrices, camera, snapshots, filter, maxBoxes, 1.0f);
            } else {
                RenderSystem.enableDepthTest();
                if (mode == DepthMode.OCCLUDED_DIMMED) {
                    // Passe 1 : ce qui est CACHÉ. GL_GREATER ne garde que les fragments plus loin
                    // que le terrain déjà écrit. L'écriture de profondeur est coupée, sinon des
                    // lignes situées derrière le décor masqueraient la géométrie dessinée ensuite.
                    RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_GREATER);
                    RenderSystem.depthMask(false);
                    drawPass(matrices, camera, snapshots, filter, maxBoxes, 0.28f);
                }
                // Passe 2 : ce qui est VISIBLE, à pleine intensité.
                RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
                drawPass(matrices, camera, snapshots, filter, maxBoxes, 1.0f);
            }
        } finally {
            // Restauration dans un finally : si un appel de dessin lève, l'état doit malgré tout
            // revenir à la normale. Restaurer uniquement en chemin nominal laisse le jeu sans test
            // de profondeur dès la première erreur, et la cause est invisible dans la stacktrace.
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0f);
        }
    }

    private static void drawPass(MatrixStack matrices, Vec3d camera, List<BeSnapshot> snapshots,
                                 BeFilter filter, int maxBoxes, float alphaScale) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                                 VertexFormats.POSITION_COLOR);
        int drawn = 0;
        for (BeSnapshot be : snapshots) {
            if (!filter.test(be)) continue;
            if (drawn >= maxBoxes) break;
            drawn++;

            int color = colorFor(be);
            float a = ((color >>> 24) & 0xFF) / 255f * alphaScale;
            float r = ((color >>> 16) & 0xFF) / 255f;
            float g = ((color >>> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            // Soustraction en double AVANT la conversion en float : c'est ce qui préserve la
            // précision loin de l'origine.
            float x1 = (float) (be.x() - camera.x);
            float y1 = (float) (be.y() - camera.y);
            float z1 = (float) (be.z() - camera.z);
            box(buffer, model, x1, y1, z1, x1 + 1, y1 + 1, z1 + 1, r, g, b, a);
        }
        var built = buffer.endNullable();
        if (built != null) {
            net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    /** Les 12 arêtes d'un cube, en paires de sommets. */
    private static void box(BufferBuilder buffer, Matrix4f m,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        float[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2},
                {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
                {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2},
                {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1},
                {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2},
        };
        for (float[] e : edges) {
            buffer.vertex(m, e[0], e[1], e[2]).color(r, g, b, a);
            buffer.vertex(m, e[3], e[4], e[5]).color(r, g, b, a);
        }
    }

    /** Couleurs choisies pour rester distinguables sans dépendre du seul couple rouge/vert. */
    private static int colorFor(BeSnapshot be) {
        if (be.hasRenderer() && !be.inViewDistance()) return 0xFFFF5252;   // hors portée
        if (be.hasRenderer() && be.ticking()) return 0xFFE040FB;           // les deux
        if (be.hasRenderer()) return 0xFF4FC3F7;                           // renderer
        if (be.ticking()) return 0xFFFFB300;                               // ticker
        return 0xFF9E9E9E;
    }

    /**
     * Solo strict : serveur intégré actif <b>et</b> non publié sur le réseau local.
     *
     * <p>{@code isInSingleplayer()} seul ne suffit pas — un monde « ouvert au LAN » le satisfait
     * tout en acceptant des joueurs distants. Évalué à chaque frame, jamais mis en cache :
     * l'ouverture au LAN se fait en cours de partie.
     */
    private static boolean isStrictSingleplayer(MinecraftClient client) {
        try {
            var server = client.getServer();
            return client.isInSingleplayer()
                    && server != null
                    && !server.isRemote()
                    && client.getCurrentServerEntry() == null;
        } catch (Throwable ignored) {
            return false;   // en cas de doute, on ne traverse pas les murs
        }
    }
}
