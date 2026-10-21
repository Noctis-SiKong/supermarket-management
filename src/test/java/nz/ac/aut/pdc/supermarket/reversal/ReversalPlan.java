package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.stock.StockEntry;

/** Uncommitted reversal plan: RELEASE facts plus reverse stock candidates; generated only after D1 validation.
 */
public record ReversalPlan(
        String originalRedemptionId,
        Redemption releaseRedemption,
        List<StockEntry> stockEntries) {

    public ReversalPlan {
        if (originalRedemptionId == null || originalRedemptionId.isBlank()) {
            throw new IllegalArgumentException("originalRedemptionId must not be blank");
        }
        Objects.requireNonNull(releaseRedemption, "releaseRedemption");
        stockEntries = List.copyOf(Objects.requireNonNull(stockEntries, "stockEntries"));
    }
}
