package nz.ac.aut.pdc.supermarket.fixture;

import java.util.Optional;
import java.util.Set;

import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Fictional read-only IF-06 product-existence snapshot for tests, never production.
 *
 *
 * 虚构商品只读快照（测试替身，非生产实现）。
 *
 * <p>契约：A 的 IF-06 商品读取最小视图；只回答商品是否存在。</p>
 */
public final class FakeProductCatalog implements StockPorts.ProductCatalog {

    private final Set<String> barcodes;

    public FakeProductCatalog(Set<String> barcodes) {
        this.barcodes = Set.copyOf(barcodes);
    }

    @Override
    public Optional<String> productName(String barcode) {
        return barcodes.contains(barcode) ? Optional.of("虚构商品-" + barcode) : Optional.empty();
    }
}
