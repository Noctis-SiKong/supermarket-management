package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Read-only B product-catalog adapter over A WorkingCopy.
 *
 * A WorkingCopy 上的 B 商品只读目录适配器。 */
public final class SharedProductCatalog implements StockPorts.ProductCatalog {

    private final WorkingCopy workingCopy;

    public SharedProductCatalog(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public Optional<String> productName(String barcode) {
        return workingCopy.find("PRODUCT", barcode)
                .map(record -> record.as(Product.class).name());
    }
}
