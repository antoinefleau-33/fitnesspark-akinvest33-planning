package dev.poc.bediag;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filtres de sélection des BlockEntity.
 *
 * <p>Le défaut est {@link #WITH_RENDERER}, et ce choix est le cœur de l'outil : le coût de
 * <em>rendu</em> d'une BlockEntity tient à la présence d'un {@code BlockEntityRenderer}, pas à ce
 * qu'elle contient. Coffres, shulkers et ender chests en font partie — ils sont même parmi les plus
 * chers, modèle animé et manipulation de matrices à chaque frame — mais panneaux, bannières, lits,
 * cloches, conduits et têtes aussi. Se limiter aux trois conteneurs sous-estime le coût réel, et
 * laisse de côté les hoppers, qui dominent le coût de <em>tick</em>.
 *
 * <p>Mesuré sur un monde de test : 2298 BlockEntity avec renderer contre 1310 pour les trois
 * conteneurs seuls, soit 43 % du coût de rendu invisible avec le filtre le plus étroit.
 */
public final class BeFilter {

    public static final BeFilter ALL =
            new BeFilter("toutes", be -> true);

    /** Ont un renderer : le filtre pertinent pour un diagnostic d'affichage. */
    public static final BeFilter WITH_RENDERER =
            new BeFilter("avec renderer", BeSnapshot::hasRenderer);

    /** Ont un ticker client : le filtre pertinent pour un diagnostic de charge CPU. */
    public static final BeFilter TICKING =
            new BeFilter("ticking", BeSnapshot::ticking);

    /**
     * Ont un renderer mais sont hors portée de rendu. Une valeur élevée signale un
     * {@code getViewDistance} mal implémenté dans un mod tiers.
     */
    public static final BeFilter RENDERER_OUT_OF_RANGE =
            new BeFilter("renderer hors portée",
                    be -> be.hasRenderer() && !be.inViewDistance());

    private final String label;
    private final Predicate<BeSnapshot> predicate;

    private BeFilter(String label, Predicate<BeSnapshot> predicate) {
        this.label = label;
        this.predicate = predicate;
    }

    /** Filtre par identifiants de registre explicites, pour isoler un type suspect. */
    public static BeFilter ofTypes(String... typeIds) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(typeIds));
        String label = set.size() <= 3 ? String.join(", ", set) : set.size() + " types";
        return new BeFilter(label, be -> set.contains(be.typeId()));
    }

    /** Limite de distance : borne le coût du diagnostic lui-même. */
    public BeFilter withinBlocks(double blocks) {
        double sq = blocks * blocks;
        return new BeFilter(label + " ≤" + (int) blocks + "m",
                be -> predicate.test(be) && be.distanceSqToCamera() <= sq);
    }

    public boolean test(BeSnapshot snapshot) {
        return predicate.test(snapshot);
    }

    public String label() {
        return label;
    }

    /** Ordre de cyclage sur la touche dédiée. */
    public static final BeFilter[] CYCLE = {
            WITH_RENDERER, TICKING, ALL, RENDERER_OUT_OF_RANGE
    };
}
