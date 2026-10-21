package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;

/** One return/exchange line: QUALITY requires qualityProblem; OTHER requires reasonText. Reasons never relax time, opened-item, quota, money or stock rules.
 */
public record AftersaleCommandLine(
        String saleLineId,
        RedemptionKind kind,
        int quantity,
        boolean opened,
        boolean qualityProblem,
        AftersaleReasonKind reasonKind,
        String reasonText,
        String replacementBarcode,
        int replacementQty,
        boolean qualityPair) {

    public AftersaleCommandLine {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reasonKind, "reasonKind");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (reasonKind == AftersaleReasonKind.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("OTHER reason requires reasonText");
        }
        if (reasonKind == AftersaleReasonKind.QUALITY && !qualityProblem) {
            throw new IllegalArgumentException("QUALITY reason requires qualityProblem=true");
        }
        if (kind == RedemptionKind.EXCHANGE) {
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                throw new IllegalArgumentException("exchange requires replacementBarcode");
            }
            if (replacementQty <= 0) {
                throw new IllegalArgumentException("exchange requires positive replacementQty");
            }
        }
    }
}
