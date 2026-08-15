package dev.poc.core.module;

import dev.poc.api.event.EventBus;
import dev.poc.api.input.KeybindService;
import dev.poc.api.module.ClientModule;
import dev.poc.api.module.GameBridge;
import dev.poc.api.module.ModuleMetadata;
import dev.poc.core.event.SimpleEventBus;
import dev.poc.core.input.KeybindRegistry;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Cycle de vie des modules : découverte, résolution, instanciation, activation, déchargement. */
public final class ModuleManager {

    public enum State { DISCOVERED, LOADED, ENABLED, DISABLED, ERRORED, UNLOADED }

    public static final class Loaded {
        final ModuleMetadata metadata;
        final ClientModule instance;
        final ModuleContextImpl context;
        final ModuleClassLoader classLoader;
        State state = State.LOADED;
        Throwable error;

        Loaded(ModuleMetadata metadata, ClientModule instance, ModuleContextImpl context,
               ModuleClassLoader classLoader) {
            this.metadata = metadata;
            this.instance = instance;
            this.context = context;
            this.classLoader = classLoader;
        }

        public ModuleMetadata metadata() { return metadata; }
        public State state() { return state; }
        public Optional<Throwable> error() { return Optional.ofNullable(error); }
    }

    private final Path modulesDir;
    private final Path configRoot;
    private final SimpleEventBus bus;
    private final KeybindRegistry keybinds;
    private final Supplier<GameBridge> gameSupplier;
    private final Map<String, Loaded> loaded = new LinkedHashMap<>();
    private final System.Logger log = System.getLogger("modules");

    public ModuleManager(Path modulesDir, Path configRoot, SimpleEventBus bus,
                         KeybindRegistry keybinds, Supplier<GameBridge> gameSupplier) {
        this.modulesDir = modulesDir;
        this.configRoot = configRoot;
        this.bus = bus;
        this.keybinds = keybinds;
        this.gameSupplier = gameSupplier;
    }

