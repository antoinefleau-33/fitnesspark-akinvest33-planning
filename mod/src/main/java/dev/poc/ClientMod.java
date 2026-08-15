package dev.poc;

import dev.poc.bediag.BeCollector;
import dev.poc.bediag.BeFilter;
import dev.poc.bediag.BeSnapshot;
import dev.poc.bediag.BeStats;
import dev.poc.bediag.DepthMode;
import dev.poc.bediag.DiagRenderer;
import dev.poc.musichud.MusicHud;
import dev.poc.musichud.SpotifyBridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entrée du mod. Deux fonctionnalités indépendantes :
 *
 * <ul>
 *   <li><b>Musique</b> : incrustation Spotify, alimentée par le lanceur via un serveur local.</li>
 *   <li><b>Diagnostic BlockEntity</b> : boîtes de débogage et statistiques de rendu.</li>
 * </ul>
 *
 * <p>Chacune s'active par un raccourci et reste inerte tant qu'elle n'est pas demandée : un mod de
 * débogage qui travaille en permanence fausserait les mesures qu'il produit.
 */
public final class ClientMod implements ClientModInitializer {

    public static final String MOD_ID = "poc-client";

    // -- État partagé --------------------------------------------------------------------------

    private static SpotifyBridge bridge;
    private static boolean musicVisible = true;

    private static boolean diagEnabled = false;
    private static int filterIndex = 0;
    private static BeFilter filter = BeFilter.CYCLE[0].withinBlocks(64);
    private static DepthMode depthMode = DepthMode.OCCLUDED_DIMMED;
    private static final BeStats STATS = new BeStats();
    private static List<BeSnapshot> lastScan = new ArrayList<>();
    private static int frameCounter;

    private KeyBinding keyMusicToggle;
    private KeyBinding keyMusicNext;
    private KeyBinding keyMusicPlayPause;
    private KeyBinding keyDiagToggle;
    private KeyBinding keyDiagFilter;
    private KeyBinding keyDiagDepth;

    @Override
    public void onInitializeClient() {
        // Le lanceur écrit ce fichier dans le dossier de jeu au démarrage de la partie. Il
        // contient le port et le jeton du serveur local, qui changent à chaque lancement.
        Path tokenFile = FabricLoader.getInstance().getGameDir().resolve(".spotify-bridge.json");
        bridge = new SpotifyBridge(tokenFile);
        bridge.start(3);

        keyMusicToggle = register("music.toggle", GLFW.GLFW_KEY_M, "Musique");
        keyMusicPlayPause = register("music.playpause", GLFW.GLFW_KEY_P, "Musique");
        keyMusicNext = register("music.next", GLFW.GLFW_KEY_PERIOD, "Musique");
        keyDiagToggle = register("diag.toggle", GLFW.GLFW_KEY_B, "Diagnostic");
        keyDiagFilter = register("diag.filter", GLFW.GLFW_KEY_N, "Diagnostic");
        keyDiagDepth = register("diag.depth", GLFW.GLFW_KEY_COMMA, "Diagnostic");

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (musicVisible) MusicHud.render(context, bridge.state());
            if (diagEnabled) DiagnosticsHud.render(context, STATS, filter, depthMode);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRenderWorld);
    }

    private static KeyBinding register(String name, int key, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + "." + name,
                InputUtil.Type.KEYSYM, key,
                "category." + MOD_ID + "." + category.toLowerCase()));
    }

    // -- Entrées -------------------------------------------------------------------------------

    private void onTick(MinecraftClient client) {
        while (keyMusicToggle.wasPressed()) musicVisible = !musicVisible;
        while (keyMusicPlayPause.wasPressed()) bridge.playPause();
        while (keyMusicNext.wasPressed()) bridge.next();

        while (keyDiagToggle.wasPressed()) {
            diagEnabled = !diagEnabled;
            if (!diagEnabled) lastScan = new ArrayList<>();
        }
        while (keyDiagFilter.wasPressed()) {
            filterIndex = (filterIndex + 1) % BeFilter.CYCLE.length;
            filter = BeFilter.CYCLE[filterIndex].withinBlocks(64);
        }
        while (keyDiagDepth.wasPressed()) {
            depthMode = depthMode.nextMode();
        }
    }

    // -- Rendu monde ---------------------------------------------------------------------------

    private void onRenderWorld(WorldRenderContext context) {
        if (!diagEnabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        long start = System.nanoTime();

        // La collecte ne se refait pas à chaque frame : elle parcourt des milliers d'entrées, et
        // à 200 fps ce serait l'outil qui coûterait plus cher que ce qu'il mesure. Une fois tous
        // les 10 rendus suffit largement pour l'œil.
        if (frameCounter % 10 == 0) {
            lastScan = BeCollector.collect(client, 64);
            STATS.reset();
            for (BeSnapshot be : lastScan) STATS.record(be, filter.test(be));
        }
        frameCounter++;
        if (frameCounter % 600 == 0) STATS.compact();

        DiagRenderer.renderBoxes(context.matrixStack(), context.camera().getPos(),
                                 lastScan, filter, depthMode, 4000);

        STATS.recordFrameNanos(System.nanoTime() - start);
        STATS.drawn = Math.min(STATS.matched, 4000);
    }

    public static SpotifyBridge bridge() {
        return bridge;
    }
}
