package dev.poc;

import dev.poc.bediag.BeFilter;
import dev.poc.bediag.BeStats;
import dev.poc.bediag.DepthMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Panneau de statistiques du diagnostic BlockEntity.
 *
 * <p>Affiche en permanence le <b>coût de l'outil lui-même</b>. Un diagnostic de performance qui
 * ne s'inclut pas dans sa propre mesure ment sur ce qu'il observe.
 */
public final class DiagnosticsHud {

    private static final int BG = 0xC0101114;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int DIM = 0xFFB0B4BC;
    private static final int RENDERER = 0xFF4FC3F7;
    private static final int TICKING = 0xFFFFB300;
    private static final int WARN = 0xFFFF5252;

    private DiagnosticsHud() {}

    public static void render(DrawContext context, BeStats stats, BeFilter filter,
                              DepthMode mode) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = 6;
        int y = 6;
        int width = 214;
        int lineHeight = 10;
        int lines = 8 + Math.min(4, stats.topTypes(4).size());

        context.fill(x - 2, y - 2, x + width, y + lines * lineHeight + 8, BG);

        y = line(context, client, x, y, "BlockEntity — " + filter.label(), TEXT);
        y = line(context, client, x, y, "occlusion : " + mode.label(), DIM);
        y += 3;
        y = line(context, client, x, y, "chargées      " + stats.total, DIM);
        y = line(context, client, x, y, "retenues      " + stats.matched, DIM);
        y = line(context, client, x, y, "avec renderer " + stats.withRenderer, RENDERER);
        y = line(context, client, x, y, "ticking       " + stats.ticking, TICKING);
        y = line(context, client, x, y, "hors portée   " + stats.culled,
                 stats.culled > 0 ? WARN : DIM);

        double micros = stats.averageMicros();
        y = line(context, client, x, y,
                 String.format("coût outil    %.0f µs", micros),
                 micros > 1000 ? WARN : 0xFF6E7178);

        y += 3;
        for (BeStats.TypeCount type : stats.topTypes(4)) {
            String shortId = type.typeId.startsWith("minecraft:")
                    ? type.typeId.substring(10) : type.typeId;
            y = line(context, client, x, y,
                     String.format("  %-18s %4d", shortId, type.total), 0xFF8A8D96);
        }
    }

    private static int line(DrawContext context, MinecraftClient client,
                            int x, int y, String text, int color) {
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, color);
        return y + 10;
    }
}
