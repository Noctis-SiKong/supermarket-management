package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;

/** CUI 售后菜单的一次完整输入，避免把多字段散落在 Operations 参数表。 */
public record AftersaleRequest(
        String saleLineId,
        RedemptionKind kind,
        int quantity,
        boolean opened,
        boolean qualityProblem,
        AftersaleReasonKind reasonKind,
        String reasonText,
        String replacementBarcode,
        int replacementQuantity) {

    public AftersaleRequest {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reasonKind, "reasonKind");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (kind == RedemptionKind.EXCHANGE) {
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                throw new IllegalArgumentException("exchange requires replacementBarcode");
            }
            if (replacementQuantity <= 0) {
                throw new IllegalArgumentException("exchange requires positive replacementQuantity");
            }
        }
    }
}
