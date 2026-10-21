package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable minimal original-sale snapshot: stable ID, original barcode, quantity Q, paid P in two-decimal RMB and business time in Asia/Shanghai; historical test model.
 */
public record OriginalSaleLine(
        String saleLineId,
        String barcode,
        int quantity,
        BigDecimal paidAmount,
        Instant saleTime) {

    public OriginalSaleLine {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(paidAmount, "paidAmount");
        if (paidAmount.signum() < 0) {
            throw new IllegalArgumentException("paidAmount must not be negative");
        }
        Objects.requireNonNull(saleTime, "saleTime");
    }
}
