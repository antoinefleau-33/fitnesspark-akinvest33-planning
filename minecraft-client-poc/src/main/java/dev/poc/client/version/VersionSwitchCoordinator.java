package dev.poc.client.version;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Drives an in-session version swap.
 *
 * <p>The sequence, and which thread each step runs on:
 *
 * <pre>
 *  worker  RESOLVING   manifest for the target version
 *  worker  FETCHING    missing artifacts into the shared {@link AssetStore}
 *  worker  PREPARING   build the class loader, remap, pre-link the version adapter
 *  render  DRAINING    disable modules, flush pending GL work, save state
 *  render  DETACHING   old runtime releases its GL objects and gives the window back
 *  render  ATTACHING   new runtime takes the window and initialises
 *  render  RESUMING    re-enable modules against the new adapter
 * </pre>
 *
 * <p>Everything up to DRAINING happens while the current version keeps rendering, so the only
 * visible interruption is the detach/attach pair. Keeping a target {@link VersionRuntime.Phase#STANDBY}
 * via {@link #warm} moves that cost off the critical path entirely: the swap becomes a couple of
 * seconds of black screen instead of a full relaunch.
 *
 * <p>The window handle is created once by the shell and passed to each runtime in turn. That is the
 * constraint the whole design bends around — a GLFW window and its GL context belong to the process
 * and thread that made them, so the shell owns LWJGL and the game versions are guests.
 */
public final class VersionSwitchCoordinator {

    public enum Phase {
        IDLE, RESOLVING, FETCHING, PREPARING, DRAINING, DETACHING, ATTACHING, RESUMING, FAILED
    }

    /** Hooks the client uses to quiesce and restore itself around the swap. */
    public interface SwapHooks {
        /** Render thread, before the old runtime detaches. Disable modules, persist state. */
        void beforeDetach(String outgoingVersionId);

        /** Render thread, after the new runtime attaches. Rebind adapters, re-enable modules. */
        void afterAttach(String incomingVersionId);
    }

    public interface ProgressListener {
        void onPhase(Phase phase, String versionId, double fraction);
    }

    private final Function<String, VersionManifest> manifestResolver;
    private final Function<VersionManifest, VersionRuntime> runtimeFactory;
    private final RenderThreadExecutor renderThread;
    private final SwapHooks hooks;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "version-prepare");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, VersionRuntime> warmRuntimes = new ConcurrentHashMap<>();
    private final AtomicReference<VersionRuntime> active = new AtomicReference<>();
    private volatile ProgressListener listener = (phase, version, fraction) -> {
    };
    private volatile Phase phase = Phase.IDLE;
    private volatile long windowHandle;

    public VersionSwitchCoordinator(Function<String, VersionManifest> manifestResolver,
                                    Function<VersionManifest, VersionRuntime> runtimeFactory,
                                    RenderThreadExecutor renderThread,
                                    SwapHooks hooks) {
        this.manifestResolver = manifestResolver;
        this.runtimeFactory = runtimeFactory;
        this.renderThread = renderThread;
        this.hooks = hooks;
    }

    public void setWindowHandle(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    public void setProgressListener(ProgressListener listener) {
        this.listener = listener;
    }

    public Phase phase() {
        return phase;
    }

    public VersionRuntime activeRuntime() {
        return active.get();
    }

    /**
     * Prepares a version in the background without swapping to it. Call this when the user opens
     * the version dropdown, or on the version they used last session — by the time they click, the
     * expensive half is already done.
     */
    public CompletableFuture<Void> warm(String versionId) {
        return CompletableFuture.runAsync(() -> {
            if (warmRuntimes.containsKey(versionId)) {
                return;
            }
            try {
                VersionManifest manifest = manifestResolver.apply(versionId);
                VersionRuntime runtime = runtimeFactory.apply(manifest);
                runtime.prepare();
                VersionRuntime previous = warmRuntimes.putIfAbsent(versionId, runtime);
                if (previous != null) {
                    runtime.close(); // lost the race, do not leak the loser
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not warm " + versionId, e);
            }
        }, worker);
    }

    /** Frees a warm runtime the user is unlikely to pick. Each one costs real heap. */
    public void evict(String versionId) {
        VersionRuntime runtime = warmRuntimes.remove(versionId);
        if (runtime != null && runtime != active.get()) {
            runtime.close();
        }
    }

    public CompletableFuture<Void> switchTo(String versionId) {
        VersionRuntime current = active.get();
        if (current != null && current.versionId().equals(versionId)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(() -> prepareTarget(versionId), worker)
                .thenCompose(this::performSwapOnRenderThread)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        phase = Phase.FAILED;
                        listener.onPhase(Phase.FAILED, versionId, 1.0);
                    } else {
                        phase = Phase.IDLE;
                    }
                });
    }

    private VersionRuntime prepareTarget(String versionId) {
        VersionRuntime warm = warmRuntimes.get(versionId);
        if (warm != null && warm.phase() == VersionRuntime.Phase.STANDBY) {
            listener.onPhase(Phase.PREPARING, versionId, 1.0);
            return warm;
        }
        try {
            phase = Phase.RESOLVING;
            listener.onPhase(Phase.RESOLVING, versionId, 0.0);
            VersionManifest manifest = manifestResolver.apply(versionId);

            phase = Phase.FETCHING;
            listener.onPhase(Phase.FETCHING, versionId, 0.1);
            VersionRuntime runtime = runtimeFactory.apply(manifest);

            phase = Phase.PREPARING;
            listener.onPhase(Phase.PREPARING, versionId, 0.4);
            runtime.prepare();
            warmRuntimes.put(versionId, runtime);
            listener.onPhase(Phase.PREPARING, versionId, 1.0);
            return runtime;
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare " + versionId, e);
        }
    }

    private CompletableFuture<Void> performSwapOnRenderThread(VersionRuntime target) {
        return renderThread.submit(() -> {
            VersionRuntime outgoing = active.get();
            String outgoingId = outgoing == null ? null : outgoing.versionId();
            try {
                phase = Phase.DRAINING;
                listener.onPhase(Phase.DRAINING, target.versionId(), 0.0);
                hooks.beforeDetach(outgoingId);

                if (outgoing != null) {
                    phase = Phase.DETACHING;
                    listener.onPhase(Phase.DETACHING, target.versionId(), 0.3);
                    outgoing.detach();
                }

                phase = Phase.ATTACHING;
                listener.onPhase(Phase.ATTACHING, target.versionId(), 0.6);
                target.attach(windowHandle);
                active.set(target);

                phase = Phase.RESUMING;
                listener.onPhase(Phase.RESUMING, target.versionId(), 0.9);
                hooks.afterAttach(target.versionId());
                listener.onPhase(Phase.IDLE, target.versionId(), 1.0);
                return null;
            } catch (Exception e) {
                // Try to put the old version back rather than leaving a dead window.
                if (outgoing != null) {
                    try {
                        outgoing.attach(windowHandle);
                        active.set(outgoing);
                        hooks.afterAttach(outgoing.versionId());
                    } catch (Exception rollbackFailure) {
                        e.addSuppressed(rollbackFailure);
                    }
                }
                throw new IllegalStateException("Swap to " + target.versionId() + " failed", e);
            }
        });
    }

    public void shutdown() {
        worker.shutdownNow();
        warmRuntimes.values().forEach(VersionRuntime::close);
        warmRuntimes.clear();
    }
}
