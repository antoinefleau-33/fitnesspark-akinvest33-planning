package dev.poc.core.version;

import dev.poc.api.event.GameEvents;
import dev.poc.api.game.GameAdapter;
import dev.poc.api.game.GameEnvironment;
import dev.poc.api.module.GameBridge;
import dev.poc.core.event.SimpleEventBus;
import dev.poc.core.module.ModuleManager;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Machine à états du changement de version à chaud.
 *
 * <p>Toutes les étapes s'exécutent sur le <b>thread principal</b> (celui qui possède le contexte
 * OpenGL). Le switcher n'est pas asynchrone : un arrêt de session concurrent au rendu produit des
 * crashs natifs impossibles à diagnostiquer. Le coût est une pause de quelques secondes, masquée
 * par un overlay rendu par le shell — qui, lui, survit à la bascule puisqu'il n'appartient à
 * aucune version.
 *
 * <pre>
 *   IDLE ──start──▶ BOOTING ──▶ RUNNING ──switch──▶ STOPPING ──▶ UNLOADING ──▶ BOOTING ...
 *                      │                                │
 *                      └────────── FAILED ◀─────────────┘
 * </pre>
 */
public final class VersionSwitcher {

    public enum Phase { IDLE, BOOTING, RUNNING, STOPPING, UNLOADING, FAILED }

    /** Progression 0..1 + libellé, consommé par l'overlay de transition. */
    public record Progress(Phase phase, float fraction, String label) {}

    private final SimpleEventBus bus;
    private final ModuleManager modules;
    private final EnvironmentFactory environmentFactory;
    private final Consumer<Progress> progressSink;
    private final System.Logger log = System.getLogger("switcher");

    private Phase phase = Phase.IDLE;
    private VersionClassLoader loader;
    private GameAdapter adapter;
    private GameBridge bridge;
    private SessionScope scope;
    private String pendingAutoConnect;

    public interface EnvironmentFactory {
        GameEnvironment create(VersionInstall install, SessionScope scope, String autoConnect);
    }

    public VersionSwitcher(SimpleEventBus bus, ModuleManager modules,
                           EnvironmentFactory environmentFactory, Consumer<Progress> progressSink) {
        this.bus = bus;
        this.modules = modules;
        this.environmentFactory = environmentFactory;
        this.progressSink = progressSink == null ? p -> {} : progressSink;
    }

    public Phase phase() { return phase; }

    public Optional<GameBridge> bridge() { return Optional.ofNullable(bridge); }

    /** Le pont courant, pour le {@code Supplier<GameBridge>} du {@code ModuleManager}. */
    public GameBridge currentBridge() { return bridge; }

    /**
     * Bascule complète. Séquence stricte — l'ordre des cinq blocs n'est pas négociable, chaque
     * inversion produit soit une fuite, soit un crash natif.
     */
    public void switchTo(VersionInstall target) {
        try {
            // 1. Mémoriser le contexte à restaurer (adresse serveur) AVANT de perdre le pont.
            pendingAutoConnect = bridge != null && bridge.world() != null
                    ? bridge.world().serverAddress()
                    : null;

            // 2. Décharger les modules d'abord : ils tiennent des références vers le jeu.
            //    Les décharger après la session laisserait des handlers pointant dans le vide.
            report(Phase.STOPPING, 0.05f, "Déchargement des modules");
            modules.unloadAll();

            // 3. Arrêter la session courante.
            stopCurrent();

            // 4. Démarrer la nouvelle.
            bootInto(target);

            // 5. Recharger les modules avec le filtre de compatibilité de la nouvelle version.
            report(Phase.BOOTING, 0.9f, "Rechargement des modules");
            modules.loadAll(target.versionId());
            modules.modules().forEach(m -> modules.enable(m.metadata().id()));

            phase = Phase.RUNNING;
            report(Phase.RUNNING, 1f, "Prêt");
        } catch (Exception e) {
            phase = Phase.FAILED;
            log.log(System.Logger.Level.ERROR, "bascule vers " + target.versionId() + " échouée", e);
            report(Phase.FAILED, 1f, "Échec : " + e.getMessage());
            throw new IllegalStateException("bascule échouée", e);
        }
    }

