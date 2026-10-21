package nz.ac.aut.pdc.supermarket.query;

import java.math.BigDecimal;
import java.time.Instant;

/** 原销售行的只读展示视图（不暴露可写实体）。 */
public record SaleLineView(
        String saleLineId,
        String barcode,
        int quantity,
        BigDecimal paidAmount,
        Instant saleTime) {
}
