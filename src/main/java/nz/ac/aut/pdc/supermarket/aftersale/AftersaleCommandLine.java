package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;

/** 售后单中的一条明细（退货或换货）。 */
public record AftersaleCommandLine(
        String saleLineId,
        RedemptionKind kind,
        int quantity,
        boolean opened,
        boolean qualityProblem,
        String replacementBarcode,
        int replacementQty,
        boolean qualityPair) {

    public AftersaleCommandLine {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
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
