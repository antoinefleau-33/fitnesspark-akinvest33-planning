package dev.poc.client.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hierarchical event bus with owner-scoped registration.
 *
 * <p>The owner key is what makes hot-unloading a module safe: {@link #unregisterAll(Object)}
 * drops every handler a module ever registered, including handlers on objects the module
 * created after {@code onEnable}. Without it, a stale handler keeps a strong reference to a
 * class loaded by a discarded {@code ModuleClassLoader} and the whole loader leaks.
 *
 * <p>Dispatch walks the event's supertype chain and its interfaces, so a handler on a base
 * event type sees subtypes. The resolved handler list is cached per concrete event class and
 * invalidated whenever the registry changes.
 */
public final class EventBus {

    private record Handler(Object owner, Object listener, MethodHandle handle,
                           Class<?> eventType, int priority, boolean receiveCancelled) {
    }

    private final List<Handler> handlers = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<Handler>> dispatchCache = new ConcurrentHashMap<>();
    private final MethodHandles.Lookup lookup = MethodHandles.lookup();

    /**
     * Registers every {@code @Subscribe} method on {@code listener}.
     *
     * @param owner opaque key used by {@link #unregisterAll(Object)}; pass the module id
     */
    public void register(Object owner, Object listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException(
                        "@Subscribe method must take exactly one parameter: " + method);
            }
            if (Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("@Subscribe method must not be static: " + method);
            }
            method.setAccessible(true);
            try {
                MethodHandle handle = lookup.unreflect(method).bindTo(listener);
                handlers.add(new Handler(owner, listener, handle, method.getParameterTypes()[0],
                        annotation.priority(), annotation.receiveCancelled()));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access @Subscribe method " + method, e);
            }
        }
        dispatchCache.clear();
    }

    /** Drops every handler registered under {@code owner}. Called on module disable. */
    public void unregisterAll(Object owner) {
        if (handlers.removeIf(h -> h.owner().equals(owner))) {
            dispatchCache.clear();
        }
    }

    /** Drops the handlers of a single listener object, leaving the owner's other handlers alone. */
    public void unregister(Object listener) {
        if (handlers.removeIf(h -> h.listener() == listener)) {
            dispatchCache.clear();
        }
    }

    public <T> T post(T event) {
        List<Handler> matching = dispatchCache.computeIfAbsent(event.getClass(), this::resolve);
        if (matching.isEmpty()) {
            return event;
        }
        boolean cancellable = event instanceof Cancellable;
        for (Handler handler : matching) {
            if (cancellable && ((Cancellable) event).isCancelled() && !handler.receiveCancelled()) {
                continue;
            }
            try {
                handler.handle().invoke(event);
            } catch (Throwable t) {
                // A misbehaving module must not take the render loop down with it.
                System.err.println("[EventBus] handler from '" + handler.owner() + "' threw on "
                        + event.getClass().getSimpleName());
                t.printStackTrace();
            }
        }
        return event;
    }

    private List<Handler> resolve(Class<?> eventType) {
        List<Handler> matching = new ArrayList<>();
        for (Handler handler : handlers) {
            if (handler.eventType().isAssignableFrom(eventType)) {
                matching.add(handler);
            }
        }
        matching.sort(Comparator.comparingInt(Handler::priority));
        return List.copyOf(matching);
    }

    public int handlerCount() {
        return handlers.size();
    }
}
