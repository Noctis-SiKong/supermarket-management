package nz.ac.aut.pdc.supermarket.reversal;

import java.util.Objects;

/** Uncommitted reversal candidate result.
  */
public sealed interface ReversalResult
        permits ReversalResult.Prepared, ReversalResult.Rejected, ReversalResult.Failed {

    record Prepared(ReversalPlan plan) implements ReversalResult {
        public Prepared {
            Objects.requireNonNull(plan, "plan");
        }
    }

    record Rejected(String code, String field, String message) implements ReversalResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record Failed(String code, String message) implements ReversalResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
