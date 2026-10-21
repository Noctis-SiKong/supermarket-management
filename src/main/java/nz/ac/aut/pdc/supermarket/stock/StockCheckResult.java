package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** IF-07 PREPARED/REJECTED/FAILED: PREPARED means candidates, never committed success or allocated commitOrdinal.
 */
public sealed interface StockCheckResult
        permits StockCheckResult.Prepared, StockCheckResult.Rejected, StockCheckResult.Failed {

    /** Candidate entries and resulting balances for coordinator overlay and later submission.
  */
    record Prepared(List<StockEntry> entries, List<StockBalance> balancesAfter) implements StockCheckResult {
        public Prepared {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            balancesAfter = List.copyOf(Objects.requireNonNull(balancesAfter, "balancesAfter"));
        }
    }

    /** Business rejection: insufficient stock, invalid quantity, unbalanced transfer or invalid reference.
  */
    record Rejected(String code, String field, String message) implements StockCheckResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Internal/dependency technical failure, distinct from business rejection.
  */
    record Failed(String code, String message) implements StockCheckResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
