package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.fixture.FakeIdGenerator;
import nz.ac.aut.pdc.supermarket.fixture.FakeProductCatalog;
import nz.ac.aut.pdc.supermarket.fixture.FakeWorkingCopy;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockBalance;
import nz.ac.aut.pdc.supermarket.stock.StockOperations;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AftersaleServiceTest {

    private static final String SALE_LINE = "SL-1";
    private static final String BARCODE = "A";
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    private InMemoryOriginalSaleReader originalSaleReader;
    private InMemoryQuotaLedgerReader quotaLedgerReader;
    private InMemoryProductSnapshotReader productReader;
    private AftersaleService service;
    private FakeWorkingCopy workingCopy;

    @Before
    public void setUp() {
        FakeIdGenerator ids = new FakeIdGenerator(NOW);
        originalSaleReader = new InMemoryOriginalSaleReader();
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 3, new BigDecimal("10.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        quotaLedgerReader = new InMemoryQuotaLedgerReader();
        productReader = new InMemoryProductSnapshotReader();
        productReader.put(BARCODE, new ProductSnapshot("虚构商品", new BigDecimal("8.00")));

        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(NOW),
                new StockValidator());
        service = new AftersaleService(
                originalSaleReader,
                quotaLedgerReader,
                productReader,
                stockOperations,
                new QuotaViewBuilder(),
                new QuotaCalculator(),
                new QualityPairValidator(),
                new AftersaleAssembler(ids),
                ids);
        workingCopy = new FakeWorkingCopy(1, List.of(new StockBalance(BARCODE, Position.S, 5)));
    }

    @Test
    public void ac79ThreeSingleUnitReturnsUseStableCentAllocation() {
        AftersaleCommand command = command(
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false),
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false),
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) service.apply(
                workingCopy, command, Role.CLERK);

        assertEquals(
                List.of(new BigDecimal("3.33"), new BigDecimal("3.33"), new BigDecimal("3.34")),
                prepared.redemptions().stream().map(Redemption::amount).toList());
    }

    @Test
    public void sameLinesProduceSameResultRegardlessOfInputOrder() {
        AftersaleCommand first = command(
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false),
                line(RedemptionKind.RETURN, 2, false, false, null, 0, false));
        AftersaleCommand second = command(
                line(RedemptionKind.RETURN, 2, false, false, null, 0, false),
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult.Prepared one = (AftersaleResult.Prepared) service.apply(
                workingCopy, first, Role.CLERK);
        AftersaleResult.Prepared two = (AftersaleResult.Prepared) service.apply(
                workingCopy, second, Role.CLERK);

        assertEquals(
                one.redemptions().stream().map(Redemption::amount).toList(),
                two.redemptions().stream().map(Redemption::amount).toList());
        assertEquals(
                one.settlements().stream().map(Settlement::amount).toList(),
                two.settlements().stream().map(Settlement::amount).toList());
    }

    @Test
    public void ac34ExchangeComputesChargeRefundAndZeroDifference() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 1, new BigDecimal("48.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        productReader.put(BARCODE, new ProductSnapshot("替换品", new BigDecimal("55.00")));
        AftersaleCommand command = command(
                line(RedemptionKind.EXCHANGE, 1, false, false, BARCODE, 1, false));

        AftersaleResult.Prepared charge = (AftersaleResult.Prepared) service.apply(
                workingCopy, command, Role.CLERK);
        assertEquals(SettlementDirection.CHARGE, charge.settlements().get(0).direction());
        assertEquals(new BigDecimal("7.00"), charge.settlements().get(0).amount());

        productReader.put(BARCODE, new ProductSnapshot("替换品", new BigDecimal("30.00")));
        AftersaleResult.Prepared refund = (AftersaleResult.Prepared) service.apply(
                workingCopy, command, Role.CLERK);
        assertEquals(SettlementDirection.REFUND, refund.settlements().get(0).direction());
        assertEquals(new BigDecimal("18.00"), refund.settlements().get(0).amount());

        productReader.put(BARCODE, new ProductSnapshot("替换品", new BigDecimal("48.00")));
        AftersaleResult.Prepared zero = (AftersaleResult.Prepared) service.apply(
                workingCopy, command, Role.CLERK);
        assertEquals(SettlementDirection.NONE, zero.settlements().get(0).direction());
    }

    @Test
    public void ac47WindowEndsAtShanghaiMidnight() {
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult inside = serviceAt(Instant.parse("2026-10-31T15:59:59Z"))
                .apply(workingCopy, command, Role.CLERK);
        AftersaleResult outside = serviceAt(Instant.parse("2026-10-31T16:00:00Z"))
                .apply(workingCopy, command, Role.CLERK);

        assertTrue(inside instanceof AftersaleResult.Prepared);
        assertTrue(outside instanceof AftersaleResult.Rejected);
        assertEquals("FORBIDDEN", ((AftersaleResult.Rejected) outside).code());
    }

    @Test
    public void ac09AndAc17SingleReturnRefundsOriginalPaidEight() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 1, new BigDecimal("8.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) service.apply(
                workingCopy, command, Role.CLERK);

        assertEquals(new BigDecimal("8.00"), prepared.redemptions().get(0).amount());
        assertEquals(new BigDecimal("8.00"), prepared.settlements().get(0).amount());
        assertEquals(SettlementDirection.REFUND, prepared.settlements().get(0).direction());
    }

    private AftersaleService serviceAt(Instant now) {
        FakeIdGenerator ids = new FakeIdGenerator(now);
        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(now),
                new StockValidator());
        return new AftersaleService(
                originalSaleReader,
                quotaLedgerReader,
                productReader,
                stockOperations,
                new QuotaViewBuilder(),
                new QuotaCalculator(),
                new QualityPairValidator(),
                new AftersaleAssembler(ids),
                ids);
    }

    @Test
    public void ac33ReturnedAndExchangedLeavesOnlyOneUnit() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 3, new BigDecimal("10.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        quotaLedgerReader.put(SALE_LINE, new Redemption(
                "R-1", "R-1", SALE_LINE, BARCODE, "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-H1", null, false, false, "其他原因"));
        quotaLedgerReader.put(SALE_LINE, new Redemption(
                "R-2", "R-2", SALE_LINE, BARCODE, "AF-1",
                RedemptionKind.EXCHANGE, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-H2", null, false, false, "其他原因"));

        AftersaleResult result = service.apply(
                workingCopy, command(line(RedemptionKind.RETURN, 2, false, false, null, 0, false)),
                Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Rejected);
    }

    @Test
    public void partialReturnCalculatesQuotaAmountAndRefund() {
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Prepared);
        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) result;
        assertEquals(1, prepared.redemptions().size());
        assertEquals(new BigDecimal("3.33"), prepared.redemptions().get(0).amount());
        assertTrue(prepared.replacements().isEmpty());
        assertTrue(prepared.stockEntries().isEmpty());
        assertEquals(SettlementDirection.REFUND, prepared.settlements().get(0).direction());
        assertEquals(new BigDecimal("3.33"), prepared.settlements().get(0).amount());
    }

    @Test
    public void qualityExchangeCreatesReplacementAndDeductsStock() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 1, new BigDecimal("8.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        AftersaleCommand command = command(line(
                RedemptionKind.EXCHANGE, 1, false, true, BARCODE, 1, true));

        AftersaleResult result = service.apply(workingCopy, command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Prepared);
        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) result;
        assertEquals(new BigDecimal("8.00"), prepared.redemptions().get(0).amount());
        Replacement replacement = prepared.replacements().get(0);
        assertEquals(prepared.redemptions().get(0).redemptionId(), replacement.qualityRedemptionId());
        assertEquals(1, prepared.stockEntries().size());
        assertEquals(-1, prepared.stockEntries().get(0).delta());
        assertEquals(SettlementDirection.NONE, prepared.settlements().get(0).direction());
    }

    @Test
    public void openedNonQualityIsRejectedWithoutManagerException() {
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, true, false, null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.MANAGER);

        assertTrue(result instanceof AftersaleResult.Rejected);
        assertEquals("FORBIDDEN", ((AftersaleResult.Rejected) result).code());
    }

    @Test
    public void managerCannotOverrideOpenedNonQuality() {
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, true, false, null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.MANAGER);

        assertTrue(result instanceof AftersaleResult.Rejected);
        assertEquals("FORBIDDEN", ((AftersaleResult.Rejected) result).code());
    }

    @Test
    public void outOfWindowSaleIsRejectedEvenForManager() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 3, new BigDecimal("10.00"),
                Instant.parse("2026-09-01T00:00:00Z")));
        AftersaleCommand command = command(line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.MANAGER);

        assertTrue(result instanceof AftersaleResult.Rejected);
        assertEquals("FORBIDDEN", ((AftersaleResult.Rejected) result).code());
    }

    @Test
    public void unknownSaleLineIsRejected() {
        AftersaleCommand command = command(new AftersaleCommandLine(
                "missing", RedemptionKind.RETURN, 1, false, false,
                AftersaleReasonKind.OTHER, "顾客原因", null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Rejected);
        assertEquals("REFERENCE_INVALID", ((AftersaleResult.Rejected) result).code());
    }

    @Test
    public void insufficientReplacementStockIsRejected() {
        originalSaleReader.put(new OriginalSaleLine(
                SALE_LINE, BARCODE, 2, new BigDecimal("16.00"),
                Instant.parse("2026-10-01T00:00:00Z")));
        workingCopy = new FakeWorkingCopy(1, List.of(new StockBalance(BARCODE, Position.S, 1)));
        AftersaleCommand command = command(line(
                RedemptionKind.EXCHANGE, 2, false, false, BARCODE, 2, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Rejected);
        assertEquals("INSUFFICIENT_STOCK", ((AftersaleResult.Rejected) result).code());
    }

    @Test
    public void multipleLinesForSameSaleLineConsumeQuotaSequentially() {
        AftersaleCommand command = command(
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false),
                line(RedemptionKind.RETURN, 1, false, false, null, 0, false));

        AftersaleResult result = service.apply(workingCopy, command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Prepared);
        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) result;
        assertEquals(2, prepared.redemptions().size());
        assertEquals(new BigDecimal("3.33"), prepared.redemptions().get(0).amount());
        assertEquals(new BigDecimal("3.33"), prepared.redemptions().get(1).amount());
        assertEquals(2, prepared.settlements().size());
    }

    @Test
    public void resultCarriesGeneratedIdsAndReasonText() {
        AftersaleResult result = service.apply(
                workingCopy, command(line(RedemptionKind.RETURN, 1, false, false, null, 0, false)), Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Prepared);
        Redemption redemption = ((AftersaleResult.Prepared) result).redemptions().get(0);
        assertNotNull(redemption.redemptionId());
        assertEquals("REQ-1", redemption.requestKey());
        assertEquals("顾客原因", redemption.reasonText());
        assertFalse(redemption.qualityProblem());
    }

    @Test(expected = IllegalArgumentException.class)
    public void otherReasonRequiresReasonText() {
        new AftersaleCommandLine(
                SALE_LINE, RedemptionKind.RETURN, 1, false, false,
                AftersaleReasonKind.OTHER, " ", null, 0, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void qualityReasonRequiresQualityProblem() {
        new AftersaleCommandLine(
                SALE_LINE, RedemptionKind.RETURN, 1, false, false,
                AftersaleReasonKind.QUALITY, null, null, 0, false);
    }

    private AftersaleCommand command(AftersaleCommandLine... lines) {
        return new AftersaleCommand("REQ-1", "V-1", List.of(lines));
    }

    private AftersaleCommandLine line(
            RedemptionKind kind, int quantity, boolean opened, boolean qualityProblem,
            String replacementBarcode, int replacementQty, boolean qualityPair) {
        AftersaleReasonKind reasonKind;
        String reasonText;
        if (qualityProblem) {
            reasonKind = AftersaleReasonKind.QUALITY;
            reasonText = null;
        } else {
            reasonKind = AftersaleReasonKind.OTHER;
            reasonText = "顾客原因";
        }
        return new AftersaleCommandLine(
                SALE_LINE, kind, quantity, opened, qualityProblem, reasonKind, reasonText,
                replacementBarcode, replacementQty, qualityPair);
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

        void put(String saleLineId, Redemption redemption) {
            history.computeIfAbsent(saleLineId, ignored -> new ArrayList<>()).add(redemption);
        }

        @Override
        public List<Redemption> effectiveFor(String saleLineId) {
            return history.getOrDefault(saleLineId, List.of());
        }
    }

    private static final class InMemoryProductSnapshotReader implements ProductSnapshotReader {
        private final Map<String, ProductSnapshot> products = new HashMap<>();

        void put(String barcode, ProductSnapshot product) {
            products.put(barcode, product);
        }

        @Override
        public Optional<ProductSnapshot> find(String barcode) {
            return Optional.ofNullable(products.get(barcode));
        }
    }
}


