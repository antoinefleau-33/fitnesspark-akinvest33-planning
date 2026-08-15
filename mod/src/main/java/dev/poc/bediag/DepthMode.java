package dev.poc.bediag;

/**
 * Traitement de l'occlusion pour les boîtes de diagnostic.
 */
public enum DepthMode {

    /** Test de profondeur normal : masqué par le terrain, comme n'importe quelle géométrie. */
    OCCLUDED,

    /**
     * Deux passes : la partie visible en pleine intensité, la partie cachée atténuée.
     *
     * <p>Le mode recommandé pour un diagnostic, et il porte strictement plus d'information que
     * les deux autres : il laisse distinguer d'un coup d'œil ce qui est réellement visible de ce
     * qui est occlus — exactement la question qu'on se pose en déboguant du culling. Un aplat
     * traversant, lui, écrase cette distinction.
     */
    OCCLUDED_DIMMED,

    /**
     * Test de profondeur désactivé : tout traverse le terrain.
     *
     * <p><b>Dégradé en {@link #OCCLUDED_DIMMED} hors solo strict</b>, voir {@link #resolve}.
     */
    THROUGH_WALLS;

    /**
     * Politique d'occlusion, définie ici et nulle part ailleurs.
     *
     * <p>Centraliser la règle en un point unique et testable évite qu'un autre morceau du mod la
     * réimplémente — c'est-à-dire l'oublie. Le rendu est le seul appelant.
     *
     * @param singleplayer serveur intégré actif ET non publié sur le réseau local
     */
    public static DepthMode resolve(DepthMode requested, boolean singleplayer) {
        if (requested == THROUGH_WALLS && !singleplayer) return OCCLUDED_DIMMED;
        return requested;
    }

    public DepthMode nextMode() {
        DepthMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public String label() {
        switch (this) {
            case OCCLUDED: return "Occlusion normale";
            case OCCLUDED_DIMMED: return "Visible net / caché atténué";
            default: return "Traversant (solo uniquement)";
        }
    }
}
