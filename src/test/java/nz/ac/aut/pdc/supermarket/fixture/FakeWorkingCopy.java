package nz.ac.aut.pdc.supermarket.fixture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockBalance;
import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Fictional read-only IF-04 balance view (shared v1.02 section 5.3); uses supplied balances and never accesses real data.
 *
 *
 * 虚构只读工作副本（测试替身，非生产实现）。
 *
 * <p>不读写真实 data/，只按构造时给定的固定余额回答 IF-04 的最小只读视图。
 * 契约版本：共享规范 v1.02 §5.3 的 WorkingCopyView 逻辑视图。</p>
 */
public final class FakeWorkingCopy implements StockPorts.WorkingCopyView {

    private final long baseLast;
    private final List<StockBalance> balances;

    public FakeWorkingCopy(long baseLast, List<StockBalance> balances) {
        this.baseLast = baseLast;
        this.balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
    }

    @Override
    public long baseLast() {
        return baseLast;
    }

    @Override
    public List<StockBalance> balances(Collection<String> barcodes) {
        List<StockBalance> result = new ArrayList<>();
        for (StockBalance balance : balances) {
            if (barcodes.contains(balance.barcode())) {
                result.add(balance);
            }
        }
        return result;
    }

    /** Convenience balance factory for tests.
 *
 * 便于测试构造余额。 */
    public static StockBalance balance(String barcode, Position position, int quantity) {
        return new StockBalance(barcode, position, quantity);
    }
}
