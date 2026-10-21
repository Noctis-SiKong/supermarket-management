package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Build quota from original snapshots and effective CONSUME minus corresponding RELEASE; no uncommitted inputs or cross-line reallocation. Invalid negative remainder fails explicitly.
 *
 *
 * 由"原行快照 + 有效核销历史"构造额度视图。
 *
 * <p>只汇总 CONSUME，并减去对应的 RELEASE；不读取当前未提交明细，
 * 不跨原销售行重分摊。历史非法（出现负剩余）时明确失败，不静默修正。</p>
 */
public final class QuotaViewBuilder {

    public QuotaView build(OriginalSaleLine line, List<Redemption> history) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(history, "history");

        int returnedQty = 0;
        BigDecimal returnedAmount = BigDecimal.ZERO;
        int exchangedQty = 0;
        BigDecimal exchangedAmount = BigDecimal.ZERO;

        for (Redemption redemption : history) {
            if (!line.saleLineId().equals(redemption.originalSaleLineId())) {
                continue;
            }
            int direction = redemption.effect() == Effect.CONSUME ? 1 : -1;
            int quantity = direction * redemption.quantity();
            BigDecimal amount = redemption.amount().multiply(BigDecimal.valueOf(direction));
            if (redemption.kind() == RedemptionKind.RETURN) {
                returnedQty = Math.addExact(returnedQty,quantity);
                returnedAmount = returnedAmount.add(amount);
            } else {
                exchangedQty = Math.addExact(exchangedQty,quantity);
                exchangedAmount = exchangedAmount.add(amount);
            }
        }

        int remainingQty = Math.subtractExact(Math.subtractExact(line.quantity(),returnedQty),exchangedQty);
        BigDecimal remainingAmount = line.paidAmount().subtract(returnedAmount).subtract(exchangedAmount);
        if (returnedQty < 0 || exchangedQty < 0 || remainingQty < 0
                || returnedAmount.signum() < 0 || exchangedAmount.signum() < 0
                || remainingAmount.signum() < 0) {
            throw new IllegalStateException("核销历史使额度出现负数，拒绝构造额度视图");
        }
        return new QuotaView(
                line.saleLineId(),
                line.quantity(),
                line.paidAmount(),
                returnedQty,
                returnedAmount,
                exchangedQty,
                exchangedAmount,
                remainingQty,
                remainingAmount);
    }
}
