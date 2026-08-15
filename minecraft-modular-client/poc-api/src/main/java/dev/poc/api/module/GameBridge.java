package dev.poc.api.module;

/**
 * Abstraction du jeu, <b>indépendante de la version</b>. C'est la pièce maîtresse qui permet à un
 * module unique de fonctionner de 1.8.9 à 1.20.1 : un module ne référence jamais une classe de
 * Minecraft, il ne parle qu'à cette interface. L'adaptateur spécifique à la version (voir
 * {@code poc-adapters/}) est le seul code à réécrire quand une nouvelle version sort.
 *
 * <p>Cette interface est chargée par le classloader racine, donc son identité de classe est
 * stable de part et d'autre de la frontière d'isolation. Corollaire : elle ne doit exposer que
 * des types du JDK et des types de {@code poc-api}.
 */
public interface GameBridge {

    /** Identifiant de version au sens du manifeste Mojang, ex. {@code "1.20.1"}. */
    String versionId();

    /** Famille d'adaptateur, ex. {@code "1.20"} — utile pour les modules qui doivent dégrader. */
    String versionFamily();

    LocalPlayer player();

    World world();

    Hud hud();

    /** Vrai si un écran (inventaire, menu, chat) capture actuellement la saisie. */
    boolean isScreenOpen();

    /** Vrai si un champ de texte a le focus — utilisé par le pipeline d'input. */
    boolean isTextInputFocused();

    /**
     * Vrai uniquement si le monde tourne sur le serveur intégré <b>et</b> qu'aucune connexion
     * distante n'est ouverte (donc : ni multijoueur, ni monde solo « ouvert au LAN »).
     *
     * <p>Les outils de diagnostic qui traversent la géométrie s'appuient sur cette méthode pour
     * dégrader automatiquement hors solo. Elle doit être évaluée à chaque frame et jamais mise en
     * cache : ouvrir au LAN se fait en cours de partie.
     */
    boolean isSingleplayer();

    /** Caméra de rendu de la frame courante. */
    Camera camera();

    /**
     * Parcourt les BlockEntity chargées côté client.
     *
     * <p>Visiteur plutôt que {@code List} : sur une base construite, un monde chargé dépasse
     * couramment 20 000 BlockEntity. Matérialiser une liste à chaque frame, c'est 20 000
     * allocations à 60 Hz — le diagnostic finirait par coûter plus cher que ce qu'il mesure.
     * Le visiteur permet de filtrer et d'agréger sans rien allouer.
     */
    void forEachBlockEntity(BlockEntityVisitor visitor);

    @FunctionalInterface
    interface BlockEntityVisitor {
        void visit(BlockEntitySnapshot snapshot);
    }

    /**
     * Vue immuable d'une BlockEntity, en types du JDK uniquement (elle traverse la frontière des
     * classloaders).
     *
     * @param typeId       identifiant de registre, ex. {@code "minecraft:chest"}
     * @param hasRenderer  possède un {@code BlockEntityRenderer} — c'est le vrai critère de coût
     *                     de rendu, et donc le filtre pertinent pour un diagnostic d'affichage
     * @param ticking      possède un ticker côté client ({@code TickingBlockEntity})
     * @param inViewDistance dans la distance de rendu du BER ({@code getViewDistance})
     */
    record BlockEntitySnapshot(
            int x, int y, int z,
            String typeId,
            boolean hasRenderer,
            boolean ticking,
            boolean inViewDistance,
            int chunkX, int chunkZ,
            double distanceSqToCamera) {

        public long chunkKey() { return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL); }
    }

    /**
     * Caméra. La matrice est fournie en <b>view-projection relative à la caméra</b> : la
     * translation de la position caméra en est déjà retirée. Voir {@code BoxRenderer} pour la
     * raison — à x = 1 000 000, un {@code float} ne distingue plus que ~0,06 bloc et les boîtes
     * tremblent visiblement.
     */
    record Camera(double x, double y, double z, float yaw, float pitch,
                  float[] viewProjectionMatrix) {

        public Camera {
            if (viewProjectionMatrix == null || viewProjectionMatrix.length != 16) {
                throw new IllegalArgumentException("matrice 4x4 attendue (16 floats)");
            }
            viewProjectionMatrix = viewProjectionMatrix.clone();
        }

        @Override
        public float[] viewProjectionMatrix() { return viewProjectionMatrix.clone(); }
    }

    interface LocalPlayer {
        double x();
        double y();
        double z();
        float yaw();
        float pitch();
        float health();
        boolean onGround();
        String name();
    }

    interface World {
        String dimensionId();
        long dayTime();
        int playerCount();
        /** Nom du serveur, ou {@code null} en solo. */
        String serverAddress();
    }

    /** Dessin dans l'espace écran du jeu, délégué au renderer du shell. */
    interface Hud {
        int width();
        int height();
        void text(float x, float y, String text, int argb);
        void rect(float x, float y, float w, float h, int argb);
        void roundedRect(float x, float y, float w, float h, float radius, int argb);
    }
}
