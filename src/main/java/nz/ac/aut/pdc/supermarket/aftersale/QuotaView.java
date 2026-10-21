package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;

/** Derived quota: returned Qr/Mr and exchanged Qx/Mx consume original quotas. Rebuild from facts; never maintain independent refundable balances.
 */
public record QuotaView(
        String saleLineId,
        int originalQty,
        BigDecimal originalPaid,
        int returnedQty,
        BigDecimal returnedAmount,
        int exchangedQty,
        BigDecimal exchangedAmount,
        int remainingQty,
        BigDecimal remainingAmount) {

    public QuotaView {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        if (originalQty < 0 || returnedQty < 0 || exchangedQty < 0 || remainingQty < 0) {
            throw new IllegalArgumentException("quantities must not be negative");
        }
        if (originalPaid == null || involvedAmountNegative(
                returnedAmount, exchangedAmount, remainingAmount)) {
            throw new IllegalArgumentException("amounts must be non-negative");
        }
    }

    private static boolean involvedAmountNegative(BigDecimal... amounts) {
        for (BigDecimal amount : amounts) {
            if (amount == null || amount.signum() < 0) {
                return true;
            }
        }
        return false;
    }
}
