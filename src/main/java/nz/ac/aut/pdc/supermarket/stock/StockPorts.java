package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Internal read-only IF-07 ports adapting A WorkingCopy/IF-03/IF-06; production uses thin Shared adapters and doubles stay in tests.
 *
 *
 * IF-07 依赖的只读协作端口。
 *
 * <p>这些是 B 侧适配 A 公共契约的内部端口：正式实现由 A 的 WorkingCopy / IF-03 /
 * IF-06 提供，生产使用 SharedWorkingCopyView 等薄适配；替身仅在测试源码。</p>
 */
public final class StockPorts {

    private StockPorts() {
    }

    /** Minimal read-only IF-04 working-copy view.
 *
 * A 的 IF-04 工作副本的最小只读视图。 */
    public interface WorkingCopyView {

        long baseLast();

        List<StockBalance> balances(Collection<String> barcodes);
    }

    /** Minimal read-only IF-06 product view.
 *
 * A 的 IF-06 商品只读快照的最小视图。 */
    public interface ProductCatalog {

        Optional<String> productName(String barcode);
    }

    /** IF-03 stable IDs and business time.
 *
 * A 的 IF-03 稳定 ID 与业务时间。 */
    public interface IdGenerator {

        String nextId(String prefix);

        Instant now();
    }
}
