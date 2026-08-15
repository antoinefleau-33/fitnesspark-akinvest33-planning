package dev.poc.modules.bediag;

import dev.poc.api.module.GameBridge.BlockEntitySnapshot;
import dev.poc.api.render.WorldRenderer.DepthMode;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Vérifie la logique de diagnostic sur un monde synthétique, sans Minecraft ni contexte OpenGL.
 *
 * <pre>
 *   java -cp out dev.poc.modules.bediag.DiagnosticsDemo
 * </pre>
 */
public final class DiagnosticsDemo {

    public static void main(String[] args) {
        List<BlockEntitySnapshot> world = syntheticWorld();

        section("Monde synthétique");
        System.out.printf("  %d BlockEntity réparties sur %d chunks%n",
                world.size(), world.stream().map(BlockEntitySnapshot::chunkKey).distinct().count());

        section("Filtres");
        for (BeFilter f : BeFilter.CYCLE) {
            long n = world.stream().filter(f::test).count();
            System.out.printf("  %-24s %5d%n", f.label(), n);
        }
        BeFilter conteneurs = BeFilter.ofTypes(
                "minecraft:chest", "minecraft:shulker_box", "minecraft:ender_chest");
        System.out.printf("  %-24s %5d%n", conteneurs.label(),
                world.stream().filter(conteneurs::test).count());
        System.out.println("  → le filtre conteneurs rate les hoppers, qui dominent le coût "
                + "de tick ; c'est WITH_RENDERER qui répond à la question de rendu");

        section("Agrégation");
        BeStats stats = new BeStats();
        BeFilter filter = BeFilter.WITH_RENDERER;

        // Échauffement avant mesure : la première passe est dominée par la compilation JIT et
        // donne un chiffre 20 à 50 fois trop élevé. Un outil de perf qui publie sa mesure à froid
        // est exactement le genre de faux signal qu'on cherche à éliminer.
        for (int i = 0; i < 200; i++) {
            stats.reset();
            for (BlockEntitySnapshot be : world) stats.record(be, filter.test(be));
        }

        long best = Long.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            stats.reset();
            long t0 = System.nanoTime();
            for (BlockEntitySnapshot be : world) stats.record(be, filter.test(be));
            long dt = System.nanoTime() - t0;
            best = Math.min(best, dt);
            stats.endCollect(dt);
        }

        System.out.printf("  chargées %d | retenues %d | renderer %d | ticking %d | hors portée %d%n",
                stats.total, stats.matched, stats.withRenderer, stats.ticking, stats.culled);
        System.out.printf("  coût par frame : %.0f µs (meilleur) / %.0f µs (moyenne 60 frames)%n",
                best / 1000.0, stats.averageCollectMicros());
        System.out.printf("  soit %.1f %% d'une frame à 60 fps%n", best / 1000.0 / 16666.0 * 100);

        System.out.println("\n  types les plus nombreux :");
        for (BeStats.TypeCount t : stats.topTypes(5)) {
            System.out.printf("    %-26s %4d  (renderer %3d, ticking %3d)%n",
                    t.typeId, t.total, t.withRenderer, t.ticking);
        }

        section("Détection de chunks saturés");
        var hotspots = stats.hotspotChunks(24, 5);
        if (hotspots.isEmpty()) {
            System.out.println("  aucun chunk au-dessus du seuil");
        }
        for (long[] h : hotspots) {
            System.out.printf("    chunk (%d, %d) → %d BlockEntity retenues%n",
                    BeStats.chunkX(h[0]), BeStats.chunkZ(h[0]), h[1]);
        }
        System.out.println("  → c'est cette vue qui répond à « quel chunk fait chuter le "
                + "framerate » ; la moyenne globale ne le montre jamais");

        section("Politique d'occlusion");
        for (DepthMode requested : DepthMode.values()) {
            System.out.printf("  demandé %-16s → solo: %-16s  multijoueur: %s%n",
                    requested,
                    DepthMode.resolve(requested, true),
                    DepthMode.resolve(requested, false));
        }
    }

    /**
     * Monde jouet : distribution réaliste, avec deux chunks volontairement saturés pour simuler
     * une ferme à hoppers et un système de tri.
     */
    private static List<BlockEntitySnapshot> syntheticWorld() {
        // Graine fixe : deux exécutions doivent donner exactement les mêmes chiffres, sinon la
        // sortie n'est pas comparable d'un run à l'autre.
        RandomGenerator rng = java.util.random.RandomGeneratorFactory
                .of("Xoshiro256PlusPlus").create(42);

        record Type(String id, boolean renderer, boolean ticking, int weight) {}
        List<Type> types = List.of(
                new Type("minecraft:chest", true, false, 220),
                new Type("minecraft:hopper", false, true, 180),
                new Type("minecraft:furnace", false, true, 90),
                new Type("minecraft:sign", true, false, 140),
                new Type("minecraft:shulker_box", true, false, 40),
                new Type("minecraft:ender_chest", true, false, 12),
                new Type("minecraft:banner", true, false, 30),
                new Type("minecraft:bed", true, false, 25),
                new Type("minecraft:barrel", false, false, 60),
                new Type("minecraft:brewing_stand", false, true, 15));
        int totalWeight = types.stream().mapToInt(Type::weight).sum();

        List<BlockEntitySnapshot> out = new ArrayList<>(4096);
        for (int i = 0; i < 4000; i++) {
            int roll = rng.nextInt(totalWeight);
            Type type = types.get(0);
            for (Type t : types) {
                roll -= t.weight();
                if (roll < 0) { type = t; break; }
            }

            int chunkX, chunkZ;
            if (i % 7 == 0) {            // concentration artificielle sur deux chunks
                chunkX = (i % 14 == 0) ? 3 : -5;
                chunkZ = (i % 14 == 0) ? -2 : 8;
            } else {
                chunkX = rng.nextInt(-24, 25);
                chunkZ = rng.nextInt(-24, 25);
            }

            int x = chunkX * 16 + rng.nextInt(16);
            int z = chunkZ * 16 + rng.nextInt(16);
            int y = rng.nextInt(-40, 120);
            double distSq = (double) x * x + (double) y * y + (double) z * z;

            // 8 % des BE avec renderer sont hors distance de rendu.
            boolean inRange = !type.renderer() || rng.nextInt(100) >= 8;

            out.add(new BlockEntitySnapshot(x, y, z, type.id(),
                    type.renderer(), type.ticking(), inRange, chunkX, chunkZ, distSq));
        }
        return out;
    }

    private static void section(String title) {
        System.out.println("\n" + "=".repeat(72));
        System.out.println(title);
        System.out.println("=".repeat(72));
    }
}
