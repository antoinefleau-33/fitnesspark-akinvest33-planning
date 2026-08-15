package dev.poc.core.module;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Classloader d'un module, en délégation <b>parent-last</b> avec liste blanche.
 *
 * <p>Ordre de recherche d'une classe :
 * <ol>
 *   <li>déjà chargée ? on la rend ;</li>
 *   <li>package protégé (JDK, {@code dev.poc.api}, LWJGL) → <b>parent obligatoire</b>, sinon
 *       l'identité de type serait cassée et tout passage de {@code GameBridge} à travers la
 *       frontière lèverait un {@code ClassCastException} incompréhensible ;</li>
 *   <li>jar du module → chargement local, ce qui permet à un module d'embarquer sa propre version
 *       d'une lib sans polluer les autres ;</li>
 *   <li>modules dont il dépend (résolus en amont) ;</li>
 *   <li>parent en dernier recours.</li>
 * </ol>
 *
 * <p>Le piège classique : mettre {@code dev.poc.api} en parent-last « pour la symétrie ». Le module
 * chargerait alors sa propre copie de {@code GameBridge}, différente de celle du shell, et chaque
 * appel échouerait au cast. La liste blanche n'est pas un détail, c'est la condition de
 * fonctionnement du système.
 */
public final class ModuleClassLoader extends URLClassLoader {

    static { ClassLoader.registerAsParallelCapable(); }

    /** Packages qui DOIVENT venir du parent pour garantir l'identité de type. */
    private static final Set<String> PARENT_FIRST_PREFIXES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "dev.poc.api.",          // le contrat partagé
            "org.lwjgl.",            // natifs JNI : une seule instance par JVM (voir docs/03)
            "org.slf4j."             // façade de log commune
    );

    private final String moduleId;
    private final List<ModuleClassLoader> dependencies;

    public ModuleClassLoader(String moduleId, URL[] urls, ClassLoader parent,
                             List<ModuleClassLoader> dependencies) {
        super("module:" + moduleId, urls, parent);
        this.moduleId = moduleId;
        this.dependencies = List.copyOf(dependencies);
    }

    public String moduleId() { return moduleId; }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return finish(c, resolve);

            if (isParentFirst(name)) {
                return finish(super.loadClass(name, false), resolve);
            }

            try {
                c = findClass(name);           // jar du module d'abord
                return finish(c, resolve);
            } catch (ClassNotFoundException ignored) {
                // suite : dépendances déclarées
            }

            for (ModuleClassLoader dep : dependencies) {
                try {
                    return finish(dep.loadLocalOnly(name), resolve);
                } catch (ClassNotFoundException ignored) {
                    // dépendance suivante
                }
            }

            return finish(super.loadClass(name, false), resolve);
        }
    }

    /**
     * Chargement restreint au jar de ce module. Évite qu'une chaîne de dépendances circulaire
     * (A dépend de B qui dépend de A) ne parte en récursion infinie.
     */
    Class<?> loadLocalOnly(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            return c != null ? c : findClass(name);
        }
    }

    private Class<?> finish(Class<?> c, boolean resolve) {
        if (resolve) resolveClass(c);
        return c;
    }

    private static boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Les ressources suivent la même politique parent-last : le module gagne sur le shell. */
    @Override
    public URL getResource(String name) {
        URL local = findResource(name);
        if (local != null) return local;
        for (ModuleClassLoader dep : dependencies) {
            URL fromDep = dep.findResource(name);
            if (fromDep != null) return fromDep;
        }
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return super.getResources(name);
    }
}
