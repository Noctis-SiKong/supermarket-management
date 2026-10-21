package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/** Read-only barcode/position/nonnegative quantity balance from a working copy or test snapshot.
 */
public record StockBalance(String barcode, Position position, int quantity) {

    public StockBalance {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        Objects.requireNonNull(position, "position");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
    }
}
