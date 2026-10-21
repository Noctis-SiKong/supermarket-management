package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;
import java.util.Optional;

/**
 * 质量同款同数量零差换货的配对校验（CR07-01/AC-58/82）。
 *
 * <p>校验通过的必要条件：qualityProblem=true、同业务类型换货占用、
 * qualityRedemptionId 指向本次核销、原/替换品条码相同、数量相同、
 * 替换品金额与原商品抵扣金额差额为 0。</p>
 */
public final class QualityPairValidator {

    public Optional<String> validate(
            OriginalSaleLine line, Redemption redemption, Replacement replacement) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(redemption, "redemption");
        Objects.requireNonNull(replacement, "replacement");

        if (!redemption.qualityProblem()) {
            return Optional.of("质量零差配对要求 qualityProblem=true");
        }
        if (redemption.kind() != RedemptionKind.EXCHANGE || redemption.effect() != Effect.CONSUME) {
            return Optional.of("质量零差配对必须是换货占用核销");
        }
        if (!redemption.redemptionId().equals(replacement.qualityRedemptionId())) {
            return Optional.of("qualityRedemptionId 必须指向本次换货核销");
        }
        if (!line.barcode().equals(replacement.barcode())) {
            return Optional.of("原商品与替换品条码必须相同");
        }
        if (redemption.quantity() != replacement.quantity()) {
            return Optional.of("原商品与替换品数量必须相同");
        }
        if (replacement.replacementAmount().compareTo(redemption.amount()) != 0) {
            return Optional.of("质量零差配对要求补退差为 0");
        }
        return Optional.empty();
    }
}
