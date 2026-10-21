package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 原销售行的一次核销明细（ERD REDEMPTION 的 B 侧不可变模型）。
 *
 * <p>业务类型与账务方向分离：kind=RETURN/EXCHANGE，effect=CONSUME/RELEASE。
 * RELEASE 必须通过 reverseOfId 指向被释放的原核销。资格例外（CR07-09）事实
 * 写在例外字段中，便于审计；未使用例外时为空。</p>
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
        boolean qualityProblem,
        String exceptionAuthorizedBy,
        String exceptionReason,
        String relaxedRule) {

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

    /** 兼容旧调用：无资格例外事实。 */
    public Redemption(
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
        this(redemptionId, sourceLineId, originalSaleLineId, originalBarcode, aftersaleId,
                kind, effect, quantity, amount, requestKey, reverseOfId, opened, qualityProblem,
                null, null, null);
    }
}
