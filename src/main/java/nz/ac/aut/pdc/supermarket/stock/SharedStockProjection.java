package nz.ac.aut.pdc.supermarket.stock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** Read-only stock projection: sourceLineId resolves through DOC_LINE to barcode; aggregate all stock facts without changing WorkingCopy or submitting.
 */
final class SharedStockProjection {

    private SharedStockProjection() {
    }

    static List<StockBalance> balances(WorkingCopy workingCopy, Collection<String> barcodes) {
        Map<StockValidator.BalanceKey, Integer> totals = balanceMap(workingCopy);
        List<StockBalance> result = new ArrayList<>();
        for (Map.Entry<StockValidator.BalanceKey, Integer> entry : totals.entrySet()) {
            if (barcodes.contains(entry.getKey().barcode())) {
                result.add(new StockBalance(
                        entry.getKey().barcode(), entry.getKey().position(), entry.getValue()));
            }
        }
        result.sort(Comparator.comparing(StockBalance::barcode).thenComparing(StockBalance::position));
        return result;
    }

    static Map<StockValidator.BalanceKey, Integer> balanceMap(WorkingCopy workingCopy) {
        Map<StockValidator.BalanceKey, Integer> totals = new HashMap<>();
        for (var record : workingCopy.records("STOCK_ENTRY")) {
            StockEntry entry = record.as(StockEntry.class);
            String barcode = barcode(workingCopy, entry.sourceLineId());
            totals.merge(
                    new StockValidator.BalanceKey(barcode, position(entry.position())),
                    entry.quantityDelta(),
                    Math::addExact);
        }
        for (Map.Entry<StockValidator.BalanceKey, Integer> entry : totals.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("negative stock balance for "
                        + entry.getKey().barcode() + ":" + entry.getKey().position());
            }
        }
        return totals;
    }

    static String barcode(WorkingCopy workingCopy, String sourceLineId) {
        var line = workingCopy.find("DOC_LINE", sourceLineId)
                .orElseThrow(() -> new IllegalArgumentException("REFERENCE_INVALID: DOC_LINE " + sourceLineId));
        return line.as(DocumentLine.class).barcode();
    }

    static Position position(StockEntry.Position position) {
        return switch (position) {
            case S -> Position.S;
            case W -> Position.W;
        };
    }
}
