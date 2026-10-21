package nz.ac.aut.pdc.supermarket.stock;

import java.util.Collection;
import java.util.List;

/** B-owned IF-07 contract for coordinators: balance reading/candidate checks, never save/submit.
 */
public interface StockOperations {

    /** Read product/position balances for supplied barcodes, not receipt-specific source availability.
  */
    List<StockBalance> balances(StockPorts.WorkingCopyView workingCopy, Collection<String> barcodes);

    /** Check the full new movement group against one read-only working copy and already merged overlay.
     */
    StockCheckResult check(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            List<StockChangeLine> changes);
}
