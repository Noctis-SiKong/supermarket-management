package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** WP-03 store loss deducts source stock once and prepares loss/stock facts. Customer returns have no stock position or current-stock deduction here; no submission.
 *
 *
 * 店内报损用例服务（WP-03）：按来源位置扣减一次，生成 LossRecord 与候选流水。
 *
 * <p>顾客交回品不在此处理（无库存位置、不得扣减现货）。本骨架不提交。</p>
 */
public final class LossService {

    private final StockOperations stockOperations;
    private final StockPorts.IdGenerator idGenerator;

    public LossService(StockOperations stockOperations, StockPorts.IdGenerator idGenerator) {
        this.stockOperations = Objects.requireNonNull(stockOperations, "stockOperations");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public LossResult recordLoss(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            String barcode,
            Position position,
            int quantity) {
        if (barcode == null || barcode.isBlank()) {
            return new LossResult.Rejected("INVALID_INPUT", "barcode", "商品条码不能为空");
        }
        if (position == null) {
            return new LossResult.Rejected("INVALID_INPUT", "position", "报损必须指定来源位置");
        }
        if (quantity < StockValidator.MIN_QUANTITY || quantity > StockValidator.MAX_QUANTITY) {
            return new LossResult.Rejected(
                    "INVALID_INPUT", "quantity", "业务数量必须为 1–9999 的整数");
        }

        StockChangeLine change = new StockChangeLine(barcode, position, -quantity, null, null, null);
        StockCheckResult checked = stockOperations.check(workingCopy, overlayEntries, List.of(change));
        if (checked instanceof StockCheckResult.Rejected rejected) {
            return new LossResult.Rejected(rejected.code(), rejected.field(), rejected.message());
        }
        if (checked instanceof StockCheckResult.Failed failed) {
            return new LossResult.Failed(failed.code(), failed.message());
        }

        StockCheckResult.Prepared prepared = (StockCheckResult.Prepared) checked;
        LossRecord lossRecord = new LossRecord(
                idGenerator.nextId("LOSS"),
                null,
                barcode,
                position,
                quantity,
                LossOrigin.STORE,
                null,
                LossState.PENDING);
        return new LossResult.Prepared(lossRecord, prepared.entries(), prepared.balancesAfter());
    }
}
