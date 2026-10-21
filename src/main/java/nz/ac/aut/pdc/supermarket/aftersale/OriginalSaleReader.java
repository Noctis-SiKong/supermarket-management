package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Optional;

/** Read-only original-sale adapter port for IF-11; historical prototype can use doubles.
 *
 * 原销售行只读端口（IF-11 的 B 侧适配，当前用替身）。 */
public interface OriginalSaleReader {

    Optional<OriginalSaleLine> readLine(String saleLineId);
}
