package dev.poc.modules.bediag;

import dev.poc.api.event.GameEvents;
import dev.poc.api.input.ActivationContext;
import dev.poc.api.input.ActivationMode;
import dev.poc.api.input.Chord;
import dev.poc.api.input.Keybind;
import dev.poc.api.module.ClientModule;
import dev.poc.api.module.GameBridge;
import dev.poc.api.module.ModuleContext;
import dev.poc.api.render.WorldRenderer;

/**
 * Diagnostic de rendu et de chargement des BlockEntity.
 *
 * <p>Répond à trois questions concrètes :
 * <ol>
 *   <li><b>Combien, et de quel type ?</b> Histogramme par type, avec la part qui possède un
 *       {@code BlockEntityRenderer} — c'est cette part qui coûte à chaque frame.</li>
 *   <li><b>Où sont les concentrations ?</b> Contour des sections de chunk dépassant un seuil. Une
 *       ferme à hoppers concentre des centaines de BE dans deux chunks, et aucune moyenne globale
 *       ne le révèle.</li>
 *   <li><b>Le culling fonctionne-t-il ?</b> Comptage des BE avec renderer hors distance de rendu.
 *       Une valeur élevée signale un {@code getViewDistance} mal implémenté.</li>
 * </ol>
 *
 * <p>L'outil mesure aussi <b>son propre coût</b> et l'affiche. Un diagnostic de performance qui ne
 * s'inclut pas dans la mesure ment sur ce qu'il observe : à 20 000 BlockEntity, une itération
 * naïve par frame coûte plus cher que le rendu qu'elle prétend analyser.
 */
public final class BlockEntityDiagnosticsModule implements ClientModule {

    // Palette par état. Couleurs choisies pour rester distinguables sur terrain clair comme
    // sombre, et pour ne pas dépendre du rouge/vert seul (lisibilité en deutéranopie).
    private static final int COLOR_RENDERER      = 0xFF4FC3F7;   // bleu clair : a un BER
    private static final int COLOR_TICKING       = 0xFFFFB300;   // ambre : ticke
    private static final int COLOR_BOTH          = 0xFFE040FB;   // magenta : les deux
    private static final int COLOR_PLAIN         = 0xFF9E9E9E;   // gris : ni l'un ni l'autre
    private static final int COLOR_OUT_OF_RANGE  = 0xFFFF5252;   // rouge : BER hors portée
    private static final int COLOR_HOTSPOT       = 0xFFFFEB3B;   // jaune : chunk saturé

    private static final int HOTSPOT_THRESHOLD = 24;
    private static final double DEFAULT_RANGE_BLOCKS = 64.0;
    private static final int MAX_BOXES_PER_FRAME = 4000;

    private final BeStats stats = new BeStats();

    private boolean enabled;
    private boolean showChunkHotspots = true;
    private int filterIndex;
    private BeFilter filter = BeFilter.CYCLE[0].withinBlocks(DEFAULT_RANGE_BLOCKS);
    private WorldRenderer.DepthMode depthMode = WorldRenderer.DepthMode.OCCLUDED_DIMMED;
    private int frameCounter;

    @Override
    public void onLoad(ModuleContext ctx) {
        this.context = ctx;

        ctx.keybinds().register(
                Keybind.builder("be-diagnostics:toggle")
                        .displayName("Diagnostic BlockEntity")
                        .category("debug")
                        .defaultChord(Chord.key(66, Chord.Modifier.CTRL, Chord.Modifier.SHIFT))
                        .mode(ActivationMode.TOGGLE)
                        .context(ActivationContext.IN_GAME)
                        .build(),
                e -> {
                    enabled = e.toggleState();
                    ctx.log(System.Logger.Level.INFO, "diagnostic {0}",
                            enabled ? "activé" : "désactivé");
                });

        ctx.keybinds().register(
                Keybind.builder("be-diagnostics:cycle-filter")
                        .displayName("Filtre suivant")
                        .category("debug")
                        .defaultChord(Chord.key(67, Chord.Modifier.CTRL, Chord.Modifier.SHIFT))
                        .context(ActivationContext.IN_GAME)
                        .build(),
                e -> {
                    filterIndex = (filterIndex + 1) % BeFilter.CYCLE.length;
                    filter = BeFilter.CYCLE[filterIndex].withinBlocks(DEFAULT_RANGE_BLOCKS);
                    ctx.log(System.Logger.Level.INFO, "filtre : {0}", filter.label());
                });

        // Le mode « à travers les murs » est proposé, mais le renderer le dégrade de lui-même
        // hors solo. Le module n'a pas à connaître cette règle — elle est appliquée en un seul
        // point, ce qui évite qu'un futur module l'oublie ou la contourne par inadvertance.
        ctx.keybinds().register(
                Keybind.builder("be-diagnostics:cycle-depth")
                        .displayName("Mode d'occlusion")
                        .category("debug")
                        .defaultChord(Chord.key(68, Chord.Modifier.CTRL, Chord.Modifier.SHIFT))
                        .context(ActivationContext.IN_GAME)
                        .build(),
                e -> {
                    var modes = WorldRenderer.DepthMode.values();
                    depthMode = modes[(depthMode.ordinal() + 1) % modes.length];
                    ctx.log(System.Logger.Level.INFO, "occlusion : {0}", depthMode);
                });

        ctx.events().subscribe(GameEvents.RenderWorld.class, this::onRenderWorld);
        ctx.events().subscribe(GameEvents.RenderHud.class, this::onRenderHud);
    }

