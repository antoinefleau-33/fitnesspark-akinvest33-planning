package dev.poc.core.event;

import dev.poc.api.event.Event;
import dev.poc.api.event.EventBus;
import dev.poc.api.event.Subscribe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bus d'événements par type exact, avec listes copy-on-write.
 *
 * <p>Deux choix qui comptent pour un client à 240 fps :
 * <ul>
 *   <li><b>Pas de parcours de hiérarchie</b> au moment du post. La table des handlers d'un type
 *       est calculée une fois (incluant super-types et interfaces) puis mise en cache ; un
 *       {@code RenderHud} posté 240 fois par seconde ne doit pas refaire d'introspection.</li>
 *   <li><b>Copy-on-write</b> plutôt que synchronisation : le post est le chemin chaud, les
 *       (dés)abonnements sont rares. Un module qui se désabonne pendant qu'on itère ne provoque
 *       pas de {@code ConcurrentModificationException}.</li>
 * </ul>
 *
 * <p>Les handlers annotés passent par {@link MethodHandle} plutôt que par {@code Method.invoke} :
 * après échauffement du JIT, le coût d'appel rejoint celui d'un appel virtuel direct.
 */
public final class SimpleEventBus implements EventBus {

    private record Handler(Class<?> type, int priority, long seq, boolean receiveCancelled,
                           Consumer<Event> action) {}

    private final Map<Class<?>, List<Handler>> registered = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Handler>> dispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong sequencer = new AtomicLong();

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> Subscription subscribe(Class<E> type, int priority, Consumer<E> handler) {
        Handler h = new Handler(type, priority, sequencer.incrementAndGet(), false,
                (Consumer<Event>) handler);
        add(h);
        return () -> remove(h);
    }

    @Override
    public Subscription register(Object listener) {
        List<Handler> added = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (var method : listener.getClass().getMethods()) {
            Subscribe ann = method.getAnnotation(Subscribe.class);
            if (ann == null) continue;
            if (method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("@Subscribe invalide sur " + method
                        + " : un unique paramètre de type Event est attendu");
            }
            Class<?> eventType = method.getParameterTypes()[0];
            try {
                MethodHandle mh = lookup.unreflect(method).bindTo(listener);
                Handler h = new Handler(eventType, ann.priority(), sequencer.incrementAndGet(),
                        ann.receiveCancelled(), event -> {
                            try {
                                mh.invoke(event);
                            } catch (Throwable t) {
                                throw new RuntimeException(
                                        "handler " + method.getName() + " a échoué", t);
                            }
                        });
                add(h);
                added.add(h);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("handler inaccessible: " + method, e);
            }
        }
        return () -> added.forEach(this::remove);
    }

    private void add(Handler h) {
        registered.compute(h.type(), (k, list) -> {
            List<Handler> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
            copy.add(h);
            copy.sort(Comparator.comparingInt((Handler x) -> -x.priority())
                    .thenComparingLong(Handler::seq));
            return List.copyOf(copy);
        });
        dispatchCache.clear();   // la hiérarchie a changé
    }

    private void remove(Handler h) {
        registered.computeIfPresent(h.type(), (k, list) -> {
            List<Handler> copy = new ArrayList<>(list);
            copy.remove(h);
            return copy.isEmpty() ? null : List.copyOf(copy);
        });
        dispatchCache.clear();
    }

    @Override
    public <E extends Event> E post(E event) {
        List<Handler> handlers = dispatchCache.computeIfAbsent(
                event.getClass(), SimpleEventBus.this::buildDispatchList);

        boolean cancellable = event instanceof Event.Cancellable;
        for (Handler h : handlers) {
            if (cancellable && !h.receiveCancelled()
                    && ((Event.Cancellable) event).cancelled()) {
                continue;
            }
            try {
                h.action().accept(event);
            } catch (RuntimeException e) {
                System.getLogger("events").log(System.Logger.Level.ERROR,
                        "handler en échec pour " + event.getClass().getSimpleName(), e);
            }
        }
        return event;
    }

    /** Aplatit la hiérarchie de types une fois, puis met en cache. */
    private List<Handler> buildDispatchList(Class<?> eventType) {
        List<Handler> out = new ArrayList<>();
        for (Class<?> c = eventType; c != null && c != Object.class; c = c.getSuperclass()) {
            collect(c, out);
            for (Class<?> itf : c.getInterfaces()) collect(itf, out);
        }
        out.sort(Comparator.comparingInt((Handler h) -> -h.priority())
                .thenComparingLong(Handler::seq));
        return List.copyOf(out);
    }

    private void collect(Class<?> type, List<Handler> out) {
        List<Handler> list = registered.get(type);
        if (list != null) out.addAll(list);
    }

    /**
     * Purge tous les handlers dont le code appartient au classloader donné. Filet de sécurité
     * appelé au déchargement d'un module ou d'une session de jeu : un handler oublié suffit à
     * retenir tout un classloader Minecraft en mémoire.
     */
    public int purgeClassLoader(ClassLoader loader) {
        int removed = 0;
        // Itération explicite : ConcurrentHashMap.replaceAll interdit les valeurs nulles, on ne
        // peut donc pas signaler la suppression d'une entrée par un retour null.
        for (var it = registered.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            List<Handler> list = entry.getValue();
            List<Handler> kept = list.stream()
                    .filter(h -> h.action().getClass().getClassLoader() != loader)
                    .toList();
            removed += list.size() - kept.size();
            if (kept.isEmpty()) {
                it.remove();
            } else if (kept.size() != list.size()) {
                entry.setValue(kept);
            }
        }
        dispatchCache.clear();
        return removed;
    }
}
