package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/** Uncommitted IF-07 stock candidate. IF-05 assigns commitOrdinal; eventKey prevents duplicates and receiptReviewId identifies the approved receipt source.
 *
 *
 * IF-07 产出的"候选库存流水"，尚未提交、不含 commitOrdinal。
 *
 * <p>commitOrdinal 由 IF-05 在提交时统一分配；本记录只用于组装候选变更。
 * eventKey 用于幂等/防重，receiptReviewId 用于收货审核来源链。</p>
 */
public record StockEntry(
        String id,
        String barcode,
        Position position,
        int delta,
        String eventKey,
        String transferKey,
        String sourceLineId,
        String reverseOfId,
        String receiptReviewId) {

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

    /** Compatibility constructor without receipt-review reference.
 *
 * 兼容旧调用的构造器：收货审核引用留空。 */
    public StockEntry(
            String id, String barcode, Position position, int delta,
            String eventKey, String transferKey, String sourceLineId, String reverseOfId) {
        this(id, barcode, position, delta, eventKey, transferKey, sourceLineId, reverseOfId, null);
    }
}
