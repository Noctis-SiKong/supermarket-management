package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/** Pure settlement calculation: refund returns, refund/charge exchange differences, NONE for quality zero-difference. Produces candidates only; coordinator creates money facts and submits.
 */
public final class SettlementCalculator {

    public Settlement calculate(Redemption redemption, Replacement replacement) {
        Objects.requireNonNull(redemption, "redemption");
        if(replacement!=null && replacement.qualityRedemptionId()!=null)
            return new Settlement(redemption.redemptionId(),replacement.lineId(),SettlementDirection.NONE,BigDecimal.ZERO.setScale(2));
        String replacementId = replacement == null ? null : replacement.lineId();
        BigDecimal difference = redemption.amount().subtract(
                replacement == null ? BigDecimal.ZERO : replacement.replacementAmount());
        if (difference.signum() > 0) {
            return new Settlement(
                    redemption.redemptionId(), replacementId, SettlementDirection.REFUND, difference);
        }
        if (difference.signum() < 0) {
            return new Settlement(
                    redemption.redemptionId(), replacementId, SettlementDirection.CHARGE, difference.abs());
        }
        return new Settlement(
                redemption.redemptionId(), replacementId, SettlementDirection.NONE, BigDecimal.ZERO);
    }
}
