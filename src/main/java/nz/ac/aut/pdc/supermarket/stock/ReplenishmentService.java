package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/**
 * 补货用例服务（WP-03）：组装 W−q / S+q 双腿变动，调用 IF-07 核对，返回候选。
 *
 * <p>本骨架不提交：真实 ChangeSet 还包含单据头/行等 A 公共类型；待 A 的
 * `shared/` 交付后由协调者统一经 IF-05 提交。</p>
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
