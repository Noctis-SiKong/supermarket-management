package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DependencyCheckerTest {

    private final DependencyChecker checker = new DependencyChecker();

    @Test
    public void directDependentBlocksReversal() {
        DependencyChecker.DependencyDecision decision =
                checker.analyze("R-1", Map.of("R-1", List.of("AF-2")));

        assertTrue(decision.blocked());
        assertEquals(List.of("AF-2"), decision.blockingIds());
        assertFalse(decision.cycleDetected());
    }

    @Test
    public void transitiveDependentBlocksReversal() {
        DependencyChecker.DependencyDecision decision = checker.analyze(
                "R-1", Map.of("R-1", List.of("AF-2"), "AF-2", List.of("AF-3")));

        assertTrue(decision.blocked());
        assertEquals(List.of("AF-2", "AF-3"), decision.blockingIds());
    }

    @Test
    public void noDependentsIsNotBlocked() {
        DependencyChecker.DependencyDecision decision =
                checker.analyze("R-1", Map.of("R-1", List.of()));

        assertFalse(decision.blocked());
        assertTrue(decision.blockingIds().isEmpty());
    }

    @Test
    public void cycleIsDetected() {
        DependencyChecker.DependencyDecision decision = checker.analyze(
                "R-1", Map.of("R-1", List.of("AF-2"), "AF-2", List.of("R-1")));

        assertTrue(decision.blocked());
        assertTrue(decision.cycleDetected());
    }
}
