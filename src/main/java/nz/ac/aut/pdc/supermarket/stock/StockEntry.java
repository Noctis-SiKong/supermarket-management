package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/** Uncommitted IF-07 stock candidate. IF-05 assigns commitOrdinal; eventKey prevents duplicates and receiptReviewId identifies the approved receipt source.
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
  */
    public StockEntry(
            String id, String barcode, Position position, int delta,
            String eventKey, String transferKey, String sourceLineId, String reverseOfId) {
        this(id, barcode, position, delta, eventKey, transferKey, sourceLineId, reverseOfId, null);
    }
}
