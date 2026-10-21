package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 售后结算纯计算：比较核销抵扣金额与替换品金额。
 *
 * <p>纯退货退核销金额；换货按差额退款/补收；质量同款零差为 NONE。
 * 不直接创建款项记录，只产生结算候选供协调者接入 A 的款项/提交边界。</p>
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
