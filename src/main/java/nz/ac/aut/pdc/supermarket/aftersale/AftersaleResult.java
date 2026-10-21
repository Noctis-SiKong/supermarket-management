package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.stock.Position;

/** 售后编排的候选结果（尚未提交）。 */
public sealed interface AftersaleResult
        permits AftersaleResult.Prepared, AftersaleResult.Rejected, AftersaleResult.Failed {

    record Prepared(
            List<Redemption> redemptions,
            List<Replacement> replacements,
            List<StockEntryView> stockEntries) implements AftersaleResult {
        public Prepared {
            redemptions = List.copyOf(Objects.requireNonNull(redemptions, "redemptions"));
            replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));
            stockEntries = List.copyOf(Objects.requireNonNull(stockEntries, "stockEntries"));
        }
    }

    record Rejected(String code, String field, String message) implements AftersaleResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record Failed(String code, String message) implements AftersaleResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record StockEntryView(
            String id,
            String barcode,
            Position position,
            int delta,
            String eventKey,
            String transferKey) {
        public StockEntryView {
            Objects.requireNonNull(position, "position");
        }
    }
}
