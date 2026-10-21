package nz.ac.aut.pdc.supermarket.stock;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.shared.WorkingCopy;

/** B 的 IF-07 只读视图在 A WorkingCopy 上的适配器。 */
public final class SharedWorkingCopyView implements StockPorts.WorkingCopyView {

    private final WorkingCopy workingCopy;

    public SharedWorkingCopyView(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public long baseLast() {
        return workingCopy.baseLast();
    }

    @Override
    public List<StockBalance> balances(Collection<String> barcodes) {
        return SharedStockProjection.balances(workingCopy, barcodes);
    }
}
