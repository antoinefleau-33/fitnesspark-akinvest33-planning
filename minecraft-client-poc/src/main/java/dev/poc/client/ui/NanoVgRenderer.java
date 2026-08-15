package dev.poc.client.ui;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgBoxGradient;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgPathWinding;
import static org.lwjgl.nanovg.NanoVG.nvgRGBAf;
import static org.lwjgl.nanovg.NanoVG.nvgResetScissor;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;
import static org.lwjgl.nanovg.NanoVG.nvgTextBounds;
import static org.lwjgl.nanovg.NanoVG.NVG_HOLE;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * Thin drawing layer over NanoVG.
 *
 * <p>Why NanoVG rather than raw GL for the interface: a client UI is almost entirely rounded
 * rectangles, gradients, soft shadows and text. Writing that against GL directly means writing a
 * text renderer and a path rasteriser, which is a project in itself. NanoVG is one C library with an
 * LWJGL binding, draws into the existing GL3 context, and costs a handful of draw calls per frame.
 *
 * <p>Vulkan is deliberately not used here. The window and context belong to the shell and are shared
 * with the game, and Minecraft is a GL application — introducing a second API means either a second
 * window or interop plumbing, for a UI that is not remotely GPU-bound.
 *
 * <p>Colours are packed {@code 0xRRGGBBAA} ints. The reusable {@link NVGColor} and {@link NVGPaint}
 * instances are allocated once: allocating them per call, inside a per-frame loop, is a real source
 * of GC pressure in this kind of code.
 */
public final class NanoVgRenderer implements AutoCloseable {

    private final long context;
    private final NVGColor colorA = NVGColor.create();
    private final NVGColor colorB = NVGColor.create();
    private final NVGPaint paint = NVGPaint.create();
    private final float[] textBounds = new float[4];

    private String defaultFont = "sans";

    private NanoVgRenderer(long context) {
        this.context = context;
    }

    /** Must be called on the thread owning the GL context, after GL capabilities are created. */
    public static NanoVgRenderer create() {
        long context = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (context == 0L) {
            throw new IllegalStateException("Could not create a NanoVG context");
        }
        return new NanoVgRenderer(context);
    }

    public long handle() {
        return context;
    }

    public boolean loadFont(String name, String path) {
        int id = nvgCreateFont(context, name, path);
        if (id == -1) {
            return false;
        }
        defaultFont = name;
        return true;
    }

    /**
     * @param pixelRatio framebuffer width / window width. Passing 1.0 on a HiDPI display is why UI
     *                   text looks soft on a Retina MacBook — the ratio is 2.0 there.
     */
    public void beginFrame(int width, int height, float pixelRatio) {
        nvgBeginFrame(context, width, height, pixelRatio);
    }

    public void endFrame() {
        nvgEndFrame(context);
    }

    public void globalAlpha(float alpha) {
        nvgGlobalAlpha(context, alpha);
    }

    public void roundedRect(float x, float y, float w, float h, float radius, int rgba) {
        nvgBeginPath(context);
        nvgRoundedRect(context, x, y, w, h, radius);
        nvgFillColor(context, color(colorA, rgba));
        nvgFill(context);
    }

    public void roundedRectOutline(float x, float y, float w, float h, float radius,
                                   float thickness, int rgba) {
        nvgBeginPath(context);
        nvgRoundedRect(context, x, y, w, h, radius);
        nvgStrokeWidth(context, thickness);
        nvgStrokeColor(context, color(colorA, rgba));
        nvgStroke(context);
    }

    public void verticalGradient(float x, float y, float w, float h, float radius,
                                 int topRgba, int bottomRgba) {
        nvgLinearGradient(context, x, y, x, y + h,
                color(colorA, topRgba), color(colorB, bottomRgba), paint);
        nvgBeginPath(context);
        nvgRoundedRect(context, x, y, w, h, radius);
        nvgFillPaint(context, paint);
        nvgFill(context);
    }

    public void horizontalGradient(float x, float y, float w, float h, float radius,
                                   int leftRgba, int rightRgba) {
        nvgLinearGradient(context, x, y, x + w, y,
                color(colorA, leftRgba), color(colorB, rightRgba), paint);
        nvgBeginPath(context);
        nvgRoundedRect(context, x, y, w, h, radius);
        nvgFillPaint(context, paint);
        nvgFill(context);
    }

    public void circle(float cx, float cy, float radius, int rgba) {
        nvgBeginPath(context);
        nvgCircle(context, cx, cy, radius);
        nvgFillColor(context, color(colorA, rgba));
        nvgFill(context);
    }

    /**
     * Soft drop shadow behind a rounded rect. The hole in the path stops the shadow from being
     * drawn under a translucent card, which would otherwise darken the card itself.
     */
    public void dropShadow(float x, float y, float w, float h, float radius, float blur, int rgba) {
        nvgBoxGradient(context, x, y + 2f, w, h, radius, blur,
                color(colorA, rgba), color(colorB, rgba & 0xFFFFFF00), paint);
        nvgBeginPath(context);
        nvgRect(context, x - blur, y - blur, w + blur * 2f, h + blur * 2f);
        nvgRoundedRect(context, x, y, w, h, radius);
        nvgPathWinding(context, NVG_HOLE);
        nvgFillPaint(context, paint);
        nvgFill(context);
    }

    public void text(float x, float y, float size, int rgba, String value) {
        text(x, y, size, rgba, value, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    public void text(float x, float y, float size, int rgba, String value, int align) {
        nvgFontSize(context, size);
        nvgFontFace(context, defaultFont);
        nvgTextAlign(context, align);
        nvgFillColor(context, color(colorA, rgba));
        nvgText(context, x, y, value);
    }

    public float textWidth(float size, String value) {
        nvgFontSize(context, size);
        nvgFontFace(context, defaultFont);
        return nvgTextBounds(context, 0f, 0f, value, textBounds);
    }

    public void pushScissor(float x, float y, float w, float h) {
        nvgSave(context);
        nvgScissor(context, x, y, w, h);
    }

    public void popScissor() {
        nvgResetScissor(context);
        nvgRestore(context);
    }

    private static NVGColor color(NVGColor target, int rgba) {
        return nvgRGBAf(
                ((rgba >> 24) & 0xFF) / 255f,
                ((rgba >> 16) & 0xFF) / 255f,
                ((rgba >> 8) & 0xFF) / 255f,
                (rgba & 0xFF) / 255f,
                target);
    }

    /** Replaces the alpha byte of a packed colour, for fading a themed colour in and out. */
    public static int withAlpha(int rgba, float alpha) {
        int a = Math.round(Easings.clamp01(alpha) * 255f);
        return (rgba & 0xFFFFFF00) | a;
    }

    /**
     * Blends two packed colours. Interpolating in sRGB like this is technically wrong — the
     * perceptually correct path is linear light or Oklab — but it is what every UI toolkit does and
     * what designers' gradients are drawn against, so matching them beats being right here.
     */
    public static int mix(int from, int to, float t) {
        float f = Easings.clamp01(t);
        int r = Math.round(Easings.lerp((from >> 24) & 0xFF, (to >> 24) & 0xFF, f));
        int g = Math.round(Easings.lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, f));
        int b = Math.round(Easings.lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, f));
        int a = Math.round(Easings.lerp(from & 0xFF, to & 0xFF, f));
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    @Override
    public void close() {
        nvgDelete(context);
    }
}
