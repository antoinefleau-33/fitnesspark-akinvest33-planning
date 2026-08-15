package dev.poc.core.version;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Classloader d'une version de Minecraft. Isole totalement le jeu (client jar remappé + ses
 * librairies) du shell.
 *
 * <h2>Pourquoi parent-last est ici obligatoire, pas un raffinement</h2>
 * Minecraft 1.8.9 embarque Guava 17, Gson 2.2.4, Netty 4.0.23 ; 1.20.1 embarque Guava 31, Gson
 * 2.10, Netty 4.1.82. En délégation parent-first classique, la première version chargée gagnerait
 * et la seconde exploserait sur des {@code NoSuchMethodError}. En parent-last, chaque version voit
 * ses propres librairies, et deux copies incompatibles de Guava coexistent sans se voir.
 *
 * <h2>La liste blanche</h2>
 * Trois familles doivent impérativement venir du parent :
 * <ul>
 *   <li>{@code java.*} — imposé par la JVM de toute façon ;</li>
 *   <li>{@code dev.poc.api.*} — sinon {@code GameBridge} chargé par la version n'est pas le même
 *       type que celui manipulé par le shell, et tout appel casse au cast ;</li>
 *   <li>{@code org.lwjgl.*} — <b>le point critique</b>. Une bibliothèque native ne peut être
 *       chargée que par un seul classloader dans une JVM : un second {@code System.load} de la
 *       même {@code .so}/{@code .dll} lève « Native Library ... already loaded in another
 *       classloader ». Si chaque version chargeait son propre LWJGL, la deuxième bascule
 *       échouerait systématiquement. LWJGL vit donc dans le shell, en un seul exemplaire, et
 *       toutes les versions l'empruntent.</li>
 * </ul>
 *
 * <p>Conséquence directe : toutes les versions doivent tourner sur <b>LWJGL 3</b>. Pour 1.12.2 et
 * antérieures (qui ciblent LWJGL 2 et son API {@code Display}/{@code Keyboard}/{@code Mouse}), il
 * faut réécrire ces appels au moment du remapping — c'est exactement ce que fait le projet
 * open-source {@code lwjgl3ify}. Voir {@code docs/03-version-switching.md}.
 */
public final class VersionClassLoader extends URLClassLoader {

    static { ClassLoader.registerAsParallelCapable(); }

    private static final Set<String> PARENT_FIRST_PREFIXES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "jakarta.",
            "dev.poc.api.",
            "org.lwjgl."
    );

    /**
     * Certaines classes doivent être chargées localement même si elles matchent la liste blanche.
     * Cas réel : les shims {@code org.lwjgl.opengl.Display} générés pour 1.8.9, qui portent un nom
     * LWJGL 2 mais sont du code à nous, spécifique à la version.
     */
    private static final Set<String> LOCAL_OVERRIDES = Set.of(
            "org.lwjgl.opengl.Display",
            "org.lwjgl.input.Keyboard",
            "org.lwjgl.input.Mouse",
            "org.lwjgl.LWJGLException"
    );

    private final String versionId;
    private final Function<byte[], byte[]> transformer;

    /**
     * @param transformer chaîne de transformation bytecode (Mixin, hooks d'événements, patchs de
     *                    compatibilité). Appliquée à la volée : c'est le point d'insertion qui
     *                    permet d'ajouter des hooks sans jamais modifier le jar sur disque.
     */
    public VersionClassLoader(String versionId, URL[] classpath, ClassLoader shell,
                              Function<byte[], byte[]> transformer) {
        super("mc:" + versionId, classpath, shell);
        this.versionId = versionId;
        this.transformer = transformer == null ? Function.identity() : transformer;
    }

    public String versionId() { return versionId; }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (isParentFirst(name) && !LOCAL_OVERRIDES.contains(name)) {
                    c = getParent().loadClass(name);
                } else {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        c = getParent().loadClass(name);
                    }
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL res = findResource(path);
        if (res == null) throw new ClassNotFoundException(name);
        try (var in = res.openStream()) {
            byte[] original = in.readAllBytes();
            byte[] transformed = transformer.apply(original);
            definePackageIfNeeded(name);
            return defineClass(name, transformed, 0, transformed.length);
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    private void definePackageIfNeeded(String className) {
        int dot = className.lastIndexOf('.');
        if (dot < 0) return;
        String pkg = className.substring(0, dot);
        if (getDefinedPackage(pkg) == null) {
            try {
                definePackage(pkg, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ignored) {
                // Course bénigne : un autre thread vient de le définir.
            }
        }
    }

    private static boolean isParentFirst(String name) {
        for (String p : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }

    static URL[] classpathOf(List<java.nio.file.Path> jars) {
        return jars.stream().map(p -> {
            try {
                return p.toUri().toURL();
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }).toArray(URL[]::new);
    }
}
