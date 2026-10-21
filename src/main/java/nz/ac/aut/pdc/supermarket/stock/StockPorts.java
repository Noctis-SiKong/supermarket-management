package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Internal read-only IF-07 ports adapting A WorkingCopy/IF-03/IF-06; production uses thin Shared adapters and doubles stay in tests.
 */
public final class StockPorts {

    private StockPorts() {
    }

    /** Minimal read-only IF-04 working-copy view.
  */
    public interface WorkingCopyView {

        long baseLast();

        List<StockBalance> balances(Collection<String> barcodes);
    }

    /** Minimal read-only IF-06 product view.
  */
    public interface ProductCatalog {

        Optional<String> productName(String barcode);
    }

    /** IF-03 stable IDs and business time.
  */
    public interface IdGenerator {

        String nextId(String prefix);

        Instant now();
    }
}