    private void onRenderWorld(GameEvents.RenderWorld event) {
        if (!enabled || context == null) return;
        // Le pont est redemandé à chaque frame : il devient invalide à chaque bascule de version,
        // et le mettre en champ produirait un accès à une session morte.
        GameBridge game = context.game().orElse(null);
        if (game == null) return;

        WorldRenderer r = event.renderer;

        long start = System.nanoTime();
        stats.reset();

        // Compteur capturé dans une variable locale plutôt qu'un champ : le visiteur est appelé
        // des dizaines de milliers de fois, et un accès de champ par appel se voit au profileur.
        int[] drawn = {0};

        game.forEachBlockEntity(be -> {
            boolean match = filter.test(be);
            stats.record(be, match);
            // Plafond de dessin, mais le comptage continue : tronquer l'affichage sans tronquer
            // la mesure, sinon le diagnostic sous-estimerait exactement les scènes qui posent
            // problème. Le HUD signale la troncature.
            if (!match || drawn[0] >= MAX_BOXES_PER_FRAME) return;

            r.blockBox(be.x(), be.y(), be.z(), 0.002, colorFor(be), depthMode);
            drawn[0]++;
        });

        stats.drawn = drawn[0];
        stats.endCollect(System.nanoTime() - start);

        if (showChunkHotspots) renderHotspots(r);

        // Purge périodique : sans elle, les maps accumulent une entrée par chunk visité depuis le
        // début de la session, et la mémoire monte lentement pendant une exploration longue.
        if (++frameCounter % 600 == 0) stats.compact();
    }

    /**
     * Contour des sections de chunk saturées. Dessiné en {@code OCCLUDED_DIMMED} quel que soit le
     * mode courant : un volume 16×16×16 en plein écran et sans occlusion masque tout le reste.
     */
    private void renderHotspots(WorldRenderer r) {
        for (long[] entry : stats.hotspotChunks(HOTSPOT_THRESHOLD, 12)) {
            int cx = BeStats.chunkX(entry[0]);
            int cz = BeStats.chunkZ(entry[0]);
            // Colonne entière plutôt que section : la position Y des BE d'un même chunk est
            // rarement groupée, et une colonne se repère de loin.
            r.box(cx * 16.0, -64, cz * 16.0, cx * 16.0 + 16, 320, cz * 16.0 + 16,
                    COLOR_HOTSPOT, WorldRenderer.DepthMode.OCCLUDED_DIMMED);
        }
    }

    private static int colorFor(GameBridge.BlockEntitySnapshot be) {
        if (be.hasRenderer() && !be.inViewDistance()) return COLOR_OUT_OF_RANGE;
        if (be.hasRenderer() && be.ticking()) return COLOR_BOTH;
        if (be.hasRenderer()) return COLOR_RENDERER;
        if (be.ticking()) return COLOR_TICKING;
        return COLOR_PLAIN;
    }

    private void onRenderHud(GameEvents.RenderHud event) {
        if (!enabled) return;
        var hud = event.hud;

        int x = 8, y = 8, line = 11;
        hud.roundedRect(x - 4, y - 4, 250, 108, 5, 0xC0101114);

        hud.text(x, y, "BlockEntity — " + filter.label(), 0xFFE6E8EC);
        y += line + 3;
        hud.text(x, y, "chargées      %d".formatted(stats.total), 0xFFB0B4BC);
        y += line;
        hud.text(x, y, "retenues      %d".formatted(stats.matched), 0xFFB0B4BC);
        y += line;
        hud.text(x, y, "avec renderer %d".formatted(stats.withRenderer), COLOR_RENDERER);
        y += line;
        hud.text(x, y, "ticking       %d".formatted(stats.ticking), COLOR_TICKING);
        y += line;
        hud.text(x, y, "hors portée   %d".formatted(stats.culled),
                stats.culled > 0 ? COLOR_OUT_OF_RANGE : 0xFFB0B4BC);
        y += line;

        // Coût de l'outil : affiché en permanence, et en rouge s'il devient significatif.
        double micros = stats.averageCollectMicros();
        hud.text(x, y, "coût outil    %.0f µs%s".formatted(
                        micros, stats.drawn >= MAX_BOXES_PER_FRAME ? " (tronqué)" : ""),
                micros > 1000 ? COLOR_OUT_OF_RANGE : 0xFF6E7178);
        y += line + 4;

        for (var t : stats.topTypes(4)) {
            String shortId = t.typeId.startsWith("minecraft:") ? t.typeId.substring(10) : t.typeId;
            hud.text(x, y, "  %-18s %4d".formatted(shortId, t.total), 0xFF8A8D96);
            y += line;
        }
    }

    /**
     * Le contexte, lui, peut être conservé : c'est un scope, révoqué et neutralisé au déchargement
     * du module. C'est précisément le {@code GameBridge} qu'il ne faut pas retenir.
     */
    private ModuleContext context;

    @Override
    public void onEnable(ModuleContext ctx) {
        this.context = ctx;
    }

    @Override
    public void onDisable(ModuleContext ctx) {
        enabled = false;
        this.context = null;
    }
}
