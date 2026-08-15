package dev.poc.core.version;

import java.nio.file.Path;
import java.util.List;

/**
 * Provisionnement d'une version : téléchargement, remapping, mise en cache.
 *
 * <p><b>Principe directeur : tout le travail coûteux est fait à l'installation, jamais à la
 * bascule.</b> Remapper le client 1.20.1 prend 10 à 30 secondes ; le faire au moment où
 * l'utilisateur clique « 1.20.1 » dans le menu rendrait la fonctionnalité inutilisable. Une fois
 * installée, une version bascule en 2 à 4 secondes parce qu'il ne reste que : créer un
 * classloader, charger ~6000 classes, initialiser les registres.
 *
 * <h2>Pipeline d'installation</h2>
 * <ol>
 *   <li><b>Manifeste</b> — {@code https://piston-meta.mojang.com/mc/game/version_manifest_v2.json}
 *       donne la liste des versions et l'URL du JSON de chacune.</li>
 *   <li><b>JSON de version</b> — décrit {@code downloads.client}, {@code libraries} (avec leurs
 *       règles d'OS), {@code assetIndex}, {@code javaVersion}, et pour les versions récentes
 *       {@code downloads.client_mappings} (les mappings officiels Mojang, format ProGuard).</li>
 *   <li><b>Librairies</b> — filtrées par les règles de plateforme, téléchargées dans un store
 *       partagé façon Maven ({@code libraries/<group>/<artifact>/<version>/}). Deux versions de
 *       Minecraft qui partagent une librairie ne la téléchargent qu'une fois.</li>
 *   <li><b>Natifs</b> — extraits par version. Sauf LWJGL, qu'on <em>ignore délibérément</em> :
 *       le shell fournit le sien (voir {@link VersionClassLoader}).</li>
 *   <li><b>Mappings</b> — trois sources possibles :
 *     <ul>
 *       <li><b>Mojmap</b> (officiel) : disponible à partir de 1.14.4, noms de classes/méthodes
 *           réels, mais licence propre et rien avant 1.14 ;</li>
 *       <li><b>Yarn</b> (FabricMC) : couvre 1.14+, noms communautaires, format tiny v2 ;</li>
 *       <li><b>MCP / Searge</b> : la seule option praticable pour 1.8.9 et 1.12.2.</li>
 *     </ul>
 *   </li>
 *   <li><b>Remapping</b> — c'est l'étape qui rend le système viable. On remappe le client vers un
 *       <b>espace de noms unifié et stable</b>, comme le fait l'{@code intermediary} de Fabric.
 *       Outil : {@code net.fabricmc:tiny-remapper}.</li>
 *   <li><b>Cache</b> — sortie dans {@code versions/<id>/client-poc.jar}, avec un fichier
 *       d'empreinte (hash du jar source + hash des mappings + version du remapper) pour invalider
 *       proprement quand l'un des trois change.</li>
 * </ol>
 *
 * <h2>La limite qu'il faut accepter</h2>
 * Un espace de noms unifié n'unifie pas la <em>structure</em> du jeu. {@code EntityPlayerSP}
 * (1.8.9) et {@code LocalPlayer} (1.20.1) ne sont pas la même classe avec un autre nom : les
 * champs, la hiérarchie et le modèle de rendu ont changé. Aucun remapping ne comble cet écart.
 * D'où l'architecture retenue : les modules ne touchent jamais aux classes du jeu, ils passent par
 * {@code GameBridge}, et seul l'adaptateur (quelques milliers de lignes par famille de version)
 * connaît les détails. C'est le compromis qui rend le multi-version réellement maintenable.
 */
public interface VersionProvisioner {

    /** Versions disponibles au téléchargement (manifeste distant). */
    List<Available> listAvailable() throws Exception;

    /** Versions déjà installées localement, prêtes à démarrer sans réseau. */
    List<VersionInstall> listInstalled();

    /**
     * Installe (ou répare) une version. Longue opération, à exécuter hors du thread de rendu.
     *
     * @param progress rapport 0..1 + libellé, pour la barre de progression de l'UI
     */
    VersionInstall install(String versionId, ProgressListener progress) throws Exception;

    /** Supprime les artefacts d'une version. Les librairies partagées ne sont pas touchées. */
    void uninstall(String versionId) throws Exception;

    record Available(String id, String type, String releaseTime, String url) {}

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(float fraction, String label);
    }

    /** Espaces de noms de mappings, dans l'ordre du pipeline de remapping. */
    enum MappingNamespace {
        /** Noms obfusqués du jar Mojang, ex. {@code cft}. */
        OBFUSCATED,
        /** Espace stable maison, ex. {@code net.minecraft.class_1234} — cible du remapping. */
        INTERMEDIARY,
        /** Noms lisibles pour le développement et les stacktraces. */
        NAMED
    }

    /**
     * Abstraction du remapper, pour que {@code poc-core} n'ait pas tiny-remapper en dépendance
     * dure (il tirerait ASM dans le classloader du shell, où Minecraft en apporte déjà un autre).
     */
    interface Remapper {
        void remap(Path inputJar, Path outputJar, Path mappings,
                   MappingNamespace from, MappingNamespace to,
                   List<Path> classpath) throws Exception;
    }
}
