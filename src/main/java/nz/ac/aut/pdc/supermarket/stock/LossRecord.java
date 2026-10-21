package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/**
 * 损耗事实（店内报损骨架）。
 *
 * <p>门店报损会伴随一次来源位置扣减；顾客交回品不在此记录器内产生库存流水。
 * 字段与 ERD LOSS_RECORD 对齐，处置/修正后续追加。</p>
 */
public record LossRecord(
        String lossId,
        String sourceLineId,
        String barcode,
        Position position,
        int quantity,
        LossOrigin origin,
        String disposition,
        LossState dispositionState) {

    public LossRecord {
        if (lossId == null || lossId.isBlank()) {
            throw new IllegalArgumentException("lossId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        Objects.requireNonNull(position, "position");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(dispositionState, "dispositionState");
    }
}
