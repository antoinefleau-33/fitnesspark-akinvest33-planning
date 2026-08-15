package dev.poc.client.module;

import java.nio.file.Path;

/** Everything the manager knows about one module. Mutable state lives here, not in the module. */
public final class ModuleContainer {

    private final ModuleDescriptor descriptor;
    private final Path source;
    private final ModuleClassLoader classLoader;

    private Module instance;
    private ModuleContext context;
    private ModuleState state = ModuleState.LOADED;
    private Throwable error;

    ModuleContainer(ModuleDescriptor descriptor, Path source, ModuleClassLoader classLoader) {
        this.descriptor = descriptor;
        this.source = source;
        this.classLoader = classLoader;
    }

    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    public String id() {
        return descriptor.id();
    }

    /** Null for built-in modules loaded from the client classpath. */
    public Path source() {
        return source;
    }

    public ModuleClassLoader classLoader() {
        return classLoader;
    }

    public Module instance() {
        return instance;
    }

    public ModuleContext context() {
        return context;
    }

    public ModuleState state() {
        return state;
    }

    public Throwable error() {
        return error;
    }

    public boolean isEnabled() {
        return state == ModuleState.ENABLED;
    }

    void setInstance(Module instance) {
        this.instance = instance;
    }

    void setContext(ModuleContext context) {
        this.context = context;
    }

    void setState(ModuleState state) {
        this.state = state;
    }

    void fail(Throwable error) {
        this.error = error;
        this.state = ModuleState.ERRORED;
    }

    @Override
    public String toString() {
        return descriptor.id() + " " + descriptor.version() + " [" + state + "]";
    }
}
