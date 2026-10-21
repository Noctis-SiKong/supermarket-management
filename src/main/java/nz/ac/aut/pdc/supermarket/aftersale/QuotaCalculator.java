package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Pure quota calculation: HALF_UP(P * q / Q, 2) capped by remaining money; final settlement uses all remaining quantity/money (AC-46/79/80). No cross-line reallocation or uncommitted inputs.
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
