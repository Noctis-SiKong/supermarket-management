package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/** Uncommitted settlement: redemption ID, optional replacement ID, direction and nonnegative amount; NONE requires zero.
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
