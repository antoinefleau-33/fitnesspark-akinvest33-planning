package dev.poc.client.module;

import dev.poc.client.event.EventBus;
import dev.poc.client.keybind.KeyChord;
import dev.poc.client.keybind.KeyContext;
import dev.poc.client.keybind.KeybindManager;
import dev.poc.client.keybind.KeybindManager.Activation;
import dev.poc.client.keybind.KeybindHandle;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The capability handle a module receives. It is the <em>only</em> thing a module is given, and
 * every registration made through it is scoped to the module id, so teardown is bookkeeping the
 * host does rather than discipline the module author has to show.
 */
public final class ModuleContext {

    private final ModuleDescriptor descriptor;
    private final EventBus eventBus;
    private final KeybindManager keybinds;
    private final Path dataDirectory;

    ModuleContext(ModuleDescriptor descriptor, EventBus eventBus, KeybindManager keybinds,
                  Path dataDirectory) {
        this.descriptor = descriptor;
        this.eventBus = eventBus;
        this.keybinds = keybinds;
        this.dataDirectory = dataDirectory;
    }

    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    public String id() {
        return descriptor.id();
    }

    /** Registers {@code @Subscribe} methods, owned by this module. Dropped on disable. */
    public void subscribe(Object listener) {
        eventBus.register(descriptor.id(), listener);
    }

    public void unsubscribe(Object listener) {
        eventBus.unregister(listener);
    }

    /**
     * Registers a keybind owned by this module. The id is namespaced with the module id, which is
     * what removes the whole class of "two mods picked the same name" breakage — see
     * {@link KeybindManager}.
     */
    public KeybindHandle bindKey(String localId, String displayName, KeyChord defaultChord,
                                 KeyContext context, Activation activation,
                                 Consumer<KeybindHandle> action) {
        return keybinds.register(descriptor.id(), localId, displayName, defaultChord, context,
                activation, action);
    }

    /** Per-module writable directory, created lazily by the manager. */
    public Path dataDirectory() {
        return dataDirectory;
    }

    public void log(String message) {
        System.out.println("[" + descriptor.id() + "] " + message);
    }

    /** Posts an event on the shared bus. Modules can define their own event types. */
    public <T> T post(T event) {
        return eventBus.post(event);
    }
}
