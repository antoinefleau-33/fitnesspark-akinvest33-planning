package dev.poc.core.version;

import java.nio.file.Path;
import java.util.List;

/**
 * Une version installée et prête à démarrer. Produite par le {@code VersionProvisioner} à
 * l'installation, puis mise en cache sur disque — le changement de version à chaud ne doit rien
 * télécharger ni rien remapper, sinon on parle de plusieurs minutes au lieu de quelques secondes.
 *
 * <p>Arborescence cible :
 * <pre>
 *   versions/1.20.1/
 *     client-poc.jar          ← client vanilla déjà remappé dans l'espace de noms stable
 *     libraries.txt           ← classpath résolu, un chemin par ligne
 *     mappings.tiny           ← conservé pour l'affichage de stacktraces lisibles
 *     manifest.json
 *   assets/
 *     indexes/8.json
 *     objects/ab/abcdef...    ← partagé par toutes les versions (adressage par hash)
 * </pre>
 *
 * @param assetIndexName nom de l'index d'assets, partagé entre versions proches (1.20.1 et 1.20.2
 *                       utilisent le même) — d'où le store d'objets commun
 * @param requiresLwjglShim vrai pour les versions ≤ 1.12.2, qui ciblent l'API LWJGL 2
 */
public record VersionInstall(
        String versionId,
        String family,
        Path clientJar,
        List<Path> libraries,
        Path nativesDir,
        String assetIndexName,
        String adapterClass,
        boolean requiresLwjglShim,
        int javaMajorTarget) {

    /** Classpath complet passé au {@link VersionClassLoader}. */
    public List<Path> classpath() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(clientJar),
                libraries.stream()).toList();
    }

    public static VersionInstall vanilla(String versionId, String family, Path root,
                                         List<Path> libs, String assetIndex,
                                         String adapterClass, boolean lwjglShim, int javaTarget) {
        return new VersionInstall(
                versionId, family,
                root.resolve("client-poc.jar"),
                libs,
                root.resolve("natives"),
                assetIndex,
                adapterClass,
                lwjglShim,
                javaTarget);
    }
}
