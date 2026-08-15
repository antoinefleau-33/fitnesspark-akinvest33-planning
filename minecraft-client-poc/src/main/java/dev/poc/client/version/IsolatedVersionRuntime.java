package dev.poc.client.version;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-JVM implementation: each version gets its own class loader, and the shell keeps the window.
 *
 * <p>Why a class loader per version rather than per-version JVMs: the shell's window, GL context,
 * fonts, UI atlases and audio device survive the swap, so the client's own interface never blinks
 * and the swap costs seconds instead of a relaunch. The price is strict discipline about what is
 * shared.
 *
 * <h2>The rules that make it work</h2>
 * <ul>
 *   <li><b>The shell owns LWJGL.</b> {@code org.lwjgl.*} loads in the parent loader, once. JNI
 *       forbids loading the same native library in two loaders — the second attempt throws
 *       {@code UnsatisfiedLinkError: Native Library ... already loaded in another classloader} —
 *       so per-version LWJGL copies are not an option even in principle.</li>
 *   <li><b>Everything else is version-local.</b> Minecraft's static state (its singleton, registries,
 *       the block and item tables) lives in the version loader, and dropping the loader drops all of
 *       it. This is the part that makes the approach viable at all: there is no realistic way to
 *       reset that state in place, so we throw the whole namespace away instead.</li>
 *   <li><b>GL objects are version-local too.</b> {@link #detach()} must delete every VAO, texture,
 *       shader and buffer the version created. Leaking them survives the loader being dropped —
 *       they live on the GPU, not the heap — and a few swaps will exhaust VRAM.</li>
 *   <li><b>Modules never touch version classes directly.</b> They compile against the façade in
 *       {@code dev.poc.client.api}; the per-version adapter jar implements it. The adapter is loaded
 *       in the version loader, the interfaces come from the shared parent, and the module keeps
 *       working across versions because the only thing that changed is which adapter is behind the
 *       interface.</li>
 * </ul>
 *
 * <h2>What it does not solve</h2>
 * Any native library the game itself loads (not LWJGL — think a version-specific audio or crypto
 * native) has the same one-loader-only constraint, and JNI never unloads until the loader is
 * collected. A version that pulls its own natives is the case where you fall back to a child JVM
 * per version instead; the shell and the coordinator API stay identical, only this class changes.
 */
public final class IsolatedVersionRuntime implements VersionRuntime {

    private final VersionManifest manifest;
    private final AssetStore assetStore;
    private final Path versionRoot;

    private URLClassLoader loader;
    private Object gameInstance;
    private Method attachMethod;
    private Method detachMethod;
    private volatile Phase phase = Phase.NEW;

    public IsolatedVersionRuntime(VersionManifest manifest, AssetStore assetStore, Path versionRoot) {
        this.manifest = manifest;
        this.assetStore = assetStore;
        this.versionRoot = versionRoot;
    }

    @Override
    public String versionId() {
        return manifest.id();
    }

    @Override
    public Phase phase() {
        return phase;
    }

    @Override
    public void prepare() throws Exception {
        if (phase == Phase.STANDBY || phase == Phase.ACTIVE) {
            return;
        }
        phase = Phase.PREPARING;
        try {
            Files.createDirectories(versionRoot);
            List<URL> classpath = new ArrayList<>();
            classpath.add(materialise(manifest.clientJar()));
            for (VersionManifest.Artifact library : manifest.libraries()) {
                classpath.add(materialise(library));
            }
            // The version adapter implementing dev.poc.client.api for this exact version.
            Path adapter = versionRoot.resolve("adapter-" + manifest.id() + ".jar");
            if (Files.isRegularFile(adapter)) {
                classpath.add(adapter.toUri().toURL());
            }

            loader = new URLClassLoader("mc:" + manifest.id(),
                    classpath.toArray(URL[]::new), getClass().getClassLoader());

            // Entry point contributed by the adapter, not by Minecraft itself.
            Class<?> entryPoint = Class.forName("dev.poc.adapter.VersionEntryPoint", true, loader);
            gameInstance = entryPoint.getDeclaredConstructor().newInstance();
            attachMethod = entryPoint.getMethod("attach", long.class);
            detachMethod = entryPoint.getMethod("detach");

            phase = Phase.STANDBY;
        } catch (Exception e) {
            phase = Phase.FAILED;
            close();
            throw e;
        }
    }

    private URL materialise(VersionManifest.Artifact artifact) throws IOException {
        return assetStore.materialise(artifact, versionRoot).toUri().toURL();
    }

    @Override
    public void attach(long windowHandle) throws Exception {
        if (phase != Phase.STANDBY && phase != Phase.DETACHED) {
            throw new IllegalStateException("attach() from phase " + phase);
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            attachMethod.invoke(gameInstance, windowHandle);
            phase = Phase.ACTIVE;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public void detach() throws Exception {
        if (phase != Phase.ACTIVE) {
            return;
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            detachMethod.invoke(gameInstance);
            phase = Phase.DETACHED;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public void close() {
        gameInstance = null;
        attachMethod = null;
        detachMethod = null;
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // The jar handles are released on GC; nothing useful to do here.
            }
            loader = null;
        }
        phase = Phase.CLOSED;
    }
}
