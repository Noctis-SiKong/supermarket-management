package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.catalog.Product;

/** A WorkingCopy 上的 B 商品快照读取适配器。 */
public final class SharedProductSnapshotReader implements ProductSnapshotReader {

    private final WorkingCopy workingCopy;

    public SharedProductSnapshotReader(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public Optional<ProductSnapshot> find(String barcode) {
        return workingCopy.find("PRODUCT", barcode)
                .map(record -> record.as(Product.class))
                .map(product -> new ProductSnapshot(product.name(), product.sellingPrice()));
    }
}
