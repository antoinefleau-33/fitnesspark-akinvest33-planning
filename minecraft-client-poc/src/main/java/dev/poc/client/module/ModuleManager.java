package dev.poc.client.module;

import dev.poc.client.event.EventBus;
import dev.poc.client.keybind.KeybindManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Discovers, orders, loads, and hot-toggles modules.
 *
 * <p>Design notes that matter more than the code:
 * <ul>
 *   <li><b>Every registration is scoped.</b> Modules touch the event bus and the keybind registry
 *       only through {@link ModuleContext}, so disabling is a host-side sweep rather than a promise
 *       the module author has to keep.</li>
 *   <li><b>Disabling cascades.</b> Turning off a module that others hard-depend on turns those off
 *       first, in reverse load order, and reports what it did.</li>
 *   <li><b>Unloading is best-effort by construction.</b> Closing a {@link ModuleClassLoader} only
 *       frees memory once nothing references its classes. The scoped registrations cover the usual
 *       leaks; a module that parks a static reference in a shared class still pins its loader. This
 *       is exactly why Forge gave up on runtime mod unloading — the PoC keeps the door open and
 *       reports leaks instead of pretending they cannot happen.</li>
 * </ul>
 */
public final class ModuleManager {

    private final EventBus eventBus;
    private final KeybindManager keybinds;
    private final Path modsDirectory;
    private final Path dataRoot;

