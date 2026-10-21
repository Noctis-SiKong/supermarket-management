package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.stock.StockEntry;

/**
 * 冲销计划：释放原核销的 RELEASE 事实 + 反向库存候选流水。
 *
 * <p>仅计划，未提交；D1 依赖检查通过后才允许生成。</p>
 */
public record ReversalPlan(
        String originalRedemptionId,
        Redemption releaseRedemption,
        List<StockEntry> stockEntries) {

    public ReversalPlan {
        if (originalRedemptionId == null || originalRedemptionId.isBlank()) {
            throw new IllegalArgumentException("originalRedemptionId must not be blank");
        }
        Objects.requireNonNull(releaseRedemption, "releaseRedemption");
        stockEntries = List.copyOf(Objects.requireNonNull(stockEntries, "stockEntries"));
    }
}
