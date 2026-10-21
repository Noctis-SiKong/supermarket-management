package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;

/**
 * 换货替换品明细（ERD REPLACEMENT 的 B 侧模型）。
 *
 * <p>普通换货用 redemptionId 关联本次核销；质量同款零差另用
 * qualityRedemptionId 做特殊一对一配对。</p>
 */
public record Replacement(
        String lineId,
        String aftersaleId,
        String redemptionId,
        String barcode,
        int quantity,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        BigDecimal replacementAmount,
        String exchangeKind,
        String qualityRedemptionId) {

    public Replacement {
        if (lineId == null || lineId.isBlank()) {
            throw new IllegalArgumentException("lineId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (replacementAmount == null) {
            throw new IllegalArgumentException("replacementAmount must not be null");
        }
        if (replacementAmount.signum() < 0) {
            throw new IllegalArgumentException("replacementAmount must not be negative");
        }
    }
}
