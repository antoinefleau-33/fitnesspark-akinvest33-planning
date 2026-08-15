package dev.poc.client.ui;

/**
 * Colour and metric tokens for the whole interface, packed {@code 0xRRGGBBAA}.
 *
 * <p>Widgets read tokens and never literals, so a user theme is one record swap and a repaint —
 * including the accent, which is the one colour most users actually want to change. Keeping radius
 * and spacing in here too means a "compact" theme is possible without touching layout code.
 */
public record Theme(String name,
                    int background,
                    int backgroundAccent,
                    int surface,
                    int surfaceHover,
                    int surfaceActive,
                    int outline,
                    int textPrimary,
                    int textSecondary,
                    int accent,
                    int accentSoft,
                    int danger,
                    float radius,
                    float spacing) {

    public static final Theme MIDNIGHT = new Theme(
            "Midnight",
            0x0B0E14FF,
            0x141A26FF,
            0x161B26E6,
            0x1E2532F2,
            0x27303FFF,
            0xFFFFFF14,
            0xF2F5FAFF,
            0x8A93A6FF,
            0x5B8DEFFF,
            0x5B8DEF33,
            0xE5484DFF,
            10f,
            12f);

    public static final Theme AURORA = new Theme(
            "Aurora",
            0x0A1014FF,
            0x0F1A1CFF,
            0x122024E6,
            0x182C31F2,
            0x1F3940FF,
            0xFFFFFF14,
            0xEAF6F5FF,
            0x7E9A9AFF,
            0x2FD6A5FF,
            0x2FD6A533,
            0xE5484DFF,
            14f,
            14f);

    /** Returns a copy with a different accent — the "pick your colour" path in settings. */
    public Theme withAccent(int accent) {
        return new Theme(name, background, backgroundAccent, surface, surfaceHover, surfaceActive,
                outline, textPrimary, textSecondary, accent,
                (accent & 0xFFFFFF00) | 0x33, danger, radius, spacing);
    }
}
