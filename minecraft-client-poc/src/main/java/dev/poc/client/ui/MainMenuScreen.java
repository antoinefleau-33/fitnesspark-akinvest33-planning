package dev.poc.client.ui;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;

/**
 * Custom main menu: animated backdrop, staggered entrance, and a status pill that doubles as the
 * version switcher's progress indicator.
 *
 * <p>Layout is computed every frame from the framebuffer size rather than cached. At this widget
 * count it costs nothing, and it removes the entire class of "resized the window, the UI is now
 * wrong" bugs — including the fullscreen toggle, which is where they usually surface.
 */
public final class MainMenuScreen {

    /** Supplies live text for the status pill. Implemented by the client against the coordinator. */
    public interface Status {
        String versionLabel();

        String detail();

        /** 0..1 while a version swap is running, negative when idle. */
        float progress();
    }

    private final List<AnimatedButton> buttons = new ArrayList<>();
    private final Animated titleEntrance = Animated.tween(0f, 0.6f, Easings.OUT_EXPO);
    private final Status status;

    private Theme theme;
    private float elapsed;
    private double mouseX;
    private double mouseY;

    public MainMenuScreen(Theme theme, Status status) {
        this.theme = theme;
        this.status = status;
        this.titleEntrance.target(1f);
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public MainMenuScreen add(AnimatedButton button) {
        buttons.add(button);
        return this;
    }

    public void onCursorMove(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    public boolean onMouseButton(int button, boolean pressed) {
        for (AnimatedButton widget : buttons) {
            if (widget.onMouseButton(mouseX, mouseY, button, pressed)) {
                return true;
            }
        }
        return false;
    }

    public void render(NanoVgRenderer gfx, int width, int height, float deltaSeconds) {
        elapsed += deltaSeconds;
        layout(width, height);

        drawBackdrop(gfx, width, height);
        drawTitle(gfx, width, height, deltaSeconds);

        for (AnimatedButton button : buttons) {
            button.update(deltaSeconds, mouseX, mouseY);
            button.render(gfx, theme);
        }

        drawStatusPill(gfx, width, height);
    }

    private void layout(int width, int height) {
        float buttonWidth = Math.min(320f, width * 0.32f);
        float buttonHeight = 54f;
        float gap = theme.spacing();
        float totalHeight = buttons.size() * buttonHeight + (buttons.size() - 1) * gap;
        float startY = height / 2f - totalHeight / 2f + 40f;
        float x = width / 2f - buttonWidth / 2f;

        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setBounds(x, startY + i * (buttonHeight + gap), buttonWidth, buttonHeight);
        }
    }

    private void drawBackdrop(NanoVgRenderer gfx, int width, int height) {
        gfx.verticalGradient(0, 0, width, height, 0f, theme.backgroundAccent(), theme.background());

        // Two slow counter-drifting glows. Sine-driven so it loops forever without bookkeeping,
        // and cheap enough to leave running behind the game as well as on the menu.
        float driftX = width * (0.5f + 0.22f * (float) Math.sin(elapsed * 0.13f));
        float driftY = height * (0.35f + 0.12f * (float) Math.cos(elapsed * 0.17f));
        gfx.circle(driftX, driftY, Math.max(width, height) * 0.28f,
                NanoVgRenderer.withAlpha(theme.accent(), 0.06f));

        float driftX2 = width * (0.5f - 0.18f * (float) Math.sin(elapsed * 0.09f + 1.2f));
        float driftY2 = height * (0.72f + 0.10f * (float) Math.sin(elapsed * 0.11f));
        gfx.circle(driftX2, driftY2, Math.max(width, height) * 0.22f,
                NanoVgRenderer.withAlpha(theme.accent(), 0.04f));
    }

    private void drawTitle(NanoVgRenderer gfx, int width, int height, float deltaSeconds) {
        float appear = titleEntrance.update(deltaSeconds);
        gfx.globalAlpha(appear);
        float baseY = height / 2f - 150f + (1f - appear) * 14f;
        gfx.text(width / 2f, baseY, 44f, theme.textPrimary(), "POC CLIENT",
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        gfx.text(width / 2f, baseY + 34f, 13f, theme.textSecondary(),
                "modular runtime · hot version switching",
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        gfx.globalAlpha(1f);
    }

    private void drawStatusPill(NanoVgRenderer gfx, int width, int height) {
        float pillWidth = 232f;
        float pillHeight = 46f;
        float x = theme.spacing() * 2f;
        float y = height - pillHeight - theme.spacing() * 2f;

        gfx.dropShadow(x, y, pillWidth, pillHeight, theme.radius(), 20f,
                NanoVgRenderer.withAlpha(0x000000FF, 0.30f));
        gfx.roundedRect(x, y, pillWidth, pillHeight, theme.radius(), theme.surface());
        gfx.roundedRectOutline(x + 0.5f, y + 0.5f, pillWidth - 1f, pillHeight - 1f,
                theme.radius(), 1f, theme.outline());

        float progress = status.progress();
        // Steady dot when idle, pulsing while a swap is running — readable at a glance.
        float dotAlpha = progress < 0f ? 1f
                : 0.45f + 0.55f * (float) Math.abs(Math.sin(elapsed * 4.0));
        gfx.circle(x + 16f, y + pillHeight / 2f, 4f,
                NanoVgRenderer.withAlpha(theme.accent(), dotAlpha));

        gfx.text(x + 30f, y + pillHeight * 0.36f, 14f, theme.textPrimary(),
                status.versionLabel(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        gfx.text(x + 30f, y + pillHeight * 0.68f, 11f, theme.textSecondary(),
                status.detail(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        if (progress >= 0f) {
            float trackX = x + 12f;
            float trackWidth = pillWidth - 24f;
            gfx.roundedRect(trackX, y + pillHeight - 6f, trackWidth, 2f, 1f,
                    NanoVgRenderer.withAlpha(theme.textSecondary(), 0.25f));
            gfx.roundedRect(trackX, y + pillHeight - 6f, trackWidth * Easings.clamp01(progress),
                    2f, 1f, theme.accent());
        }
    }
}
