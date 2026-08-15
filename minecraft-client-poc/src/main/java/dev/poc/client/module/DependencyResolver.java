package dev.poc.client.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a bag of discovered manifests into a load order, or an explanation of why a module cannot
 * be loaded.
 *
 * <p>Rejection is transitive and reported, never silent: a module that depends on something the
 * resolver threw out is itself thrown out with a reason the UI can display. Cycles are reported as
 * a cycle rather than as a stack overflow at class-load time.
 */
public final class DependencyResolver {

    public record Result(List<ModuleDescriptor> loadOrder, Map<String, String> rejected) {
    }

    private DependencyResolver() {
    }

    public static Result resolve(Collection<ModuleDescriptor> discovered) {
        Map<String, ModuleDescriptor> byId = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();

        for (ModuleDescriptor descriptor : discovered) {
            ModuleDescriptor previous = byId.put(descriptor.id(), descriptor);
            if (previous != null) {
                rejected.put(descriptor.id(), "duplicate module id (versions "
                        + previous.version() + " and " + descriptor.version() + ")");
            }
        }
        rejected.keySet().forEach(byId::remove);

        for (ModuleDescriptor descriptor : List.copyOf(byId.values())) {
            if (descriptor.apiVersion() > ModuleDescriptor.CURRENT_API_VERSION) {
                byId.remove(descriptor.id());
                rejected.put(descriptor.id(), "needs API v" + descriptor.apiVersion()
                        + ", client provides v" + ModuleDescriptor.CURRENT_API_VERSION);
            }
        }

        pruneUnsatisfiable(byId, rejected);
        pruneCycles(byId, rejected);

        return new Result(topologicalOrder(byId), Map.copyOf(rejected));
    }

    /** Repeatedly drops modules whose hard dependencies are absent, until nothing changes. */
    private static void pruneUnsatisfiable(Map<String, ModuleDescriptor> byId,
                                           Map<String, String> rejected) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ModuleDescriptor descriptor : List.copyOf(byId.values())) {
                for (String dependency : descriptor.depends()) {
                    if (!byId.containsKey(dependency)) {
                        byId.remove(descriptor.id());
                        rejected.put(descriptor.id(), rejected.containsKey(dependency)
                                ? "dependency '" + dependency + "' was rejected"
                                : "missing dependency '" + dependency + "'");
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    /** Finds one cycle at a time and rejects its members, until the graph is acyclic. */
    private static void pruneCycles(Map<String, ModuleDescriptor> byId, Map<String, String> rejected) {
        List<String> cycle;
        while ((cycle = findCycle(byId)) != null) {
            String description = String.join(" -> ", cycle);
            for (String id : cycle) {
                byId.remove(id);
                rejected.put(id, "dependency cycle: " + description);
            }
            pruneUnsatisfiable(byId, rejected);
        }
    }

    private static List<String> findCycle(Map<String, ModuleDescriptor> byId) {
        Map<String, Integer> state = new HashMap<>(); // 0 = unvisited, 1 = on stack, 2 = done
        Deque<String> stack = new ArrayDeque<>();
        for (String id : byId.keySet()) {
            List<String> cycle = visitForCycle(id, byId, state, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static List<String> visitForCycle(String id, Map<String, ModuleDescriptor> byId,
                                              Map<String, Integer> state, Deque<String> stack) {
        int current = state.getOrDefault(id, 0);
        if (current == 2) {
            return null;
        }
        if (current == 1) {
            List<String> cycle = new ArrayList<>();
            for (String onStack : stack) {
                cycle.add(0, onStack);
                if (onStack.equals(id)) {
                    break;
                }
            }
            cycle.add(id);
            return cycle;
        }
        state.put(id, 1);
        stack.push(id);
        for (String dependency : edgesOf(id, byId)) {
            List<String> cycle = visitForCycle(dependency, byId, state, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        stack.pop();
        state.put(id, 2);
        return null;
    }

    private static List<ModuleDescriptor> topologicalOrder(Map<String, ModuleDescriptor> byId) {
        Set<String> visited = new LinkedHashSet<>();
        List<ModuleDescriptor> order = new ArrayList<>();
        for (String id : byId.keySet()) {
            visitForOrder(id, byId, visited, order);
        }
        return List.copyOf(order);
    }

    private static void visitForOrder(String id, Map<String, ModuleDescriptor> byId,
                                      Set<String> visited, List<ModuleDescriptor> order) {
        if (!visited.add(id)) {
            return;
        }
        for (String dependency : edgesOf(id, byId)) {
            visitForOrder(dependency, byId, visited, order);
        }
        order.add(byId.get(id));
    }

    /** Hard deps plus soft deps that actually resolved — soft deps only constrain ordering. */
    private static List<String> edgesOf(String id, Map<String, ModuleDescriptor> byId) {
        ModuleDescriptor descriptor = byId.get(id);
        if (descriptor == null) {
            return List.of();
        }
        List<String> edges = new ArrayList<>(descriptor.depends());
        for (String soft : descriptor.softDepends()) {
            if (byId.containsKey(soft)) {
                edges.add(soft);
            }
        }
        return edges;
    }
}
