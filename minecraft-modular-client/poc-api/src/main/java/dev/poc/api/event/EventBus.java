package dev.poc.api.event;

import java.util.function.Consumer;

public interface EventBus {

    /**
     * @param priority plus grand = appelé plus tôt. Les priorités identiques conservent l'ordre
     *                 d'enregistrement (déterminisme entre lancements).
     * @return un handle révocable ; il est aussi révoqué automatiquement à la fermeture du
     *         {@code ModuleContext} qui a servi à l'enregistrement.
     */
    <E extends Event> Subscription subscribe(Class<E> type, int priority, Consumer<E> handler);

    default <E extends Event> Subscription subscribe(Class<E> type, Consumer<E> handler) {
        return subscribe(type, 0, handler);
    }

    /** Scanne les méthodes annotées {@link Subscribe} de l'objet et les abonne. */
    Subscription register(Object listener);

    /** @return l'événement lui-même, pour lire son état après passage des handlers. */
    <E extends Event> E post(E event);

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