    /**
     * @param gameVersion version courante, ou {@code null} au démarrage à froid. Rappeler cette
     *                    méthode après un changement de version recharge les modules avec le bon
     *                    filtrage de compatibilité.
     */
    public void loadAll(String gameVersion) {
        var candidates = ModuleDiscovery.scan(modulesDir);
        var resolution = DependencyResolver.resolve(candidates, gameVersion);
        resolution.warnings().forEach(w -> log.log(System.Logger.Level.WARNING, w));

        Map<String, ModuleClassLoader> loaders = new HashMap<>();

        for (var candidate : resolution.ordered()) {
            ModuleMetadata meta = candidate.metadata();
            try {
                List<ModuleClassLoader> depLoaders = meta.depends().keySet().stream()
                        .map(loaders::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();

                URL[] urls = { candidate.jar().toUri().toURL() };
                ModuleClassLoader cl = new ModuleClassLoader(
                        meta.id(), urls, getClass().getClassLoader(), depLoaders);
                loaders.put(meta.id(), cl);

                Class<?> entry = Class.forName(meta.entrypoint(), true, cl);
                if (!ClientModule.class.isAssignableFrom(entry)) {
                    throw new IllegalStateException(meta.entrypoint()
                            + " n'implémente pas ClientModule");
                }
                ClientModule instance =
                        (ClientModule) entry.getDeclaredConstructor().newInstance();

                var ctx = new ModuleContextImpl(meta, configRoot, bus, keybinds, gameSupplier);
                var record = new Loaded(meta, instance, ctx, cl);
                loaded.put(meta.id(), record);

                // Le TCCL est positionné sur le classloader du module : les librairies qui font
                // du Class.forName implicite (ServiceLoader, JAXB-like, drivers) trouvent alors
                // les classes du module au lieu d'échouer sur celles du shell.
                runWithContextClassLoader(cl, () -> instance.onLoad(ctx));
                log.log(System.Logger.Level.INFO, "module chargé: {0} {1}",
                        meta.id(), meta.version());
            } catch (Throwable t) {
                log.log(System.Logger.Level.ERROR, "échec du chargement de " + meta.id(), t);
                var record = loaded.get(meta.id());
                if (record != null) {
                    record.state = State.ERRORED;
                    record.error = t;
                }
            }
        }
    }

    public void enable(String id) {
        Loaded m = require(id);
        if (m.state == State.ENABLED) return;
        try {
            runWithContextClassLoader(m.classLoader, () -> m.instance.onEnable(m.context));
            m.state = State.ENABLED;
        } catch (Throwable t) {
            m.state = State.ERRORED;
            m.error = t;
            log.log(System.Logger.Level.ERROR, "échec de l'activation de " + id, t);
        }
    }

    public void disable(String id) {
        Loaded m = require(id);
        if (m.state != State.ENABLED) return;
        try {
            runWithContextClassLoader(m.classLoader, () -> m.instance.onDisable(m.context));
        } catch (Throwable t) {
            log.log(System.Logger.Level.ERROR, "échec de la désactivation de " + id, t);
        }
        m.state = State.DISABLED;
    }

    /**
     * Décharge un module et vérifie que son classloader est bien collectable.
     *
     * <p>Ordre imposé : {@code onDisable} → {@code onUnload} → fermeture du scope (révoque
     * abonnements et keybinds) → purge de sécurité sur le bus → fermeture du classloader.
     * Inverser deux étapes suffit à créer une fuite silencieuse.
     */
    public void unload(String id) {
        Loaded m = loaded.remove(id);
        if (m == null) return;
        disable(id);
        try {
            runWithContextClassLoader(m.classLoader, () -> m.instance.onUnload(m.context));
        } catch (Throwable t) {
            log.log(System.Logger.Level.ERROR, "échec du déchargement de " + id, t);
        }
        m.context.close();
        int purged = bus.purgeClassLoader(m.classLoader);
        if (purged > 0) {
            log.log(System.Logger.Level.WARNING,
                    "{0}: {1} handler(s) laissé(s) derrière, purgés d'office", id, purged);
        }
        keybinds.unregisterNamespace(id);
        try {
            m.classLoader.close();
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "fermeture du classloader de " + id, e);
        }
        m.state = State.UNLOADED;
    }

    public void unloadAll() {
        List.copyOf(loaded.keySet()).reversed().forEach(this::unload);
    }

    public Collection<Loaded> modules() { return List.copyOf(loaded.values()); }

    public Optional<Loaded> module(String id) { return Optional.ofNullable(loaded.get(id)); }

    private Loaded require(String id) {
        Loaded m = loaded.get(id);
        if (m == null) throw new IllegalArgumentException("module inconnu: " + id);
        return m;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static void runWithContextClassLoader(ClassLoader cl, ThrowingRunnable body)
            throws Exception {
        Thread t = Thread.currentThread();
        ClassLoader previous = t.getContextClassLoader();
        t.setContextClassLoader(cl);
        try {
            body.run();
        } finally {
            t.setContextClassLoader(previous);
        }
    }

    /**
     * Contrôle de fuite : après {@code unload}, le classloader doit devenir inaccessible. Sinon
     * chaque rechargement de module fait grossir la métaspace jusqu'au {@code OutOfMemoryError}.
     * À câbler sur un raccourci de debug pendant le développement.
     */
    public static boolean assertCollectable(ClassLoader cl, long timeoutMillis)
            throws InterruptedException {
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
        PhantomReference<ClassLoader> ref = new PhantomReference<>(cl, queue);
        // La variable locale doit disparaître avant le GC, d'où l'appel via un paramètre.
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            System.gc();
            if (queue.remove(50) != null) {
                ref.clear();
                return true;
            }
        }
        return false;
    }

    static URL[] toUrls(List<Path> paths) {
        List<URL> urls = new ArrayList<>(paths.size());
        for (Path p : paths) {
            try {
                urls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(p.toString(), e);
            }
        }
        return urls.toArray(URL[]::new);
    }
}
