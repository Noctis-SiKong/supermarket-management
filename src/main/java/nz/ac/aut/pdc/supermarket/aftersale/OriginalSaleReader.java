package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Optional;

/** Read-only original-sale adapter port for IF-11; historical prototype can use doubles.
  */
public interface OriginalSaleReader {

    Optional<OriginalSaleLine> readLine(String saleLineId);
}
