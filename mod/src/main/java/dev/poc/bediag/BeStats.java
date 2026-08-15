package dev.poc.bediag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrégation des mesures sur une frame.
 *
 * <p>Toutes les structures sont réutilisées d'une frame à l'autre — remise à zéro plutôt que
 * réallocation. Un outil de mesure de performance qui alloue à chaque frame fausse sa propre
 * mesure : les pauses du ramasse-miettes qu'il provoque seraient attribuées au code observé.
 */
public final class BeStats {

    public static final class TypeCount {
        public String typeId = "";
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

    private final long[] history = new long[60];
    private int historyIndex;

    public void reset() {
        for (TypeCount t : byType.values()) {
            t.total = 0;
            t.withRenderer = 0;
            t.ticking = 0;
            t.culled = 0;
        }
        for (int[] c : byChunk.values()) c[0] = 0;
        total = matched = withRenderer = ticking = culled = drawn = 0;
    }

    public void record(BeSnapshot be, boolean matchedFilter) {
        total++;
        if (!matchedFilter) return;

        matched++;
        boolean outOfRange = be.hasRenderer() && !be.inViewDistance();
        if (be.hasRenderer()) withRenderer++;
        if (be.ticking()) ticking++;
        if (outOfRange) culled++;

        TypeCount tc = byType.computeIfAbsent(be.typeId(), id -> {
            TypeCount created = new TypeCount();
            created.typeId = id;
            return created;
        });
        tc.total++;
        if (be.hasRenderer()) tc.withRenderer++;
        if (be.ticking()) tc.ticking++;
        if (outOfRange) tc.culled++;

        byChunk.computeIfAbsent(be.chunkKey(), key -> new int[1])[0]++;
    }

    public void recordFrameNanos(long nanos) {
        history[historyIndex] = nanos;
        historyIndex = (historyIndex + 1) % history.length;
    }

    /** Moyenne glissante : une valeur instantanée est trop bruitée pour être lue à l'écran. */
    public double averageMicros() {
        long sum = 0;
        int count = 0;
        for (long value : history) {
            if (value > 0) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0 : sum / (double) count / 1000.0;
    }

    public List<TypeCount> topTypes(int limit) {
        sortedScratch.clear();
        for (TypeCount t : byType.values()) {
            if (t.total > 0) sortedScratch.add(t);
        }
        sortedScratch.sort(Comparator.comparingInt((TypeCount t) -> -t.total));
        return sortedScratch.subList(0, Math.min(limit, sortedScratch.size()));
    }

    /**
     * Chunks dépassant le seuil.
     *
     * <p>C'est la mesure qui répond à « quel chunk fait chuter le framerate » : une ferme à
     * hoppers concentre des centaines de BlockEntity dans deux ou trois chunks, et la moyenne
     * globale ne le montre jamais.
     */
    public List<long[]> hotspotChunks(int threshold, int limit) {
        List<long[]> out = new ArrayList<>();
        byChunk.forEach((key, count) -> {
            if (count[0] >= threshold) out.add(new long[]{key, count[0]});
        });
        out.sort(Comparator.comparingLong((long[] entry) -> -entry[1]));
        return out.subList(0, Math.min(limit, out.size()));
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    /** Purge les entrées à zéro : sans elle, les maps grossissent à mesure qu'on explore. */
    public void compact() {
        byType.values().removeIf(t -> t.total == 0);
        byChunk.entrySet().removeIf(entry -> entry.getValue()[0] == 0);
    }
}
