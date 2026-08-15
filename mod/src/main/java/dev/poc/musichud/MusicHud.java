package dev.poc.musichud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Incrustation « en cours de lecture », en haut à droite.
 *
 * <p>Ne fait que lire {@link MusicState} : aucune requête réseau ici. Le fil de rendu ne doit
 * jamais attendre quoi que ce soit, sous peine de saccades à chaque interrogation du lanceur.
 */
public final class MusicHud {

    private static final int PANEL_BG = 0xC0101114;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int GREEN = 0xFF5EDD5E;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int TEXT_DIM = 0xFF8A93A6;

    private MusicHud() {}

    public static void render(DrawContext context, MusicState state) {
        if (!state.connected || !state.hasTrack()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();

        int width = 172;
        int height = 44;
        int x = screenWidth - width - 8;
        int y = 8;

        context.fill(x, y, x + width, y + height, PANEL_BG);
        // Liseré d'accent à gauche : repère visuel discret, dans l'esprit des HUD de client.
        context.fill(x, y, x + 2, y + height, state.playing ? GREEN : ACCENT);

        String title = truncate(client, state.title, width - 16);
        String artist = truncate(client, state.artist, width - 16);

        context.drawTextWithShadow(client.textRenderer, Text.literal(title), x + 8, y + 6, TEXT);
        context.drawTextWithShadow(client.textRenderer, Text.literal(artist), x + 8, y + 18,
                                   TEXT_DIM);

        if (state.durationMs > 0) {
            int barX = x + 8;
            int barY = y + height - 10;
            int barWidth = width - 16;
            context.fill(barX, barY, barX + barWidth, barY + 3, 0xFF2A3344);
            int filled = (int) (barWidth * state.progress());
            context.fill(barX, barY, barX + filled, barY + 3, GREEN);
        }
    }

    /** Tronque au pixel près plutôt qu'au nombre de caractères : les glyphes ont des largeurs variables. */
    private static String truncate(MinecraftClient client, String text, int maxWidth) {
        if (client.textRenderer.getWidth(text) <= maxWidth) return text;
        String trimmed = client.textRenderer.trimToWidth(text, maxWidth - 8);
        return trimmed + "...";
    }
}
