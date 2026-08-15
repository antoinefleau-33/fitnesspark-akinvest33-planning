package dev.poc.api.event;

/** Racine des événements. Les sous-types annulables implémentent {@link Cancellable}. */
public interface Event {

    interface Cancellable extends Event {
        boolean cancelled();
        void cancel();
    }

    /** Base pratique et thread-safe-par-convention (posté sur le thread de rendu). */
    abstract class Abstract implements Event {
        private final long nanos = System.nanoTime();
        public long timestampNanos() { return nanos; }
    }

    abstract class AbstractCancellable extends Abstract implements Cancellable {
        private boolean cancelled;
        @Override public boolean cancelled() { return cancelled; }
        @Override public void cancel() { this.cancelled = true; }
    }
}
