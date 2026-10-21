package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;

/** ERD replacement line: ordinary exchanges use redemptionId; quality zero-difference exchanges additionally pair one-to-one through qualityRedemptionId.
 */
public record Replacement(
        String lineId,
        String aftersaleId,
        String redemptionId,
        String barcode,
        int quantity,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        BigDecimal replacementAmount,
        String exchangeKind,
        String qualityRedemptionId) {

    public Replacement {
        if (lineId == null || lineId.isBlank()) {
            throw new IllegalArgumentException("lineId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (replacementAmount == null) {
            throw new IllegalArgumentException("replacementAmount must not be null");
        }
        if (replacementAmount.signum() < 0) {
            throw new IllegalArgumentException("replacementAmount must not be negative");
        }
    }
}
