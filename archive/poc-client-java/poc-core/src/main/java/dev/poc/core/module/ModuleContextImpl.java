package dev.poc.core.module;

import dev.poc.api.event.EventBus;
import dev.poc.api.input.KeyEvent;
import dev.poc.api.input.Keybind;
import dev.poc.api.input.KeybindService;
import dev.poc.api.input.Chord;
import dev.poc.api.module.GameBridge;
import dev.poc.api.module.ModuleContext;
import dev.poc.api.module.ModuleMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Contexte-scope. Chaque capacité rendue au module est enveloppée pour être révocable, et les
 * révocations sont empilées en LIFO.
 *
 * <p>C'est ce qui rend le déchargement à chaud fiable sans faire confiance au module : même un
 * module qui « oublie » de se nettoyer dans {@code onUnload} ne peut pas laisser d'abonnement ni
 * de keybind derrière lui. Sans ce mécanisme, un seul module négligent suffit à retenir un
 * classloader entier et à faire échouer le changement de version.
 */
public final class ModuleContextImpl implements ModuleContext {

    private final ModuleMetadata metadata;
    private final Path configDir;
    private final EventBus delegateBus;
    private final KeybindService delegateKeybinds;
    private final Supplier<GameBridge> gameSupplier;
    private final Deque<AutoCloseable> cleanups = new ArrayDeque<>();
    private final System.Logger logger;
    private boolean closed;

    public ModuleContextImpl(ModuleMetadata metadata,
                             Path configRoot,
                             EventBus bus,
                             KeybindService keybinds,
                             Supplier<GameBridge> gameSupplier) {
        this.metadata = metadata;
        this.configDir = configRoot.resolve(metadata.id());
        this.delegateBus = bus;
        this.delegateKeybinds = keybinds;
        this.gameSupplier = gameSupplier;
        this.logger = System.getLogger("module/" + metadata.id());
    }

    @Override
    public ModuleMetadata metadata() { return metadata; }

    @Override
    public EventBus events() {
        checkOpen();
        return new EventBus() {
            @Override
            public <E extends dev.poc.api.event.Event> Subscription subscribe(
                    Class<E> type, int priority, Consumer<E> handler) {
                Subscription s = delegateBus.subscribe(type, priority, handler);
                cleanups.push(s::close);
                return s;
            }

            @Override
            public Subscription register(Object listener) {
                Subscription s = delegateBus.register(listener);
                cleanups.push(s::close);
                return s;
            }

            @Override
            public <E extends dev.poc.api.event.Event> E post(E event) {
                return delegateBus.post(event);
            }
        };
    }

    @Override
    public KeybindService keybinds() {
        checkOpen();
        return new KeybindService() {
            @Override
            public Handle register(Keybind bind, Consumer<KeyEvent> handler) {
                // Garde-fou : le namespace du keybind doit correspondre à l'id du module.
                // Sans cette règle, un module peut squatter le namespace d'un autre et
                // réintroduire exactement le problème d'écrasement qu'on cherche à éliminer.
                if (!bind.namespace().equals(metadata.id())) {
                    throw new IllegalArgumentException("le module '" + metadata.id()
                            + "' ne peut pas enregistrer le keybind '" + bind.id()
                            + "' : namespace attendu '" + metadata.id() + ":'");
                }
                Handle h = delegateKeybinds.register(bind, handler);
                cleanups.push(h::close);
                return h;
            }

            @Override public List<Conflict> conflicts() { return delegateKeybinds.conflicts(); }
            @Override public void rebind(String id, Chord c) { delegateKeybinds.rebind(id, c); }
            @Override public Chord chordOf(String id) { return delegateKeybinds.chordOf(id); }
            @Override public boolean isActive(String id) { return delegateKeybinds.isActive(id); }
            @Override public List<Keybind> registered() { return delegateKeybinds.registered(); }
        };
    }

    @Override
    public Path configDir() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return configDir;
    }

    @Override
    public Optional<GameBridge> game() { return Optional.ofNullable(gameSupplier.get()); }

    @Override
    public void log(System.Logger.Level level, String message, Object... args) {
        logger.log(level, message, args);
    }

    @Override
    public void onClose(AutoCloseable cleanup) {
        checkOpen();
        cleanups.push(cleanup);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        while (!cleanups.isEmpty()) {
            try {
                cleanups.pop().close();
            } catch (Exception e) {
                // On continue : une action de nettoyage en échec ne doit pas bloquer les suivantes.
                logger.log(System.Logger.Level.WARNING, "nettoyage en échec", e);
            }
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("contexte du module " + metadata.id() + " fermé");
    }
}
