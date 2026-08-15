package dev.poc.modules.bediag;

import dev.poc.api.module.GameBridge.BlockEntitySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrégation des mesures sur une frame.
 *
 * <p>Toutes les structures sont réutilisées d'une frame à l'autre ({@code clear()} plutôt que
 * réallocation). Un outil de mesure de performance qui alloue à chaque frame fausse la mesure
 * qu'il produit : les pauses GC qu'il provoque seraient attribuées au code observé.
 */
public final class BeStats {

    /** Compteurs par type de BlockEntity. */
    public static final class TypeCount {
        public String typeId;
        public int total;
        public int withRenderer;
        public int ticking;
        public int culled;
    }

    private final Map<String, TypeCount> byType = new HashMap<>();
    private final Map<Long, int[]> byChunk = new HashMap<>();
    private final List<TypeCount> sortedScratch = new ArrayList<>();

    public int total;
    public int matched;
    public int withRenderer;
    public int ticking;
    public int culled;
    public int drawn;

    /** Coût du diagnostic lui-même, en nanosecondes. */
    public long collectNanos;
    public long submitNanos;

    /** Moyenne glissante sur 60 frames : une valeur instantanée est trop bruitée pour être lue. */
    private final long[] collectHistory = new long[60];
    private int historyIndex;

    public void reset() {
        byType.values().forEach(t -> { t.total = 0; t.withRenderer = 0; t.ticking = 0; t.culled = 0; });
        byChunk.values().forEach(c -> c[0] = 0);
        total = matched = withRenderer = ticking = culled = drawn = 0;
    }

    public void record(BlockEntitySnapshot be, boolean matchedFilter) {
        total++;
        if (!matchedFilter) return;

        matched++;
        if (be.hasRenderer()) withRenderer++;
        if (be.ticking()) ticking++;
        if (be.hasRenderer() && !be.inViewDistance()) culled++;

        TypeCount tc = byType.computeIfAbsent(be.typeId(), id -> {
            TypeCount t = new TypeCount();
            t.typeId = id;
            return t;
        });
        tc.total++;
        if (be.hasRenderer()) tc.withRenderer++;
        if (be.ticking()) tc.ticking++;
        if (be.hasRenderer() && !be.inViewDistance()) tc.culled++;

        byChunk.computeIfAbsent(be.chunkKey(), k -> new int[1])[0]++;
    }

    public void endCollect(long nanos) {
        this.collectNanos = nanos;
        collectHistory[historyIndex] = nanos;
        historyIndex = (historyIndex + 1) % collectHistory.length;
    }

    public double averageCollectMicros() {
        long sum = 0;
        int n = 0;
        for (long v : collectHistory) {
            if (v > 0) { sum += v; n++; }
        }
        return n == 0 ? 0 : sum / (double) n / 1000.0;
    }

    /** Types les plus nombreux, tri décroissant. */
    public List<TypeCount> topTypes(int limit) {
        sortedScratch.clear();
        for (TypeCount t : byType.values()) {
            if (t.total > 0) sortedScratch.add(t);
        }
        sortedScratch.sort(Comparator.comparingInt((TypeCount t) -> -t.total));
        return sortedScratch.subList(0, Math.min(limit, sortedScratch.size()));
    }

    /**
     * Chunks dont le nombre de BlockEntity dépasse le seuil. C'est la mesure qui répond
     * directement à « quel chunk fait chuter le framerate » : une ferme à hoppers ou un système de
     * tri concentre des centaines de BE dans deux ou trois chunks, et la moyenne globale ne le
     * montre jamais.
     */
    public List<long[]> hotspotChunks(int threshold, int limit) {
        List<long[]> out = new ArrayList<>();
        byChunk.forEach((key, count) -> {
            if (count[0] >= threshold) out.add(new long[]{key, count[0]});
        });
        out.sort(Comparator.comparingLong((long[] e) -> -e[1]));
        return out.subList(0, Math.min(limit, out.size()));
    }

    public static int chunkX(long key) { return (int) (key >> 32); }
    public static int chunkZ(long key) { return (int) key; }

    /** Purge les entrées à zéro : sans ça, la map grossit indéfiniment en explorant le monde. */
    public void compact() {
        byType.values().removeIf(t -> t.total == 0);
        byChunk.entrySet().removeIf(e -> e.getValue()[0] == 0);
    }
}
