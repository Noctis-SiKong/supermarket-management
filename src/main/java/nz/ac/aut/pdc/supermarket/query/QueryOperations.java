package nz.ac.aut.pdc.supermarket.query;

import java.util.List;

import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** Read-only WP-05 queries: no writable copies or commits. Production IF-04 adapters enforce role scope; the historical skeleton alone did not filter roles.
 *
 *
 * 只读查询契约（WP-05 查询侧）。
 *
 * <p>查询不打开可写工作副本、不提交；权限范围应由 A 的 IF-04 过滤视图提供，
 * 本骨架暂用替身，未实现角色范围过滤。</p>
 */
public interface QueryOperations {

    QueryResult<SaleLineView> findSaleLine(String saleLineId);

    QueryResult<QuotaView> quotaView(String saleLineId);

    QueryResult<List<Redemption>> redemptionHistory(String saleLineId);

    QueryResult<LossRecordView> findLossRecord(String lossId);

    QueryResult<List<LossRecordView>> lossRecordsForBarcode(String barcode);
}
