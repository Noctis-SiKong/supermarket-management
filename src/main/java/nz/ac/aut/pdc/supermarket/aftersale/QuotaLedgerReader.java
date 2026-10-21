package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.List;

/** Read-only committed redemption facts for QUOTA_VIEW; historical prototype can use doubles.
  */
public interface QuotaLedgerReader {

    List<Redemption> effectiveFor(String saleLineId);
}
