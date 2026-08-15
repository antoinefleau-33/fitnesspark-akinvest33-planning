package dev.poc.core.module;

import dev.poc.api.module.ModuleMetadata;
import dev.poc.core.util.SemVer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Résolution des dépendances : validation des contraintes, détection des conflits, puis tri
 * topologique.
 *
 * <p>Le tri utilise un DFS avec détection de cycle plutôt qu'un Kahn : quand un cycle existe, le
 * DFS peut en <b>restituer le chemin exact</b> ({@code a → b → c → a}), ce qui transforme un
 * message inutilisable en diagnostic actionnable. C'est le genre de détail qui décide de
 * l'expérience de dev sur une plateforme à modules.
 */
public final class DependencyResolver {

    public record Result(List<ModuleDiscovery.Candidate> ordered, List<String> warnings) {}

    public static final class ResolutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ResolutionException(String message) { super(message); }
    }

    private DependencyResolver() {}

    public static Result resolve(List<ModuleDiscovery.Candidate> candidates, String gameVersion) {
        List<String> warnings = new ArrayList<>();
        Map<String, ModuleDiscovery.Candidate> byId = new LinkedHashMap<>();

        for (var c : candidates) {
            var prev = byId.put(c.metadata().id(), c);
            if (prev != null) {
                // Deux jars pour le même id : on garde la version la plus haute.
                SemVer a = SemVer.parse(prev.metadata().version());
                SemVer b = SemVer.parse(c.metadata().version());
                var keep = b.compareTo(a) >= 0 ? c : prev;
                byId.put(keep.metadata().id(), keep);
                warnings.add("doublon de module '" + c.metadata().id() + "' : conservé "
                        + keep.metadata().version());
            }
        }

        // Filtrage par version de jeu supportée.
        if (gameVersion != null) {
            SemVer game = SemVer.parse(gameVersion);
            byId.values().removeIf(c -> {
                boolean ok = c.metadata().gameVersions().stream().anyMatch(game::satisfies);
                if (!ok) {
                    warnings.add("module '" + c.metadata().id() + "' incompatible avec "
                            + gameVersion + " (requiert " + c.metadata().gameVersions() + ")");
                }
                return !ok;
            });
        }

        // Dépendances dures et conflits.
        for (var c : byId.values()) {
            ModuleMetadata m = c.metadata();
            m.depends().forEach((depId, range) -> {
                var dep = byId.get(depId);
                if (dep == null) {
                    throw new ResolutionException("'" + m.id() + "' requiert '" + depId
                            + "' (" + range + ") qui est absent");
                }
                SemVer have = SemVer.parse(dep.metadata().version());
                if (!have.satisfies(range)) {
                    throw new ResolutionException("'" + m.id() + "' requiert '" + depId + "' "
                            + range + " mais " + have + " est installé");
                }
            });
            m.conflicts().forEach((badId, range) -> {
                var bad = byId.get(badId);
                if (bad != null && SemVer.parse(bad.metadata().version()).satisfies(range)) {
                    throw new ResolutionException("'" + m.id() + "' est en conflit avec '"
                            + badId + "' " + bad.metadata().version());
                }
            });
            m.suggests().forEach((sugId, range) -> {
                if (!byId.containsKey(sugId)) {
                    warnings.add("'" + m.id() + "' suggère '" + sugId + "' " + range + " (absent)");
                }
            });
        }

        return new Result(topoSort(byId), warnings);
    }

    private static List<ModuleDiscovery.Candidate> topoSort(
            Map<String, ModuleDiscovery.Candidate> byId) {

        List<ModuleDiscovery.Candidate> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Set<String> onStack = new LinkedHashSet<>();
        Map<String, String> parentOf = new HashMap<>();

        for (String id : byId.keySet()) {
            if (!done.contains(id)) visit(id, byId, done, onStack, parentOf, ordered);
        }
        return ordered;
    }

    private static void visit(String id,
                              Map<String, ModuleDiscovery.Candidate> byId,
                              Set<String> done,
                              Set<String> onStack,
                              Map<String, String> parentOf,
                              List<ModuleDiscovery.Candidate> out) {
        if (done.contains(id)) return;
        if (!onStack.add(id)) {
            throw new ResolutionException("cycle de dépendances : " + renderCycle(id, parentOf));
        }
        var candidate = byId.get(id);
        if (candidate != null) {
            for (String depId : candidate.metadata().depends().keySet()) {
                if (byId.containsKey(depId)) {
                    parentOf.put(depId, id);
                    visit(depId, byId, done, onStack, parentOf, out);
                }
            }
            out.add(candidate);
        }
        onStack.remove(id);
        done.add(id);
    }

    /** Reconstitue le chemin du cycle en remontant les parents jusqu'à retomber sur {@code id}. */
    private static String renderCycle(String id, Map<String, String> parentOf) {
        Deque<String> path = new ArrayDeque<>();
        path.addFirst(id);
        String cur = parentOf.get(id);
        int guard = 0;
        while (cur != null && !cur.equals(id) && guard++ < 128) {
            path.addFirst(cur);
            cur = parentOf.get(cur);
        }
        path.addFirst(id);
        return String.join(" → ", path);
    }
}
