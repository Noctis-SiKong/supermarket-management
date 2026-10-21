package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/**
 * IF-07 产出的"候选库存流水"，尚未提交、不含 commitOrdinal。
 *
 * <p>commitOrdinal 由 IF-05 在提交时统一分配；本记录只用于组装候选变更。</p>
 */
public record StockEntry(
        String id,
        String barcode,
        Position position,
        int delta,
        String eventKey,
        String transferKey,
        String sourceLineId,
        String reverseOfId) {

    public StockEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        Objects.requireNonNull(position, "position");
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("eventKey must not be blank");
        }
    }
}
