package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** 补货用例的候选结果（尚未提交；不分配 commitOrdinal）。 */
public sealed interface ReplenishmentResult
        permits ReplenishmentResult.Prepared, ReplenishmentResult.Rejected, ReplenishmentResult.Failed {

    record Prepared(String transferKey, List<StockEntry> entries, List<StockBalance> balancesAfter)
            implements ReplenishmentResult {
        public Prepared {
            if (transferKey == null || transferKey.isBlank()) {
                throw new IllegalArgumentException("transferKey must not be blank");
            }
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            balancesAfter = List.copyOf(Objects.requireNonNull(balancesAfter, "balancesAfter"));
        }
    }

    record Rejected(String code, String field, String message) implements ReplenishmentResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record Failed(String code, String message) implements ReplenishmentResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
