package nz.ac.aut.pdc.supermarket.query;

import java.util.List;

import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** Read-only WP-05 queries: no writable copies or commits. Production IF-04 adapters enforce role scope; the historical skeleton alone did not filter roles.
 */
public interface QueryOperations {

    QueryResult<SaleLineView> findSaleLine(String saleLineId);

    QueryResult<QuotaView> quotaView(String saleLineId);

    QueryResult<List<Redemption>> redemptionHistory(String saleLineId);

    QueryResult<LossRecordView> findLossRecord(String lossId);

    QueryResult<List<LossRecordView>> lossRecordsForBarcode(String barcode);
}
