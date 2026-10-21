package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;
import java.util.Optional;

/** CR07-01/AC-58/82 quality pairing requires qualityProblem, EXCHANGE consumption, current redemption reference, identical barcode/quantity and zero replacement-versus-credit difference.
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
        if (new SettlementCalculator().calculate(redemption,replacement).direction()!=SettlementDirection.NONE) {
            return Optional.of("质量零差配对要求补退差为 0");
        }
        return Optional.empty();
    }
}
