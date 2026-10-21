package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable minimal original-sale snapshot: stable ID, original barcode, quantity Q, paid P in two-decimal RMB and business time in Asia/Shanghai; historical test model.
 *
 *
 * 原销售行的不可变快照（IF-11 返回的最小只读字段，测试用模型）。
 *
 * @param saleLineId 稳定原销售行 ID
 * @param barcode    原商品条码
 * @param quantity   原购买数量 Q
 * @param paidAmount 原行实付 P（RMB，两位小数）
 * @param saleTime   原销售业务时间（Asia/Shanghai）
 */
public record OriginalSaleLine(
        String saleLineId,
        String barcode,
        int quantity,
        BigDecimal paidAmount,
        Instant saleTime) {

    public OriginalSaleLine {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(paidAmount, "paidAmount");
        if (paidAmount.signum() < 0) {
            throw new IllegalArgumentException("paidAmount must not be negative");
        }
        Objects.requireNonNull(saleTime, "saleTime");
    }
}
