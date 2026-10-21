package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** Uncommitted store-loss candidate result.
  */
public sealed interface LossResult
        permits LossResult.Prepared, LossResult.Rejected, LossResult.Failed {

    record Prepared(LossRecord lossRecord, List<StockEntry> entries, List<StockBalance> balancesAfter)
            implements LossResult {
        public Prepared {
            Objects.requireNonNull(lossRecord, "lossRecord");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            balancesAfter = List.copyOf(Objects.requireNonNull(balancesAfter, "balancesAfter"));
        }
    }

    record Rejected(String code, String field, String message) implements LossResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record Failed(String code, String message) implements LossResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
