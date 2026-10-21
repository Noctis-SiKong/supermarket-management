package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.List;

/** 已提交核销台账只读端口（QUOTA_VIEW 的事实来源，当前用替身）。 */
public interface QuotaLedgerReader {

    List<Redemption> effectiveFor(String saleLineId);
}
