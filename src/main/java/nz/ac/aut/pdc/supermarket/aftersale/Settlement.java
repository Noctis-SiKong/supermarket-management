package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 售后结算候选：退款/补收金额与方向。
 *
 * @param redemptionId 本次核销 ID
 * @param replacementId 换货替换行 ID（纯退货为空）
 * @param direction 方向
 * @param amount 金额（非负；NONE 为 0）
 */
public record Settlement(
        String redemptionId,
        String replacementId,
        SettlementDirection direction,
        BigDecimal amount) {

    public Settlement {
        if (redemptionId == null || redemptionId.isBlank()) {
            throw new IllegalArgumentException("redemptionId must not be blank");
        }
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (direction == SettlementDirection.NONE && amount.signum() != 0) {
            throw new IllegalArgumentException("NONE settlement must have zero amount");
        }
    }
}