    private final Map<String, ModuleDescriptor> pending = new LinkedHashMap<>();
    private final Map<String, Path> pendingSources = new LinkedHashMap<>();
    private final Map<String, Supplier<Module>> builtinFactories = new LinkedHashMap<>();
    private final Map<String, ModuleContainer> containers = new LinkedHashMap<>();
    private final Map<String, String> rejected = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus, KeybindManager keybinds, Path modsDirectory, Path dataRoot) {
        this.eventBus = eventBus;
        this.keybinds = keybinds;
        this.modsDirectory = modsDirectory;
        this.dataRoot = dataRoot;
    }

    // ---------------------------------------------------------------- discovery

    /** Reads {@code module.properties} out of every jar in the mods directory. */
    public void discover() {
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(modsDirectory)) {
            for (Path jar : entries.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jarFile = new JarFile(jar.toFile())) {
                    JarEntry manifest = jarFile.getJarEntry(ModuleDescriptor.MANIFEST_NAME);
                    if (manifest == null) {
                        rejected.put(jar.getFileName().toString(),
                                "no " + ModuleDescriptor.MANIFEST_NAME + " at the jar root");
                        continue;
                    }
                    try (InputStream in = jarFile.getInputStream(manifest)) {
                        ModuleDescriptor descriptor = ModuleDescriptor.read(in);
                        pending.put(descriptor.id(), descriptor);
                        pendingSources.put(descriptor.id(), jar);
                    }
                } catch (IOException | RuntimeException e) {
                    rejected.put(jar.getFileName().toString(), "unreadable: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Registers a module that ships inside the client jar. Built-ins go through the exact same
     * lifecycle and dependency graph as external jars — the only difference is that they use the
     * client's own class loader and can never be unloaded.
     */
    public void registerBuiltin(ModuleDescriptor descriptor, Supplier<Module> factory) {
        pending.put(descriptor.id(), descriptor);
        builtinFactories.put(descriptor.id(), factory);
    }

    // ---------------------------------------------------------------- loading

    /** Resolves the graph, builds loaders, constructs instances, runs {@code onLoad}. */
    public void loadAll() {
        DependencyResolver.Result result = DependencyResolver.resolve(pending.values());
        rejected.putAll(result.rejected());

        Map<String, ModuleClassLoader> loaders = new LinkedHashMap<>();
        for (ModuleDescriptor descriptor : result.loadOrder()) {
            try {
                ModuleClassLoader loader = null;
                Path source = pendingSources.get(descriptor.id());
                if (source != null) {
                    loader = new ModuleClassLoader(descriptor.id(),
                            new java.net.URL[]{source.toUri().toURL()},
                            getClass().getClassLoader());
                    for (String dependency : descriptor.allDependencies()) {
                        ModuleClassLoader dependencyLoader = loaders.get(dependency);
                        if (dependencyLoader != null) {
                            loader.linkDependency(dependencyLoader);
                        }
                    }
                    loaders.put(descriptor.id(), loader);
                }

                ModuleContainer container = new ModuleContainer(descriptor, source, loader);
                container.setInstance(instantiate(descriptor, loader));
                container.setContext(new ModuleContext(descriptor, eventBus, keybinds,
                        dataRoot.resolve(descriptor.id())));
                container.instance().onLoad(container.context());
                container.setState(ModuleState.LOADED);
                containers.put(descriptor.id(), container);
            } catch (Throwable t) {
                rejected.put(descriptor.id(), "load failed: " + t);
            }
        }
        pending.clear();
        pendingSources.clear();
    }

    private Module instantiate(ModuleDescriptor descriptor, ModuleClassLoader loader) throws Exception {
        Supplier<Module> builtin = builtinFactories.get(descriptor.id());
        if (builtin != null) {
            return builtin.get();
        }
        Class<?> type = Class.forName(descriptor.mainClass(), true, loader);
        if (!Module.class.isAssignableFrom(type)) {
            throw new IllegalStateException(descriptor.mainClass() + " does not implement Module");
        }
        return (Module) type.getDeclaredConstructor().newInstance();
    }

    // ---------------------------------------------------------------- lifecycle

    /** Enables a module, enabling its hard dependencies first. Returns false if it errored. */
    public boolean enable(String id) {
        ModuleContainer container = containers.get(id);
        if (container == null || container.state() == ModuleState.ERRORED
                || container.state() == ModuleState.UNLOADED) {
            return false;
        }
        if (container.isEnabled()) {
            return true;
        }
        for (String dependency : container.descriptor().depends()) {
            if (!enable(dependency)) {
                container.fail(new IllegalStateException(
                        "dependency '" + dependency + "' could not be enabled"));
                return false;
            }
        }
        try {
            Files.createDirectories(container.context().dataDirectory());
            withContextClassLoader(container, () ->
                    container.instance().onEnable(container.context()));
            container.setState(ModuleState.ENABLED);
            return true;
        } catch (Throwable t) {
            // Roll back whatever the half-finished onEnable managed to register.
            releaseScopedRegistrations(id);
            container.fail(t);
            return false;
        }
    }

    /** Disables a module and, first, every enabled module that hard-depends on it. */
    public List<String> disable(String id) {
        ModuleContainer container = containers.get(id);
        if (container == null || !container.isEnabled()) {
            return List.of();
        }
        List<String> affected = new ArrayList<>();
        for (ModuleContainer other : reverseLoadOrder()) {
            if (other.isEnabled() && other.descriptor().depends().contains(id)) {
                affected.addAll(disable(other.id()));
            }
        }
        try {
            withContextClassLoader(container, () ->
                    container.instance().onDisable(container.context()));
        } catch (Throwable t) {
            System.err.println("[ModuleManager] " + id + " threw during onDisable");
            t.printStackTrace();
        } finally {
            releaseScopedRegistrations(id);
            container.setState(ModuleState.DISABLED);
            affected.add(id);
        }
        return affected;
    }

    /** Disables, unloads, and closes the class loader. Built-ins are refused. */
    public void unload(String id) {
        ModuleContainer container = containers.get(id);
        if (container == null || container.state() == ModuleState.UNLOADED) {
            return;
        }
        if (container.classLoader() == null) {
            throw new IllegalStateException("built-in module '" + id + "' cannot be unloaded");
        }
        disable(id);
        try {
            container.instance().onUnload(container.context());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        container.setInstance(null);
        try {
            container.classLoader().close();
        } catch (IOException e) {
            System.err.println("[ModuleManager] could not close loader for " + id + ": " + e);
        }
        container.setState(ModuleState.UNLOADED);
    }

    /** Everything the host registered on the module's behalf, dropped in one sweep. */
    private void releaseScopedRegistrations(String id) {
        eventBus.unregisterAll(id);
        keybinds.unregisterAll(id);
    }

    /**
     * Runs module code with its own loader as the thread context loader, so libraries inside the
     * module that use {@code Thread.currentThread().getContextClassLoader()} (ServiceLoader, most
     * logging backends, reflection-heavy JSON mappers) resolve against the module rather than the
     * host.
     */
    private void withContextClassLoader(ModuleContainer container, Runnable action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (container.classLoader() != null) {
            thread.setContextClassLoader(container.classLoader());
        }
        try {
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    // ---------------------------------------------------------------- queries

    public Collection<ModuleContainer> modules() {
        return List.copyOf(containers.values());
    }

    public Optional<ModuleContainer> module(String id) {
        return Optional.ofNullable(containers.get(id));
    }

    public Map<String, String> rejectedModules() {
        return Map.copyOf(rejected);
    }

    private List<ModuleContainer> reverseLoadOrder() {
        List<ModuleContainer> ordered = new ArrayList<>(containers.values());
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    /** Disables everything in reverse load order. Called on shutdown and before a version swap. */
    public void shutdown() {
        for (ModuleContainer container : reverseLoadOrder()) {
            if (container.isEnabled()) {
                disable(container.id());
            }
        }
    }
}
