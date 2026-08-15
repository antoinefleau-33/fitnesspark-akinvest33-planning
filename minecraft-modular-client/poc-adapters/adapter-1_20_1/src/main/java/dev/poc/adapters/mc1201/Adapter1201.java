package dev.poc.adapters.mc1201;

import dev.poc.api.game.GameAdapter;
import dev.poc.api.game.GameEnvironment;
import dev.poc.api.module.GameBridge;

/**
 * Adaptateur 1.20.x — squelette montrant la forme attendue.
 *
 * <p>Ce module se compile <b>contre le client 1.20.1 remappé</b>, pas contre le shell. Il est le
 * seul endroit du projet autorisé à importer {@code net.minecraft.*}. Les imports sont laissés en
 * commentaire ici pour que le POC compile sans le jar du jeu.
 *
 * <p>Découverte via {@code ServiceLoader} : le jar déclare
 * {@code META-INF/services/dev.poc.api.game.GameAdapter} avec le nom de cette classe. Le
 * {@code VersionSwitcher} le charge dans le classloader isolé de la version.
 *
 * <h2>Ce que fait réellement boot()</h2>
 * <ol>
 *   <li>renseigner les propriétés système attendues par le jeu ({@code java.library.path} n'est
 *       pas nécessaire, LWJGL venant du shell) ;</li>
 *   <li>construire l'équivalent de {@code net.minecraft.client.main.Main#main} <b>sans</b> sa
 *       création de fenêtre : on injecte le handle GLFW du shell dans {@code WindowManager} via un
 *       mixin, au lieu de laisser {@code Window} en créer un second ;</li>
 *   <li>instancier {@code Minecraft} et retourner un {@link GameBridge} qui lit ses champs ;</li>
 *   <li>enregistrer les executors et pools dans {@code env.registerSessionResource} pour qu'ils
 *       soient arrêtés à la bascule.</li>
 * </ol>
 *
 * <h2>Le piège des statiques</h2>
 * {@code Minecraft.getInstance()} est un singleton statique. Il n'est pas gênant ici, parce qu'il
 * vit dans le classloader de la version : abandonner ce classloader le fait disparaître avec le
 * reste. Il le deviendrait immédiatement si le shell en gardait une référence — d'où la règle
 * absolue : le shell ne manipule jamais que {@code GameAdapter} et {@code GameBridge}.
 */
public final class Adapter1201 implements GameAdapter {

    private Object minecraft;          // net.minecraft.client.Minecraft
    private volatile boolean running;

    @Override
    public String versionId() { return "1.20.1"; }

    @Override
    public String family() { return "1.20"; }

    @Override
    public GameBridge boot(GameEnvironment env) throws Exception {
        // Réel :
        //   RunArgs args = buildRunArgs(env);
        //   WindowHandleInjector.set(env.windowHandle());   // mixin sur com.mojang.blaze3d.platform.Window
        //   this.minecraft = new Minecraft(args);
        //   env.registerSessionResource(() -> Util.shutdownExecutors());
        this.running = true;
        return new Bridge1201(env);
    }

    @Override
    public void tick() {
        if (!running) return;
        // ((Minecraft) minecraft).tick();
    }

    @Override
    public void render(float partialTicks) {
        if (!running) return;
        // ((Minecraft) minecraft).gameRenderer.render(partialTicks, ...);
    }

    @Override
    public void shutdown() {
        if (!running) return;
        running = false;
        // Ordre imposé : déconnexion réseau → arrêt des executors → libération des ressources de
        // rendu → close() du LevelStorage. Court-circuiter la déconnexion laisse un thread Netty
        // vivant, qui retient à lui seul tout le classloader.
        //
        //   mc.level = null; mc.getConnection().getConnection().disconnect(...);
        //   Util.shutdownExecutors();
        //   mc.getMainRenderTarget().destroyBuffers();
        //   mc.getTextureManager().close();
        minecraft = null;
    }

    /** Implémentation du pont : lecture des champs du jeu, traduits en types du JDK. */
    private static final class Bridge1201 implements GameBridge {
        private final GameEnvironment env;

        Bridge1201(GameEnvironment env) { this.env = env; }

        @Override public String versionId() { return "1.20.1"; }
        @Override public String versionFamily() { return "1.20"; }

        @Override
        public LocalPlayer player() {
            // Réel : var p = Minecraft.getInstance().player;
            return new LocalPlayer() {
                @Override public double x() { return 0; }
                @Override public double y() { return 0; }
                @Override public double z() { return 0; }
                @Override public float yaw() { return 0; }
                @Override public float pitch() { return 0; }
                @Override public float health() { return 20f; }
                @Override public boolean onGround() { return true; }
                @Override public String name() { return "?"; }
            };
        }

        @Override
        public World world() {
            return new World() {
                @Override public String dimensionId() { return "minecraft:overworld"; }
                @Override public long dayTime() { return 0; }
                @Override public int playerCount() { return 1; }
                @Override public String serverAddress() { return null; }
            };
        }

