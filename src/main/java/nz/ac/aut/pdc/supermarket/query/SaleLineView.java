package nz.ac.aut.pdc.supermarket.query;

import java.math.BigDecimal;
import java.time.Instant;

/** Read-only original-sale presentation view, never a mutable entity.
  */
public record SaleLineView(
        String saleLineId,
        String barcode,
        int quantity,
        BigDecimal paidAmount,
        Instant saleTime) {
}
