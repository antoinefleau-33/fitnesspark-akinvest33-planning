package dev.poc.client.event;

/** An event whose downstream effect can be suppressed by a handler. */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
