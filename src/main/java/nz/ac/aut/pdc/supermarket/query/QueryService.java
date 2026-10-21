package nz.ac.aut.pdc.supermarket.query;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleLine;
import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/**
 * 查询骨架实现：原行视图、额度视图、核销历史。
 *
 * <p>只读；依赖通过端口注入（当前为测试替身）。异常转为 FAILED，不把读取失败
 * 当作"没有记录"。</p>
 */
public final class QueryService implements QueryOperations {

    private final OriginalSaleReader originalSaleReader;
    private final QuotaLedgerReader quotaLedgerReader;
    private final QuotaViewBuilder quotaViewBuilder;

    public QueryService(
            OriginalSaleReader originalSaleReader,
            QuotaLedgerReader quotaLedgerReader,
            QuotaViewBuilder quotaViewBuilder) {
        this.originalSaleReader = Objects.requireNonNull(originalSaleReader, "originalSaleReader");
        this.quotaLedgerReader = Objects.requireNonNull(quotaLedgerReader, "quotaLedgerReader");
        this.quotaViewBuilder = Objects.requireNonNull(quotaViewBuilder, "quotaViewBuilder");
    }

    @Override
    public QueryResult<SaleLineView> findSaleLine(String saleLineId) {
        try {
            return originalSaleReader.readLine(saleLineId)
                    .<QueryResult<SaleLineView>>map(line -> new QueryResult.Value<>(toView(line)))
                    .orElseGet(() -> new QueryResult.Rejected<>("NOT_FOUND", "原销售行不存在：" + saleLineId));
        } catch (RuntimeException ex) {
            return new QueryResult.Failed<>("QUERY_FAILED", "查询暂时不可用");
        }
    }

    @Override
    public QueryResult<QuotaView> quotaView(String saleLineId) {
        try {
            OriginalSaleLine line = originalSaleReader.readLine(saleLineId).orElse(null);
            if (line == null) {
                return new QueryResult.Rejected<>("NOT_FOUND", "原销售行不存在：" + saleLineId);
            }
            return new QueryResult.Value<>(
                    quotaViewBuilder.build(line, quotaLedgerReader.effectiveFor(saleLineId)));
        } catch (RuntimeException ex) {
            return new QueryResult.Failed<>("QUERY_FAILED", "查询暂时不可用");
        }
    }

    @Override
    public QueryResult<List<Redemption>> redemptionHistory(String saleLineId) {
        try {
            if (originalSaleReader.readLine(saleLineId).isEmpty()) {
                return new QueryResult.Rejected<>("NOT_FOUND", "原销售行不存在：" + saleLineId);
            }
            return new QueryResult.Value<>(List.copyOf(quotaLedgerReader.effectiveFor(saleLineId)));
        } catch (RuntimeException ex) {
            return new QueryResult.Failed<>("QUERY_FAILED", "查询暂时不可用");
        }
    }

    private SaleLineView toView(OriginalSaleLine line) {
        return new SaleLineView(
                line.saleLineId(), line.barcode(), line.quantity(), line.paidAmount(), line.saleTime());
    }
}