        @Override
        public Hud hud() {
            // Réel : délégation vers GuiGraphics de la frame courante.
            return new Hud() {
                @Override public int width() { return env.framebufferWidth(); }
                @Override public int height() { return env.framebufferHeight(); }
                @Override public void text(float x, float y, String t, int c) {}
                @Override public void rect(float x, float y, float w, float h, int c) {}
                @Override public void roundedRect(float x, float y, float w, float h,
                                                  float r, int c) {}
            };
        }

        @Override public boolean isScreenOpen() { return false; }
        @Override public boolean isTextInputFocused() { return false; }

        /**
         * Solo strict. {@code hasSingleplayerServer()} seul ne suffit pas : un monde solo
         * « ouvert au LAN » satisfait cette condition tout en acceptant des joueurs distants.
         * {@code isPublished()} est la partie qui compte, et elle bascule en cours de partie —
         * d'où l'évaluation à chaque frame plutôt qu'une mise en cache au démarrage.
         *
         * <pre>{@code
         * Minecraft mc = Minecraft.getInstance();
         * IntegratedServer server = mc.getSingleplayerServer();
         * return mc.hasSingleplayerServer()
         *         && server != null
         *         && !server.isPublished()
         *         && mc.getCurrentServer() == null;
         * }</pre>
         */
        @Override
        public boolean isSingleplayer() {
            return false;   // conservateur tant que l'adaptateur n'est pas câblé
        }

        /**
         * Caméra + matrice view-projection <b>relative à la caméra</b>.
         *
         * <pre>{@code
         * Camera cam = mc.gameRenderer.getMainCamera();
         * Vec3 pos = cam.getPosition();
         * // event fournit poseStack (déjà translaté de -camPos par le renderer) et projection
         * Matrix4f vp = new Matrix4f(event.getProjectionMatrix())
         *         .mul(event.getPoseStack().last().pose());
         * float[] out = new float[16];
         * vp.get(out);   // JOML écrit en column-major, ce qu'attend glUniformMatrix4fv
         * return new Camera(pos.x, pos.y, pos.z, cam.getYRot(), cam.getXRot(), out);
         * }</pre>
         *
         * <p>Attention au sens de la multiplication : {@code projection.mul(modelView)}, pas
         * l'inverse. Inversé, les boîtes suivent la rotation de la tête au lieu de rester ancrées
         * dans le monde — l'erreur est visuellement déroutante et facile à mal diagnostiquer.
         */
        @Override
        public Camera camera() {
            return new Camera(0, 0, 0, 0f, 0f,
                    new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1});
        }

        /**
         * Parcours des BlockEntity chargées côté client.
         *
         * <p>Il n'existe pas de liste globale côté client : il faut passer par les chunks. En
         * 1.20.1, {@code ClientChunkCache.storage} est privé, d'où un mixin accesseur —
         * l'alternative, {@code level.getChunk(x, z)} sur toute la grille de distance de rendu,
         * force le chargement de chunks vides et fausse précisément la mesure qu'on veut prendre.
         *
         * <pre>{@code
         * ClientLevel level = mc.level;
         * BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
         * Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
         *
         * for (LevelChunk chunk : ((ClientChunkCacheAccessor) level.getChunkSource()).storage()) {
         *     if (chunk == null) continue;
         *     for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
         *         BlockPos p = e.getKey();
         *         BlockEntity be = e.getValue();
         *
         *         BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(be);
         *         boolean hasRenderer = renderer != null;
         *         // shouldRender() intègre getViewDistance() ET le test spécifique du BER
         *         // (un panneau se cull plus tôt qu'un coffre) : c'est le vrai prédicat de rendu.
         *         boolean inRange = hasRenderer && renderer.shouldRender(be, camPos);
         *
         *         // Ticking : le bloc doit être un EntityBlock ET fournir un ticker côté client.
         *         // Beaucoup de BE ont un ticker serveur et aucun client — les confondre
         *         // surestime massivement la charge client (les hoppers en sont l'exemple type).
         *         boolean ticking = be.getBlockState().getBlock() instanceof EntityBlock eb
         *                 && eb.getTicker(level, be.getBlockState(), be.getType()) != null;
         *
         *         visitor.visit(new BlockEntitySnapshot(
         *                 p.getX(), p.getY(), p.getZ(),
         *                 BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString(),
         *                 hasRenderer, ticking, inRange,
         *                 chunk.getPos().x, chunk.getPos().z,
         *                 camPos.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
         *     }
         * }
         * }</pre>
         *
         * <p>Allocation : ce bloc crée un {@code BlockEntitySnapshot} par BE et par frame. Sur un
         * monde à 20 000 BE c'est ~1,2 Mo/frame, soit 70 Mo/s d'ordures — assez pour provoquer des
         * pauses GC que l'outil attribuerait ensuite au jeu. En production, réutiliser une
         * instance mutable passée au visiteur, ou ne recollecter qu'une frame sur quatre en
         * conservant l'agrégat.
         */
        @Override
        public void forEachBlockEntity(BlockEntityVisitor visitor) {
            // Sans jeu chargé, rien à visiter.
        }
    }
}
