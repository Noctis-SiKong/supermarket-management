package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/** Signed IF-07 input movement: eventKey must remain stable across retries; historical missing keys get one-off IDs. StockValidator checks quantities 1-9999.
 *
 *
 * 本次业务请求中的一条有符号库存变动（IF-07 check 的输入）。
 *
 * <p>事件键由调用方按同一业务意图稳定提供；若调用方未提供，骨架实现会生成
 * 一次性的键，重试场景必须由调用方传入相同 eventKey 才能保证幂等语义。
 * 业务数量边界（1–9999）由 {@link StockValidator} 统一校验。</p>
 */
public record StockChangeLine(
        String barcode,
        Position position,
        int delta,
        String sourceLineId,
        String transferKey,
        String reverseOfId,
        String eventKey,
        String receiptReviewId) {

    public StockChangeLine {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        Objects.requireNonNull(position, "position");
    }

    /** Compatibility constructor omitting event key and receipt-review reference.
 *
 * 兼容旧调用的构造器：事件键/收货审核引用留空。 */
    public StockChangeLine(
            String barcode, Position position, int delta,
            String sourceLineId, String transferKey, String reverseOfId) {
        this(barcode, position, delta, sourceLineId, transferKey, reverseOfId, null, null);
    }
}
