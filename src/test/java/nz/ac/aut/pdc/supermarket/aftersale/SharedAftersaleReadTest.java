package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.fixture.FakeIdGenerator;
import nz.ac.aut.pdc.supermarket.fixture.FakeProductCatalog;
import nz.ac.aut.pdc.supermarket.sales.Pricing;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedAftersaleReadTest {

    @Test
    public void readsSaleQuotaAndProductFromAWorkingCopy() {
        WorkingCopy workingCopy = workingCopy();

        OriginalSaleLine line = new SharedSaleReader(workingCopy).readLine("SL-1").orElseThrow();
        assertEquals("6901234567890", line.barcode());
        assertEquals(3, line.quantity());
        assertEquals(new BigDecimal("10.00"), line.paidAmount());

        assertEquals(1, new SharedQuotaLedgerReader(workingCopy).effectiveFor("SL-1").size());
        ProductSnapshot product = new SharedProductSnapshotReader(workingCopy)
                .find("6901234567890").orElseThrow();
        assertEquals("商品", product.name());
    }

    @Test
    public void aftersaleServiceUsesSharedReaders() {
        WorkingCopy workingCopy = workingCopy();
        FakeIdGenerator ids = new FakeIdGenerator(Instant.parse("2026-10-21T04:00:00Z"));
        AftersaleService service = new AftersaleService(
                new SharedSaleReader(workingCopy),
                new SharedQuotaLedgerReader(workingCopy),
                new SharedProductSnapshotReader(workingCopy),
                new StockService(new FakeProductCatalog(Set.of("6901234567890")), ids, new StockValidator()),
                new QuotaViewBuilder(),
                new QuotaCalculator(),
                new QualityPairValidator(),
                new AftersaleAssembler(ids),
                ids);
        AftersaleCommand command = new AftersaleCommand("REQ-1", "V-1", List.of(
                new AftersaleCommandLine("SL-1", RedemptionKind.RETURN, 1, false, false,
                        AftersaleReasonKind.OTHER, "顾客原因", null, 0, false)));

        AftersaleResult result = service.apply(
                new nz.ac.aut.pdc.supermarket.stock.SharedWorkingCopyView(workingCopy),
                command, Role.CLERK);

        assertTrue(result instanceof AftersaleResult.Prepared);
        assertEquals(new BigDecimal("3.33"),
                ((AftersaleResult.Prepared) result).redemptions().get(0).amount());
    }

    private WorkingCopy workingCopy() {
        return new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("PRODUCT", "6901234567890",
                        new Product("6901234567890", "商品", "件", new BigDecimal("8.00"), null)),
                RecordData.of("DOCUMENT", "S-1", new DocumentHeader(
                        "S-1", "SALE", "CONFIRMED", "REQ-0", "FP", "acc-1",
                        "2026-10-01T00:00:00Z", "acc-1", "2026-10-01T00:00:00Z", null)),
                RecordData.of("DOC_LINE", "SL-1",
                        new DocumentLine("SL-1", "S-1", "SALE", "6901234567890", 3)),
                RecordData.of("SALE_LINE", "SL-1", new Pricing.SaleLineSnapshot(
                        "SL-1", "商品", new BigDecimal("8.00"), new BigDecimal("24.00"),
                        null, false, BigDecimal.ZERO, new BigDecimal("10.00"))),
                RecordData.of("REDEMPTION", "R-1", new Redemption(
                        "R-1", "SL-1", "SL-1", "6901234567890", "AF-1",
                        RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                        "REQ-0", null, false, false, "其他原因"))));
    }
}
