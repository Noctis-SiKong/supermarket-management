package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/**
 * 某商品在某位置的只读库存余额（来自工作副本或替身快照）。
 *
 * @param barcode  商品条码
 * @param position 库存位置
 * @param quantity 非负余额
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
