package dev.poc.core.version;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Périmètre de vie d'une session de jeu. Sa seule raison d'être : garantir qu'après l'arrêt,
 * <b>plus aucune référence forte</b> ne pointe vers le classloader de la version — condition
 * nécessaire pour que le GC le récupère et que la métaspace ne grossisse pas à chaque bascule.
 *
 * <h2>Les six voies de fuite connues</h2>
 * <ol>
 *   <li><b>Threads</b> — Minecraft en démarre une dizaine (Netty, chunk builder, moteur audio,
 *       téléchargement de skins). Un thread vivant retient sa {@code ThreadGroup}, son
 *       {@code Runnable}, donc son classloader. Le scope les recense et les joint.</li>
 *   <li><b>Hooks d'arrêt</b> — {@code Runtime.addShutdownHook} garde une référence jusqu'à la fin
 *       du <em>processus</em>, ce qui dans notre cas veut dire « pour toujours ».</li>
 *   <li><b>ThreadLocals sur les threads du shell</b> — une valeur posée par du code de la version
 *       sur le thread principal survit à la session. Il faut la retirer explicitement.</li>
 *   <li><b>Caches du JDK</b> — {@code java.beans.Introspector}, {@code ImageIO},
 *       {@code ResourceBundle}, les niveaux de {@code java.util.logging}, le cache de
 *       {@code ServiceLoader}. Tous mémorisent des {@code Class} donc des classloaders.</li>
 *   <li><b>Callbacks GLFW</b> — un lambda enregistré via {@code glfwSetKeyCallback} depuis le code
 *       de la version est retenu côté natif. Le shell doit posséder les callbacks et router.</li>
 *   <li><b>Objets GL</b> — pas une fuite Java, mais une fuite VRAM : traitée par la
 *       {@code GlArena}.</li>
 * </ol>
 */
public final class SessionScope implements AutoCloseable {

    private final String versionId;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final Set<Thread> threadsAtStart;
    private final System.Logger log = System.getLogger("session");

    public SessionScope(String versionId) {
        this.versionId = versionId;
        this.threadsAtStart = Thread.getAllStackTraces().keySet();
    }

    public void register(AutoCloseable resource) { resources.push(resource); }

    public String versionId() { return versionId; }

    /**
     * Threads apparus depuis la création du scope et toujours vivants. Ce sont les candidats à
     * l'arrêt : on ne peut pas les tuer de force (aucune API sûre depuis la dépréciation de
     * {@code Thread.stop}), donc la stratégie est : interrompre, attendre, et signaler ceux qui
     * résistent — un thread récalcitrant est un bug à corriger dans l'adaptateur, pas une fatalité
     * à masquer.
     */
    public List<Thread> leakedThreads() {
        List<Thread> leaked = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!t.isAlive() || t.isDaemon()) continue;
            if (threadsAtStart.contains(t)) continue;
            if (t == Thread.currentThread()) continue;
            leaked.add(t);
        }
        return leaked;
    }

    /** Interrompt les threads de la session et attend leur terminaison. */
    public List<Thread> stopThreads(long timeoutMillis) {
        List<Thread> candidates = leakedThreads();
        candidates.forEach(Thread::interrupt);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        for (Thread t : candidates) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;
            try {
                t.join(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return leakedThreads();   // ce qui reste après la tentative
    }

    /**
     * Vide les caches du JDK qui mémorisent des classes. Sans cet appel, une seule ouverture de
     * PNG par la version suffit à retenir son classloader via le cache d'{@code ImageIO}.
     */
    public void clearJdkCaches() {
        try {
            java.beans.Introspector.flushCaches();
        } catch (Throwable ignored) {
            // Introspector peut être absent d'une image jlink minimale.
        }
        try {
            javax.imageio.ImageIO.setUseCache(false);
        } catch (Throwable ignored) {
        }
        try {
            java.util.ResourceBundle.clearCache(getClass().getClassLoader());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        while (!resources.isEmpty()) {
            try {
                resources.pop().close();
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING,
                        "ressource de session non libérée (" + versionId + ")", e);
            }
        }
        clearJdkCaches();
    }
}
