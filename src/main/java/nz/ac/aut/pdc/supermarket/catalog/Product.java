package nz.ac.aut.pdc.supermarket.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Product(String barcode, String name, String unit, BigDecimal sellingPrice, String zeroPriceNote) {
    public Product {
        if (barcode == null || !barcode.matches("[0-9]{8,14}") || name == null || name.isBlank() || unit == null || unit.isBlank())
            throw new IllegalArgumentException("Barcode/name/unit required");
        if (sellingPrice == null || sellingPrice.signum() < 0 || sellingPrice.compareTo(new BigDecimal("999999.99")) > 0)
            throw new IllegalArgumentException("Price outside 0–999999.99");
        sellingPrice = sellingPrice.setScale(2, RoundingMode.UNNECESSARY);
        if (sellingPrice.signum() == 0 && (zeroPriceNote == null || zeroPriceNote.isBlank()))
            throw new IllegalArgumentException("Zero price note required");
    }
}
