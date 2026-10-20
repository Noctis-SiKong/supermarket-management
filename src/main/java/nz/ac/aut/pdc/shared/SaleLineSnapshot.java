package nz.ac.aut.pdc.shared;

import java.math.BigDecimal;

/** IF-11 minimal immutable data shape; dedicated cross-person access remains pending approval. */
public record SaleLineSnapshot(String saleId, String lineId, String barcode, String productName,
    BigDecimal unitPrice, int originalQuantity, String promotionSnapshot, BigDecimal grossAmount,
    BigDecimal allocatedDiscount, BigDecimal paidAmount, String soldAt) {}
