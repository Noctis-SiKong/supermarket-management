package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 售后额度计算（纯函数）。
 *
 * <p>未结清：{@code HALF_UP(P × q ÷ Q, 2)}，并受剩余金额上限约束；
 * 结清：本次数量必须等于剩余数量，直接使用全部剩余金额（AC-46/79/80）。
 * 不跨原销售行重分摊，不读取当前未提交明细。</p>
 */
public final class QuotaCalculator {

    public BigDecimal allocate(int quantity, QuotaView view, SettlementContext settlement) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(settlement, "settlement");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (quantity > view.remainingQty()) {
            throw new IllegalArgumentException("quantity exceeds remaining quota");
        }
        if (settlement.settlesRemaining()) {
            if (quantity != view.remainingQty()) {
                throw new IllegalArgumentException("settle flag requires the full remaining quantity");
            }
            return view.remainingAmount();
        }
        if (view.originalQty() <= 0) {
            throw new IllegalArgumentException("original quantity must be positive");
        }
        BigDecimal proportional = view.originalPaid()
                .multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(view.originalQty()), 2, RoundingMode.HALF_UP);
        return proportional.min(view.remainingAmount());
    }
}
