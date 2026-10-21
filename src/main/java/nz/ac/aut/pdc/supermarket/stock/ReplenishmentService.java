package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** WP-03 candidate replenishment: paired W-minus-q/S-plus-q movements checked through IF-07. No submission; coordinator combines shared document facts and submits through IF-05.
 */
public final class ReplenishmentService {

    private final StockOperations stockOperations;
    private final StockPorts.IdGenerator idGenerator;

    public ReplenishmentService(StockOperations stockOperations, StockPorts.IdGenerator idGenerator) {
        this.stockOperations = Objects.requireNonNull(stockOperations, "stockOperations");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public ReplenishmentResult replenish(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            String barcode,
            int quantity) {
        if (barcode == null || barcode.isBlank()) {
            return new ReplenishmentResult.Rejected("INVALID_INPUT", "barcode", "商品条码不能为空");
        }
        if (quantity < StockValidator.MIN_QUANTITY || quantity > StockValidator.MAX_QUANTITY) {
            return new ReplenishmentResult.Rejected(
                    "INVALID_INPUT", "quantity", "业务数量必须为 1–9999 的整数");
        }

        String transferKey = idGenerator.nextId("TR");
        List<StockChangeLine> changes = List.of(
                new StockChangeLine(barcode, Position.W, -quantity, null, transferKey, null),
                new StockChangeLine(barcode, Position.S, quantity, null, transferKey, null));

        StockCheckResult checked = stockOperations.check(workingCopy, overlayEntries, changes);
        if (checked instanceof StockCheckResult.Prepared prepared) {
            return new ReplenishmentResult.Prepared(
                    transferKey, prepared.entries(), prepared.balancesAfter());
        }
        if (checked instanceof StockCheckResult.Rejected rejected) {
            return new ReplenishmentResult.Rejected(rejected.code(), rejected.field(), rejected.message());
        }
        StockCheckResult.Failed failed = (StockCheckResult.Failed) checked;
        return new ReplenishmentResult.Failed(failed.code(), failed.message());
    }
}
