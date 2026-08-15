package dev.poc.client.module;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * One class loader per module, with a deliberate delegation order.
 *
 * <ol>
 *   <li><b>Parent first</b> for the JDK, LWJGL and the client's own API packages. These types must
 *       be identical instances across every module, otherwise a module's {@code Module} is not the
 *       host's {@code Module} and every cast blows up with a confusing
 *       {@code ClassCastException: Module cannot be cast to Module}.</li>
 *   <li><b>Self</b> next, so a module can ship its own copy of a library without the host's version
 *       shadowing it.</li>
 *   <li><b>Declared dependencies</b> next, restricted to classes those modules define themselves —
 *       {@link #findLocal} never re-delegates, which keeps a dependency cycle from turning into a
 *       stack overflow.</li>
 *   <li><b>Parent</b> last, as a fallback.</li>
 * </ol>
 *
 * <p>The loader is {@link AutoCloseable}: closing it releases the jar file handle, which is what
 * makes replacing a module jar on disk possible on Windows without exiting the client.
 */
public final class ModuleClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    /** Packages that must resolve to the same class objects in every module. */
    private static final String[] SHARED_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "dev.poc.client.api.",
            "dev.poc.client.module.",
            "dev.poc.client.event.",
            "dev.poc.client.keybind.",
            "org.lwjgl."
    };

    private final String moduleId;
    private final List<ModuleClassLoader> dependencies = new ArrayList<>();

    ModuleClassLoader(String moduleId, URL[] urls, ClassLoader parent) {
        super("module:" + moduleId, urls, parent);
        this.moduleId = moduleId;
    }

    void linkDependency(ModuleClassLoader dependency) {
        dependencies.add(dependency);
    }

    public String moduleId() {
        return moduleId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return resolved(loaded, resolve);
            }
            if (isShared(name)) {
                return resolved(super.loadClass(name, false), resolve);
            }
            try {
                return resolved(findClass(name), resolve);
            } catch (ClassNotFoundException ignored) {
                // fall through to dependencies
            }
            for (ModuleClassLoader dependency : dependencies) {
                Class<?> fromDependency = dependency.findLocal(name);
                if (fromDependency != null) {
                    return resolved(fromDependency, resolve);
                }
            }
            return resolved(super.loadClass(name, false), resolve);
        }
    }

    /** Looks the class up in this module's own jar only. Never delegates — cycle-safe. */
    Class<?> findLocal(String name) {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        if (own != null) {
            return own;
        }
        for (ModuleClassLoader dependency : dependencies) {
            URL fromDependency = dependency.findResource(name);
            if (fromDependency != null) {
                return fromDependency;
            }
        }
        return super.getResource(name);
    }

    private Class<?> resolved(Class<?> type, boolean resolve) {
        if (resolve) {
            resolveClass(type);
        }
        return type;
    }

    private static boolean isShared(String name) {
        for (String prefix : SHARED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
