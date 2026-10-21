package nz.ac.aut.pdc.supermarket.query;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleLine;
import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.stock.LossRecord;

/** Read-only original-line, quota, redemption-history and loss-record queries without submission.
  */
public final class QueryService implements QueryOperations {

    private final OriginalSaleReader originalSaleReader;
    private final QuotaLedgerReader quotaLedgerReader;
    private final QuotaViewBuilder quotaViewBuilder;
    private final LossRecordReader lossRecordReader;

    public QueryService(
            OriginalSaleReader originalSaleReader,
            QuotaLedgerReader quotaLedgerReader,
            QuotaViewBuilder quotaViewBuilder,
            LossRecordReader lossRecordReader) {
        this.originalSaleReader = Objects.requireNonNull(originalSaleReader, "originalSaleReader");
        this.quotaLedgerReader = Objects.requireNonNull(quotaLedgerReader, "quotaLedgerReader");
        this.quotaViewBuilder = Objects.requireNonNull(quotaViewBuilder, "quotaViewBuilder");
        this.lossRecordReader = Objects.requireNonNull(lossRecordReader, "lossRecordReader");
    }

    /** Compatibility constructor without loss-record reading.
  */
    public QueryService(
            OriginalSaleReader originalSaleReader,
            QuotaLedgerReader quotaLedgerReader,
            QuotaViewBuilder quotaViewBuilder) {
        this(originalSaleReader, quotaLedgerReader, quotaViewBuilder, new EmptyLossRecordReader());
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

    @Override
    public QueryResult<LossRecordView> findLossRecord(String lossId) {
        try {
            return lossRecordReader.findById(lossId)
                    .<QueryResult<LossRecordView>>map(record -> new QueryResult.Value<>(toView(record)))
                    .orElseGet(() -> new QueryResult.Rejected<>("NOT_FOUND", "损耗记录不存在：" + lossId));
        } catch (RuntimeException ex) {
            return new QueryResult.Failed<>("QUERY_FAILED", "查询暂时不可用");
        }
    }

    @Override
    public QueryResult<List<LossRecordView>> lossRecordsForBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return new QueryResult.Rejected<>("INVALID_INPUT", "商品条码不能为空");
        }
        try {
            return new QueryResult.Value<>(lossRecordReader.findByBarcode(barcode).stream()
                    .map(this::toView)
                    .toList());
        } catch (RuntimeException ex) {
            return new QueryResult.Failed<>("QUERY_FAILED", "查询暂时不可用");
        }
    }

    private SaleLineView toView(OriginalSaleLine line) {
        return new SaleLineView(
                line.saleLineId(), line.barcode(), line.quantity(), line.paidAmount(), line.saleTime());
    }

    private LossRecordView toView(LossRecord record) {
        return new LossRecordView(
                record.lossId(), record.barcode(), record.position(), record.quantity(),
                record.origin(), record.disposition(), record.dispositionState());
    }

    private static final class EmptyLossRecordReader implements LossRecordReader {
        @Override
        public java.util.Optional<LossRecord> findById(String lossId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<LossRecord> findByBarcode(String barcode) {
            return List.of();
        }
    }
}
