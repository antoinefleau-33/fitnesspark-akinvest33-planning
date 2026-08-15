package dev.poc.ui;

import dev.poc.api.input.Chord;
import dev.poc.core.input.InputPipeline;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Fenêtre et contexte OpenGL, <b>possédés par le shell</b>.
 *
 * <p>Point d'architecture décisif pour le changement de version : la fenêtre et le contexte GL
 * sont créés une seule fois, au démarrage du client, et survivent à toutes les bascules. Une
 * session Minecraft reçoit le handle via {@code GameEnvironment} et dessine dedans. Trois
 * bénéfices :
 * <ul>
 *   <li>pas de clignotement ni de recréation de fenêtre entre deux versions ;</li>
 *   <li>les natifs GLFW/OpenGL sont chargés une fois — ils ne <em>peuvent</em> de toute façon
 *       l'être qu'une fois par JVM ;</li>
 *   <li>l'interface du client (menu, overlay de transition) continue de tourner pendant qu'aucune
 *       version n'est chargée, ce qui rend la bascule visuellement continue.</li>
 * </ul>
 *
 * <p>Les callbacks GLFW sont enregistrés ici et <b>uniquement ici</b>. Si le code d'une version
 * enregistrait son propre {@code glfwSetKeyCallback}, deux choses casseraient : l'ancien callback
 * serait écrasé (l'UI du client cesserait de répondre), et le lambda serait retenu côté natif,
 * empêchant la collecte du classloader de la version.
 */
public final class Window implements AutoCloseable {

    private final long handle;
    private int width, height;
    private int framebufferWidth, framebufferHeight;
    private float contentScale = 1f;
    private InputPipeline input;
    private Runnable onResize = () -> {};

    public Window(String title, int width, int height) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("initialisation de GLFW impossible");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);   // requis sur macOS
        glfwWindowHint(GLFW_SAMPLES, 0);                          // l'AA est fait par la SDF
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new IllegalStateException("création de la fenêtre impossible");

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);

        this.width = width;
        this.height = height;
        refreshSizes();

        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            refreshSizes();
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            onResize.run();
        });

        glfwShowWindow(handle);
    }

    /**
     * Branche le pipeline d'entrée. Tous les événements clavier/souris passent d'abord par lui ;
     * ce qu'il ne consomme pas est transmis à la session de jeu via son {@code Passthrough}.
     */
    public void attachInput(InputPipeline pipeline) {
        this.input = pipeline;

        glfwSetKeyCallback(handle, (win, key, scancode, action, mods) ->
                input.onKey(scancode, key, toAction(action)));

        glfwSetMouseButtonCallback(handle, (win, button, action, mods) ->
                input.onMouseButton(button, toAction(action)));

        // Sans ce callback, alt-tabber en tenant une touche laisse le bind « enfoncé » à vie.
        glfwSetWindowFocusCallback(handle, (win, focused) -> {
            if (!focused) input.onFocusLost();
        });
    }

    private static InputPipeline.Action toAction(int glfwAction) {
        return switch (glfwAction) {
            case GLFW_PRESS -> InputPipeline.Action.PRESS;
            case GLFW_RELEASE -> InputPipeline.Action.RELEASE;
            default -> InputPipeline.Action.REPEAT;
        };
    }

    /**
     * Libellé affichable d'un chord. {@code glfwGetKeyName} tient compte de la disposition
     * clavier : la touche bindée sur le scancode 17 s'affiche « W » en QWERTY et « Z » en AZERTY,
     * sans qu'aucune configuration ne change. C'est le pendant du choix de binder sur scancode.
     */
    public static String describe(Chord chord) {
        if (!chord.isBound()) return "—";
        StringBuilder sb = new StringBuilder();
        chord.modifiers().forEach(m -> sb.append(switch (m) {
            case CTRL -> "Ctrl+";
            case SHIFT -> "Maj+";
            case ALT -> "Alt+";
            case SUPER -> "Super+";
        }));
        if (chord.device() == Chord.Device.MOUSE) {
            return sb.append("Souris ").append(chord.code() + 1).toString();
        }
        String name = glfwGetKeyName(GLFW_KEY_UNKNOWN, chord.code());
        return sb.append(name == null ? "Touche " + chord.code()
                : name.toUpperCase(java.util.Locale.ROOT)).toString();
    }

    private void refreshSizes() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(handle, w, h);
            this.width = w.get(0);
            this.height = h.get(0);
            glfwGetFramebufferSize(handle, w, h);
            this.framebufferWidth = w.get(0);
            this.framebufferHeight = h.get(0);
            // Séparer taille logique et taille du framebuffer est indispensable sur écran HiDPI :
            // les confondre donne une UI deux fois trop petite sur Retina.
            this.contentScale = this.width == 0 ? 1f : (float) framebufferWidth / this.width;
        }
    }

    public void setResizeListener(Runnable listener) { this.onResize = listener; }

    public long handle() { return handle; }
    public int width() { return width; }
    public int height() { return height; }
    public int framebufferWidth() { return framebufferWidth; }
    public int framebufferHeight() { return framebufferHeight; }
    public float contentScale() { return contentScale; }

    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void pollEvents() { glfwPollEvents(); }
    public void swapBuffers() { glfwSwapBuffers(handle); }

    public double[] cursorPosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocDouble(1);
            var y = stack.mallocDouble(1);
            glfwGetCursorPos(handle, x, y);
            return new double[]{x.get(0), y.get(0)};
        }
    }

    @Override
    public void close() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
