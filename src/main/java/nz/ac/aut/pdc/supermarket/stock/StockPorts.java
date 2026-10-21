package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * IF-07 依赖的只读协作端口。
 *
 * <p>这些是 B 侧适配 A 公共契约的内部端口：正式实现由 A 的 WorkingCopy / IF-03 /
 * IF-06 提供，本骨架用测试替身（虚构、非生产）。</p>
 */
public final class StockPorts {

    private StockPorts() {
    }

    /** A 的 IF-04 工作副本的最小只读视图。 */
    public interface WorkingCopyView {

        long baseLast();

        List<StockBalance> balances(Collection<String> barcodes);
    }

    /** A 的 IF-06 商品只读快照的最小视图。 */
    public interface ProductCatalog {

        Optional<String> productName(String barcode);
    }

    /** A 的 IF-03 稳定 ID 与业务时间。 */
    public interface IdGenerator {

        String nextId(String prefix);

        Instant now();
    }
}
