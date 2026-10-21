package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Optional;

/** Read-only IF-06 product adapter port; historical prototype can use doubles.
  */
public interface ProductSnapshotReader {

    Optional<ProductSnapshot> find(String barcode);
}
