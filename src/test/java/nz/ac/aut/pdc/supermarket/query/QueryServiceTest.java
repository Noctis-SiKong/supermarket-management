package nz.ac.aut.pdc.supermarket.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleLine;
import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaView;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.aftersale.Effect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueryServiceTest {

    private InMemoryOriginalSaleReader originalSaleReader;
    private InMemoryQuotaLedgerReader quotaLedgerReader;
    private QueryService queryService;

    @Before
    public void setUp() {
        originalSaleReader = new InMemoryOriginalSaleReader();
        originalSaleReader.put(new OriginalSaleLine(
                "SL-1", "A", 3, new BigDecimal("10.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        quotaLedgerReader = new InMemoryQuotaLedgerReader();
        quotaLedgerReader.put("SL-1", List.of(new Redemption(
                "R-1", "DL-1", "SL-1", "A", "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-1", null, false, false)));
        queryService = new QueryService(originalSaleReader, quotaLedgerReader, new QuotaViewBuilder());
    }

    @Test
    public void findSaleLineReturnsReadOnlyView() {
        QueryResult<SaleLineView> result = queryService.findSaleLine("SL-1");

        assertTrue(result instanceof QueryResult.Value);
        SaleLineView view = ((QueryResult.Value<SaleLineView>) result).value();
        assertEquals("SL-1", view.saleLineId());
        assertEquals("A", view.barcode());
        assertEquals(3, view.quantity());
    }

    @Test
    public void missingSaleLineIsRejected() {
        QueryResult<SaleLineView> result = queryService.findSaleLine("missing");

        assertTrue(result instanceof QueryResult.Rejected);
        assertEquals("NOT_FOUND", ((QueryResult.Rejected<SaleLineView>) result).code());
    }

    @Test
    public void quotaViewAggregatesHistory() {
        QueryResult<QuotaView> result = queryService.quotaView("SL-1");

        assertTrue(result instanceof QueryResult.Value);
        QuotaView view = ((QueryResult.Value<QuotaView>) result).value();
        assertEquals(1, view.returnedQty());
        assertEquals(2, view.remainingQty());
        assertEquals(new BigDecimal("6.67"), view.remainingAmount());
    }

    @Test
    public void redemptionHistoryIsReadOnlyCopy() {
        QueryResult<List<Redemption>> result = queryService.redemptionHistory("SL-1");

        assertTrue(result instanceof QueryResult.Value);
        assertEquals(1, ((QueryResult.Value<List<Redemption>>) result).value().size());
    }

    @Test
    public void readFailureIsFailedNotNotFound() {
        QueryService failing = new QueryService(
                new ThrowingOriginalSaleReader(), quotaLedgerReader, new QuotaViewBuilder());

        QueryResult<SaleLineView> result = failing.findSaleLine("SL-1");

        assertTrue(result instanceof QueryResult.Failed);
        assertEquals("QUERY_FAILED", ((QueryResult.Failed<SaleLineView>) result).code());
    }

    private static final class InMemoryOriginalSaleReader implements OriginalSaleReader {
        private final Map<String, OriginalSaleLine> lines = new HashMap<>();

        void put(OriginalSaleLine line) {
            lines.put(line.saleLineId(), line);
        }

        @Override
        public Optional<OriginalSaleLine> readLine(String saleLineId) {
            return Optional.ofNullable(lines.get(saleLineId));
        }
    }

    private static final class InMemoryQuotaLedgerReader implements QuotaLedgerReader {
        private final Map<String, List<Redemption>> history = new HashMap<>();

        void put(String saleLineId, List<Redemption> redemptions) {
            history.put(saleLineId, redemptions);
        }

        @Override
        public List<Redemption> effectiveFor(String saleLineId) {
            return history.getOrDefault(saleLineId, List.of());
        }
    }

    private static final class ThrowingOriginalSaleReader implements OriginalSaleReader {
        @Override
        public Optional<OriginalSaleLine> readLine(String saleLineId) {
            throw new IllegalStateException("simulated read failure");
        }
    }
}
