package dev.poc.client.version;

/**
 * One instantiated Minecraft version.
 *
 * <p>The split between {@link #prepare()} and {@link #attach(long)} is the whole trick behind an
 * in-game version switch that does not feel like a restart. {@code prepare()} is everything that
 * can happen while the current version is still rendering — downloading, verifying, remapping,
 * building the class loader, pre-linking the adapter. {@code attach()} is the short, blocking part
 * that must run on the thread owning the GL context.
 *
 * <p>Implementations must assume {@code prepare()} runs on a worker thread and {@code attach()} /
 * {@code detach()} run on the render thread.
 */
public interface VersionRuntime extends AutoCloseable {

    enum Phase {
        /** Constructed, nothing fetched. */
        NEW,
        /** {@link #prepare()} running. */
        PREPARING,
        /** Prepared and warm — can be attached in the time of a couple of frames. */
        STANDBY,
        /** Owns the window and is rendering. */
        ACTIVE,
        /** Detached but still prepared; re-attaching is cheap. */
        DETACHED,
        FAILED,
        CLOSED
    }

    String versionId();

    Phase phase();

    /** Off-thread: fetch, verify, remap, build the class loader. Idempotent. */
    void prepare() throws Exception;

    /**
     * Render thread: take ownership of the GLFW window and bring the game up. The window handle is
     * created once by the shell and outlives every runtime, which is why the shell — not the game —
     * must own GLFW and LWJGL.
     */
    void attach(long windowHandle) throws Exception;

    /** Render thread: delete GL objects, stop the game's threads, give the window back. */
    void detach() throws Exception;

    /** Drop the class loader and everything under it. */
    @Override
    void close();
}
