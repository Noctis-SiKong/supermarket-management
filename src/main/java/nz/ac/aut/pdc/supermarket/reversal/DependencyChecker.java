package nz.ac.aut.pdc.supermarket.reversal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure D1 traversal from a redemption: reachable downstream references block direct reversal; cycles indicate invalid data. No reads/commits; used by the historical ReversalService and tests.
 */
public final class DependencyChecker {

    public DependencyDecision analyze(
            String originalId, Map<String, List<String>> graph) {
        if (originalId == null || originalId.isBlank()) {
            throw new IllegalArgumentException("originalId must not be blank");
        }
        Objects.requireNonNull(graph, "graph");

        Set<String> visited = new HashSet<>();
        List<String> dependents = new ArrayList<>();
        boolean cycle = false;
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(originalId);
        visited.add(originalId);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : graph.getOrDefault(current, List.of())) {
                if (next == null || next.isBlank()) {
                    continue;
                }
                if (next.equals(originalId)) {
                    cycle = true;
                    continue;
                }
                if (!visited.add(next)) {
                    continue;
                }
                dependents.add(next);
                if (hasPathBackToStart(next, originalId, graph, new HashSet<>())) {
                    cycle = true;
                }
                queue.addLast(next);
            }
        }
        return new DependencyDecision(!dependents.isEmpty(), List.copyOf(dependents), cycle);
    }

    private boolean hasPathBackToStart(
            String current, String originalId,
            Map<String, List<String>> graph, Set<String> seen) {
        if (!seen.add(current)) {
            return false;
        }
        for (String next : graph.getOrDefault(current, List.of())) {
            if (next.equals(originalId)) {
                return true;
            }
        }
        for (String next : graph.getOrDefault(current, List.of())) {
            if (hasPathBackToStart(next, originalId, graph, seen)) {
                return true;
            }
        }
        return false;
    }

    /** Dependency outcome; blockingIds identifies affected downstream records when direct reversal is blocked.
  */
    public record DependencyDecision(
            boolean blocked, List<String> blockingIds, boolean cycleDetected) {
        public DependencyDecision {
            blockingIds = List.copyOf(Objects.requireNonNull(blockingIds, "blockingIds"));
        }
    }
}
