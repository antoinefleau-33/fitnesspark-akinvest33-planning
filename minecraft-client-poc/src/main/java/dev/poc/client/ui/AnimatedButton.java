package dev.poc.client.ui;

import java.util.function.Consumer;

/**
 * Menu row with hover, press and entrance animation.
 *
 * <p>Three independent animators rather than one "state" value: hover and press can overlap (a
 * click while the hover is still easing in), and the entrance is a one-shot that must not be
 * restarted by either. Collapsing them into a single progress value is the usual reason menu
 * buttons "jump" when clicked quickly.
 */
public final class AnimatedButton extends Widget {

    private final String label;
    private final String subtitle;
    private final Consumer<AnimatedButton> onClick;

    private final Animated hover = Animated.spring(0f, 260f, 28f);
    private final Animated press = Animated.spring(0f, 900f, 42f);
    private final Animated entrance;

    private boolean armed;

    public AnimatedButton(String label, String subtitle, float entranceDelay,
                          Consumer<AnimatedButton> onClick) {
        this.label = label;
        this.subtitle = subtitle;
        this.onClick = onClick;
        this.entrance = Animated.tween(0f, 0.45f, Easings.OUT_EXPO).withDelay(entranceDelay);
        this.entrance.target(1f);
    }

    @Override
    public void update(float deltaSeconds, double mouseX, double mouseY) {
        super.update(deltaSeconds, mouseX, mouseY);
        hover.target(hovered ? 1f : 0f);
        press.target(armed ? 1f : 0f);
        hover.update(deltaSeconds);
        press.update(deltaSeconds);
        entrance.update(deltaSeconds);
    }

    @Override
    public void render(NanoVgRenderer gfx, Theme theme) {
        float appear = entrance.value();
        if (appear <= 0.001f) {
            return;
        }
        float h = hover.value();
        float p = press.value();

        // Entrance: slide in from the left while fading up. Offset shrinks as appear -> 1.
        float slide = (1f - appear) * 28f;
        float drawX = x - slide + p * 2f;
        float alpha = appear;

        gfx.globalAlpha(alpha);

        if (h > 0.01f) {
            gfx.dropShadow(drawX, y, width, height, theme.radius(), 18f,
                    NanoVgRenderer.withAlpha(0x000000FF, 0.35f * h));
        }

        int surface = NanoVgRenderer.mix(theme.surface(), theme.surfaceHover(), h);
        surface = NanoVgRenderer.mix(surface, theme.surfaceActive(), p);
        gfx.roundedRect(drawX, y, width, height, theme.radius(), surface);
        gfx.roundedRectOutline(drawX + 0.5f, y + 0.5f, width - 1f, height - 1f,
                theme.radius(), 1f, theme.outline());

        // Accent bar on the left, growing with hover. Cheap, and reads instantly as "selected".
        float barHeight = height * (0.25f + 0.55f * h);
        float barY = y + (height - barHeight) / 2f;
        gfx.roundedRect(drawX + 2f, barY, 3f, barHeight, 1.5f,
                NanoVgRenderer.mix(NanoVgRenderer.withAlpha(theme.accent(), 0.35f),
                        theme.accent(), h));

        float textX = drawX + theme.spacing() + 8f + h * 4f;
        boolean hasSubtitle = subtitle != null && !subtitle.isEmpty();
        float labelY = hasSubtitle ? y + height * 0.38f : y + height / 2f;
        gfx.text(textX, labelY, 16f,
                NanoVgRenderer.mix(theme.textPrimary(), theme.accent(), h * 0.35f), label);
        if (hasSubtitle) {
            gfx.text(textX, y + height * 0.70f, 12f, theme.textSecondary(), subtitle);
        }

        gfx.globalAlpha(1f);
    }

    @Override
    public boolean onMouseButton(double mouseX, double mouseY, int button, boolean pressed) {
        if (button != 0) {
            return false;
        }
        if (pressed) {
            armed = contains(mouseX, mouseY);
            return armed;
        }
        boolean fired = armed && contains(mouseX, mouseY);
        armed = false;
        if (fired) {
            onClick.accept(this);
        }
        return fired;
    }

    public String label() {
        return label;
    }
}
