package dev.poc.api.render;

/**
 * Dessin de primitives de débogage dans l'espace du monde, après le rendu du terrain.
 *
 * <p>L'implémentation batche tout et n'émet les draw calls qu'au {@link #flush()} de fin de frame.
 * Un module qui appelle {@link #box} 5 000 fois produit deux draw calls, pas 5 000.
 */
public interface WorldRenderer {

    /**
     * Traitement de l'occlusion. Ce n'est pas un simple interrupteur on/off : le mode
     * {@link #OCCLUDED_DIMMED} porte plus d'information que les deux autres, parce qu'il permet
     * de distinguer d'un coup d'œil ce qui est réellement visible de ce qui est caché — exactement
     * la question qu'on se pose en déboguant du culling.
     */
    enum DepthMode {
        /** Test de profondeur normal : masqué par le terrain, comme n'importe quelle géométrie. */
        OCCLUDED,

        /**
         * Deux passes : la partie visible en pleine intensité, la partie cachée atténuée.
         * Le mode recommandé pour un diagnostic.
         */
        OCCLUDED_DIMMED,

        /**
         * Test de profondeur désactivé : tout est visible à travers le terrain.
         *
         * <p><b>Dégradé automatiquement en {@link #OCCLUDED_DIMMED} hors solo</b> — voir
         * {@code GameBridge.isSingleplayer()}. La dégradation est journalisée une fois par
         * session, pas à chaque frame.
         */
        THROUGH_WALLS;

        /**
         * Politique d'occlusion, définie ici et nulle part ailleurs.
         *
         * <p>Centraliser la règle en un point testable évite qu'un futur module la
         * réimplémente — c'est-à-dire l'oublie. L'implémentation de {@link WorldRenderer} est le
         * seul appelant, et cette méthode est vérifiable sans contexte graphique.
         */
        public static DepthMode resolve(DepthMode requested, boolean singleplayer) {
            if (requested == THROUGH_WALLS && !singleplayer) return OCCLUDED_DIMMED;
            return requested;
        }
    }

    /** Boîte filaire alignée sur les axes, en coordonnées monde absolues. */
    void box(double minX, double minY, double minZ,
             double maxX, double maxY, double maxZ,
             int argb, DepthMode depthMode);

    /** Cube d'un bloc à la position donnée, avec une marge optionnelle. */
    default void blockBox(int x, int y, int z, double inset, int argb, DepthMode depthMode) {
        box(x + inset, y + inset, z + inset,
            x + 1 - inset, y + 1 - inset, z + 1 - inset, argb, depthMode);
    }

    /** Contour d'une section de chunk 16×16×16. */
    default void chunkSection(int chunkX, int sectionY, int chunkZ, int argb, DepthMode depthMode) {
        box(chunkX * 16.0, sectionY * 16.0, chunkZ * 16.0,
            chunkX * 16.0 + 16, sectionY * 16.0 + 16, chunkZ * 16.0 + 16, argb, depthMode);
    }

    void line(double x1, double y1, double z1, double x2, double y2, double z2,
              int argb, DepthMode depthMode);

    /** Émet les draw calls et restaure l'état GL. Appelé par le shell en fin de frame. */
    void flush();

    /** Nombre de primitives accumulées cette frame — utile pour s'auto-surveiller. */
    int pendingPrimitives();
}
