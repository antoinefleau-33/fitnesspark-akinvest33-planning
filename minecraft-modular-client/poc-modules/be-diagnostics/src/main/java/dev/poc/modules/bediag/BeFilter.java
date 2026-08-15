package dev.poc.modules.bediag;

import dev.poc.api.module.GameBridge.BlockEntitySnapshot;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filtres de sélection des BlockEntity.
 *
 * <p>Le filtre par défaut est {@link #WITH_RENDERER}, et ce choix est le cœur de l'outil : le coût
 * de <em>rendu</em> d'une BlockEntity est déterminé par la présence d'un {@code BlockEntityRenderer},
 * pas par ce qu'elle contient. Coffres, shulkers et ender chests en font partie — ils sont parmi
 * les plus chers, modèle animé et {@code PoseStack} à chaque frame — mais panneaux, bannières,
 * lits, cloches, conduits et têtes aussi. Filtrer sur les trois conteneurs à butin donnerait une
 * mesure de rendu incomplète et sans rapport avec la question posée.
 *
 * <p>Pour le coût de <em>tick</em>, c'est {@link #TICKING} qu'il faut : hoppers en tête, puis
 * fourneaux, brasseries et repères. Un hopper coûte plusieurs ordres de grandeur de plus qu'un
 * coffre côté serveur.
 */
public final class BeFilter {

    public static final BeFilter ALL =
            new BeFilter("toutes", be -> true);

    /** Ont un BER : le filtre pertinent pour un diagnostic de rendu. */
    public static final BeFilter WITH_RENDERER =
            new BeFilter("avec renderer", BlockEntitySnapshot::hasRenderer);

    /** Ont un ticker client : le filtre pertinent pour un diagnostic de charge CPU. */
    public static final BeFilter TICKING =
            new BeFilter("ticking", BlockEntitySnapshot::ticking);

    /**
     * Ont un BER mais sont hors distance de rendu. Utile pour repérer une BE qui devrait être
     * culled et ne l'est pas — le symptôme typique d'un {@code getViewDistance} mal implémenté
     * dans un mod tiers.
     */
    public static final BeFilter RENDERER_OUT_OF_RANGE =
            new BeFilter("renderer hors portée",
                    be -> be.hasRenderer() && !be.inViewDistance());

    private final String label;
    private final Predicate<BlockEntitySnapshot> predicate;

    private BeFilter(String label, Predicate<BlockEntitySnapshot> predicate) {
        this.label = label;
        this.predicate = predicate;
    }

    /** Filtre par identifiants de registre explicites, ex. pour isoler un type suspect. */
    public static BeFilter ofTypes(String... typeIds) {
        Set<String> set = new LinkedHashSet<>(Set.of(typeIds));
        String label = set.size() <= 3 ? String.join(", ", set) : set.size() + " types";
        return new BeFilter(label, be -> set.contains(be.typeId()));
    }

    public BeFilter and(BeFilter other) {
        return new BeFilter(label + " + " + other.label,
                be -> predicate.test(be) && other.predicate.test(be));
    }

    /** Limite de distance : borne le coût du diagnostic lui-même. */
    public BeFilter withinBlocks(double blocks) {
        double sq = blocks * blocks;
        return new BeFilter(label + " ≤" + (int) blocks + "m",
                be -> predicate.test(be) && be.distanceSqToCamera() <= sq);
    }

    public boolean test(BlockEntitySnapshot be) { return predicate.test(be); }

    public String label() { return label; }

    /** Ordre de cyclage sur la touche dédiée. */
    public static final BeFilter[] CYCLE = {
            WITH_RENDERER, TICKING, ALL, RENDERER_OUT_OF_RANGE
    };
}
