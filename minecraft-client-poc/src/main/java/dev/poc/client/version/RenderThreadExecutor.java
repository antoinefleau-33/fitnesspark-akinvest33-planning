package dev.poc.client.version;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A task queue drained once per frame by the thread that owns the GL context.
 *
 * <p>GLFW's window functions and every GL call must happen on that one thread. Anything that runs
 * off-thread — a download finishing, a remap completing — hands its render-thread half back through
 * here rather than reaching for GL from a worker and crashing the driver.
 */
public final class RenderThreadExecutor implements Executor {

    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private volatile Thread owner;

    /** Call once from the render thread, before the loop starts. */
    public void bindToCurrentThread() {
        owner = Thread.currentThread();
    }

    public boolean isRenderThread() {
        return Thread.currentThread() == owner;
    }

    @Override
    public void execute(Runnable task) {
        queue.add(task);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Runs queued work. Call once per frame, before rendering.
     *
     * @param budgetNanos soft ceiling so a burst of tasks cannot stall a frame; leftovers run next
     *                    frame. Pass {@code Long.MAX_VALUE} during a version swap, where a visible
     *                    hitch is expected and correctness beats smoothness.
     */
    public void drain(long budgetNanos) {
        long deadline = System.nanoTime() + budgetNanos;
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (System.nanoTime() > deadline) {
                return;
            }
        }
    }
}
