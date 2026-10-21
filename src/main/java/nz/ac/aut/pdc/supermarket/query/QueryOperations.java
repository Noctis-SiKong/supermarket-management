package nz.ac.aut.pdc.supermarket.query;

import java.util.List;

import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/**
 * 只读查询契约（WP-05 查询侧）。
 *
 * <p>查询不打开可写工作副本、不提交；权限范围应由 A 的 IF-04 过滤视图提供，
 * 本骨架暂用替身，未实现角色范围过滤。</p>
 */
public interface QueryOperations {

    QueryResult<SaleLineView> findSaleLine(String saleLineId);

    QueryResult<QuotaView> quotaView(String saleLineId);

    QueryResult<List<Redemption>> redemptionHistory(String saleLineId);
}
