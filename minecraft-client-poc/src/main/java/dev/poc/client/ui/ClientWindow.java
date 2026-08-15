package dev.poc.client.ui;

import dev.poc.client.keybind.KeyContext;
import dev.poc.client.keybind.KeybindManager;
import dev.poc.client.version.RenderThreadExecutor;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_SAMPLES;
import static org.lwjgl.glfw.GLFW.GLFW_STENCIL_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * The shell: it owns GLFW, the window, and the GL context, and it outlives every version runtime.
 *
 * <p>Input is routed to the {@link KeybindManager} first. When a bind consumes an event the game
 * never sees it, which is what lets a client bind sit on a key the game also uses without the two
 * firing together.
 *
 * <p>A stencil buffer is requested explicitly: NanoVG needs one for {@code NVG_STENCIL_STROKES},
 * and the default framebuffer on some drivers has none, which shows up as strokes disappearing at
 * path self-intersections rather than as an error.
 */
public final class ClientWindow implements AutoCloseable {

    private final long handle;
    private final KeybindManager keybinds;
    private final RenderThreadExecutor renderThread;
    private NanoVgRenderer gfx;

    public ClientWindow(String title, int width, int height,
                        KeybindManager keybinds, RenderThreadExecutor renderThread) {
        this.keybinds = keybinds;
        this.renderThread = renderThread;

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW init failed");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, 0);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);
        glfwWindowHint(GLFW_SAMPLES, 4);

        handle = glfwCreateWindow(width, height, title, 0L, 0L);
        if (handle == 0L) {
            glfwTerminate();
            throw new IllegalStateException("Could not create the window");
        }
        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);
        renderThread.bindToCurrentThread();
        installCallbacks();
        glfwShowWindow(handle);
    }

    public long handle() {
        return handle;
    }

    private void installCallbacks() {
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) ->
                keybinds.onKey(key, action, mods));

        glfwSetMouseButtonCallback(handle, (window, button, action, mods) ->
                keybinds.onMouseButton(button, action == GLFW_PRESS, mods));

        // Without this, alt-tabbing while a bind is held leaves it held forever.
        glfwSetWindowFocusCallback(handle, (window, focused) -> {
            if (!focused) {
                keybinds.releaseAll();
            }
        });
    }

    public void setCursorHandler(MainMenuScreen screen) {
        glfwSetCursorPosCallback(handle, (window, x, y) -> screen.onCursorMove(x, y));
        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            boolean pressed = action == GLFW_PRESS;
            if (screen.onMouseButton(button, pressed)) {
                return; // the UI took it; neither keybinds nor the game should see it
            }
            keybinds.onMouseButton(button, pressed, mods);
        });
    }

    public NanoVgRenderer renderer() {
        if (gfx == null) {
            gfx = NanoVgRenderer.create();
        }
        return gfx;
    }

    public void run(MainMenuScreen screen, Theme theme) {
        keybinds.setContext(KeyContext.IN_SCREEN);
        NanoVgRenderer renderer = renderer();
        long previousNanos = System.nanoTime();

        while (!glfwWindowShouldClose(handle)) {
            long now = System.nanoTime();
            float delta = (now - previousNanos) / 1_000_000_000f;
            previousNanos = now;

            glfwPollEvents();
            keybinds.tick();
            // 2 ms of off-thread work per frame; a version swap raises this to unlimited.
            renderThread.drain(2_000_000L);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer fbWidth = stack.mallocInt(1);
                IntBuffer fbHeight = stack.mallocInt(1);
                IntBuffer winWidth = stack.mallocInt(1);
                IntBuffer winHeight = stack.mallocInt(1);
                glfwGetFramebufferSize(handle, fbWidth, fbHeight);
                glfwGetWindowSize(handle, winWidth, winHeight);

                int fw = fbWidth.get(0);
                int fh = fbHeight.get(0);
                float pixelRatio = winWidth.get(0) == 0 ? 1f : (float) fw / winWidth.get(0);

                glViewport(0, 0, fw, fh);
                glClearColor(0f, 0f, 0f, 1f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

                renderer.beginFrame(winWidth.get(0), winHeight.get(0), pixelRatio);
                screen.render(renderer, winWidth.get(0), winHeight.get(0), delta);
                renderer.endFrame();
            }

            glfwSwapBuffers(handle);
        }
    }

    @Override
    public void close() {
        if (gfx != null) {
            gfx.close();
        }
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