    private void stopCurrent() {
        if (adapter == null) {
            phase = Phase.UNLOADING;
            return;
        }
        phase = Phase.STOPPING;

        report(Phase.STOPPING, 0.15f, "Arrêt de la session");
        if (bridge != null) bus.post(new GameEvents.SessionStopping(bridge));

        try {
            adapter.shutdown();
        } catch (Throwable t) {
            // On continue quoi qu'il arrive : rester bloqué ici laisse le client sans session.
            log.log(System.Logger.Level.ERROR, "shutdown de l'adaptateur en échec", t);
        }

        report(Phase.STOPPING, 0.3f, "Libération des ressources graphiques");
        // Les objets GL doivent partir AVANT le classloader : leur libération passe par du code
        // de la session, qui doit encore être chargeable.
        if (currentEnv != null) {
            int live = currentEnv.glArena().liveObjectCount();
            currentEnv.glArena().freeAll();
            if (live > 0) {
                log.log(System.Logger.Level.INFO, "{0} objet(s) GL libéré(s) d''office", live);
            }
        }

        report(Phase.STOPPING, 0.45f, "Arrêt des threads");
        List<Thread> stubborn = scope.stopThreads(3_000);
        if (!stubborn.isEmpty()) {
            log.log(System.Logger.Level.WARNING,
                    "threads survivants (fuite de classloader probable) : {0}",
                    stubborn.stream().map(Thread::getName).toList());
        }
        scope.close();

        report(Phase.UNLOADING, 0.55f, "Purge des références");
        int purged = bus.purgeClassLoader(loader);
        if (purged > 0) {
            log.log(System.Logger.Level.WARNING, "{0} handler(s) orphelin(s) purgé(s)", purged);
        }

        // Couper toutes les références avant de mesurer la collectabilité.
        adapter = null;
        bridge = null;
        currentEnv = null;
        VersionClassLoader dying = loader;
        loader = null;
        scope = null;

        try {
            dying.close();
        } catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "fermeture du classloader de version", e);
        }

        report(Phase.UNLOADING, 0.65f, "Vérification mémoire");
        if (!isCollectable(dying, 2_000)) {
            // Non fatal en soi, mais chaque bascule laissera alors ~150 Mo de métaspace.
            // En développement, déclencher ici un dump de heap et remonter les chaînes GC Roots.
            log.log(System.Logger.Level.WARNING,
                    "le classloader de version n'est pas collectable : fuite à investiguer");
        }
        phase = Phase.UNLOADING;
    }

    private GameEnvironment currentEnv;

    private void bootInto(VersionInstall install) throws Exception {
        phase = Phase.BOOTING;
        report(Phase.BOOTING, 0.7f, "Chargement de " + install.versionId());

        scope = new SessionScope(install.versionId());
        loader = new VersionClassLoader(
                install.versionId(),
                VersionClassLoader.classpathOf(install.classpath()),
                getClass().getClassLoader(),
                BytecodeTransformers.chainFor(install));

        currentEnv = environmentFactory.create(install, scope, pendingAutoConnect);
        pendingAutoConnect = null;

        report(Phase.BOOTING, 0.8f, "Démarrage du moteur");
        // ServiceLoader avec le classloader de la version : l'adaptateur est découvert dans
        // l'espace isolé, mais l'interface GameAdapter vient du shell — d'où l'importance de la
        // liste blanche parent-first sur dev.poc.api.
        adapter = ServiceLoader.load(GameAdapter.class, loader).stream()
                .map(ServiceLoader.Provider::get)
                .filter(a -> a.versionId().equals(install.versionId())
                        || a.family().equals(install.family()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "aucun GameAdapter pour " + install.versionId()));

        Thread current = Thread.currentThread();
        ClassLoader previousTccl = current.getContextClassLoader();
        current.setContextClassLoader(loader);
        try {
            bridge = adapter.boot(currentEnv);
        } finally {
            current.setContextClassLoader(previousTccl);
        }
        bus.post(new GameEvents.SessionStarted(bridge));
    }

    /** Boucle de jeu, appelée par le shell une fois par frame. */
    public void tick() {
        if (phase == Phase.RUNNING && adapter != null) adapter.tick();
    }

    public void render(float partialTicks) {
        if (phase == Phase.RUNNING && adapter != null) adapter.render(partialTicks);
    }

    private void report(Phase p, float fraction, String label) {
        this.phase = p;
        progressSink.accept(new Progress(p, fraction, label));
    }

    /**
     * Le classloader est-il réellement collectable ? Test empirique : {@code PhantomReference} +
     * {@code System.gc()}. Approximatif par nature (le GC n'est pas obligé d'obéir), mais il
     * détecte les régressions franches — un thread oublié, un handler resté abonné.
     */
    private static boolean isCollectable(ClassLoader cl, long timeoutMillis) {
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
        PhantomReference<ClassLoader> ref = new PhantomReference<>(cl, queue);
        cl = null;   // couper la dernière référence forte locale
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try {
            while (System.currentTimeMillis() < deadline) {
                System.gc();
                if (queue.remove(100) != null) return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ref.clear();
        }
        return false;
    }
}
