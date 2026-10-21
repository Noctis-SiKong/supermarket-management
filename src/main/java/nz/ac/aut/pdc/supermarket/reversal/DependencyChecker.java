package nz.ac.aut.pdc.supermarket.reversal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure D1 traversal from a redemption: reachable downstream references block direct reversal; cycles indicate invalid data. No reads/commits; used by the historical ReversalService and tests.
 *
 *
 * D1 依赖图分析（纯函数）：从原核销出发找出所有后续引用，并检测环。
 *
 * <p>只要存在可达的后续引用，就应阻断直接冲销；环提示数据/引用关系异常。
 * 本类不读取数据、不提交，供 ReversalService 与测试使用。</p>
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
 *
 * 依赖分析结果；blockingIds 为不可直接冲销时会受影响的后续记录。 */
    public record DependencyDecision(
            boolean blocked, List<String> blockingIds, boolean cycleDetected) {
        public DependencyDecision {
            blockingIds = List.copyOf(Objects.requireNonNull(blockingIds, "blockingIds"));
        }
    }
}
