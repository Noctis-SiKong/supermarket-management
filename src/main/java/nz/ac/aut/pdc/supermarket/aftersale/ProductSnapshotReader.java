package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Optional;

/** 商品只读快照端口（IF-06 的 B 侧适配，当前用替身）。 */
public interface ProductSnapshotReader {

    Optional<ProductSnapshot> find(String barcode);
}
