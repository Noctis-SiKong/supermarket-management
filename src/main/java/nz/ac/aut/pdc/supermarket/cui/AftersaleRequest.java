package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;

/** Complete CUI aftersale input instead of unrelated scalar Operations arguments.
  */
public record AftersaleRequest(
        String saleLineId,
        RedemptionKind kind,
        int quantity,
        boolean opened,
        boolean qualityProblem,
        AftersaleReasonKind reasonKind,
        String reasonText,
        String replacementBarcode,
        int replacementQuantity,
        String requestKey) {

    public AftersaleRequest withRequestKey(String key) {
        return new AftersaleRequest(saleLineId, kind, quantity, opened, qualityProblem,
                reasonKind, reasonText, replacementBarcode, replacementQuantity, key);
    }

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
