package dev.poc.bediag;

/**
 * Vue immuable d'une BlockEntity, sans aucun type Minecraft.
 *
 * <p>Cette séparation n'est pas cosmétique : elle rend le filtrage, l'agrégation et la politique
 * d'occlusion testables avec un simple {@code javac}, sans lancer le jeu. Seul le collecteur qui
 * remplit ces instances dépend de Minecraft.
 *
 * @param hasRenderer   possède un {@code BlockEntityRenderer} — le vrai critère de coût de rendu
 * @param ticking       possède un ticker côté client
 * @param inViewDistance dans la portée de rendu du renderer
 */
public record BeSnapshot(
        int x, int y, int z,
        String typeId,
        boolean hasRenderer,
        boolean ticking,
        boolean inViewDistance,
        int chunkX, int chunkZ,
        double distanceSqToCamera) {

    public long chunkKey() {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Nom court pour l'affichage : « chest » plutôt que « minecraft:chest ». */
    public String shortType() {
        int colon = typeId.indexOf(':');
        return colon >= 0 ? typeId.substring(colon + 1) : typeId;
    }
}
