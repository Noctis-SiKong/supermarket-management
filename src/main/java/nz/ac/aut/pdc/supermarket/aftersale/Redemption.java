package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 原销售行的一次核销明细（ERD REDEMPTION 的 B 侧不可变模型）。
 *
 * <p>业务类型与账务方向分离：kind=RETURN/EXCHANGE，effect=CONSUME/RELEASE。
 * RELEASE 必须通过 reverseOfId 指向被释放的原核销。</p>
 */
public record Redemption(
        String redemptionId,
        String sourceLineId,
        String originalSaleLineId,
        String originalBarcode,
        String aftersaleId,
        RedemptionKind kind,
        Effect effect,
        int quantity,
        BigDecimal amount,
        String requestKey,
        String reverseOfId,
        boolean opened,
        boolean qualityProblem) {

    public Redemption {
        if (redemptionId == null || redemptionId.isBlank()) {
            throw new IllegalArgumentException("redemptionId must not be blank");
        }
        if (originalSaleLineId == null || originalSaleLineId.isBlank()) {
            throw new IllegalArgumentException("originalSaleLineId must not be blank");
        }
        if (originalBarcode == null || originalBarcode.isBlank()) {
            throw new IllegalArgumentException("originalBarcode must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(effect, "effect");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (effect == Effect.RELEASE && (reverseOfId == null || reverseOfId.isBlank())) {
            throw new IllegalArgumentException("RELEASE must reference the original redemption");
        }
    }
}
