package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import nz.ac.aut.pdc.supermarket.fixture.FakeIdGenerator;
import nz.ac.aut.pdc.supermarket.fixture.FakeProductCatalog;
import nz.ac.aut.pdc.supermarket.fixture.FakeWorkingCopy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StockServiceTest {

    private static final String BARCODE = "6901234567890";
    private static final Instant FIXED_TIME = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void replenishmentProducesTwoCandidateEntriesAndPositionBalances() {
        StockOperations service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of(
                new StockBalance(BARCODE, Position.W, 10),
                new StockBalance(BARCODE, Position.S, 5)));

        StockCheckResult result = service.check(workingCopy, List.of(), List.of(
                new StockChangeLine(BARCODE, Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine(BARCODE, Position.S, 3, "doc-1", "T-1", null)));

        assertTrue(result instanceof StockCheckResult.Prepared);
        StockCheckResult.Prepared prepared = (StockCheckResult.Prepared) result;
        assertEquals(2, prepared.entries().size());

        Map<Position, Integer> after = prepared.balancesAfter().stream()
                .collect(Collectors.toMap(StockBalance::position, StockBalance::quantity));
        assertEquals(7, after.get(Position.W).intValue());
        assertEquals(8, after.get(Position.S).intValue());
    }

    @Test
    public void insufficientWarehouseStockIsRejected() {
        StockOperations service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of(
                new StockBalance(BARCODE, Position.W, 2),
                new StockBalance(BARCODE, Position.S, 5)));

        StockCheckResult result = service.check(workingCopy, List.of(), List.of(
                new StockChangeLine(BARCODE, Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine(BARCODE, Position.S, 3, "doc-1", "T-1", null)));

        assertTrue(result instanceof StockCheckResult.Rejected);
        assertEquals("INSUFFICIENT_STOCK", ((StockCheckResult.Rejected) result).code());
    }

    @Test
    public void unknownProductReferenceIsRejected() {
        StockOperations service = new StockService(
                new FakeProductCatalog(Set.of()), new FakeIdGenerator(FIXED_TIME), new StockValidator());
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(1, List.of());

        StockCheckResult result = service.check(workingCopy, List.of(), List.of(
                new StockChangeLine("unknown", Position.S, -1, null, null, null)));

        assertTrue(result instanceof StockCheckResult.Rejected);
        assertEquals("REFERENCE_INVALID", ((StockCheckResult.Rejected) result).code());
    }

    @Test
    public void overlayEntriesAreIncludedInBeforeBalance() {
        StockOperations service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of(
                new StockBalance(BARCODE, Position.W, 2),
                new StockBalance(BARCODE, Position.S, 5)));
        List<StockEntry> overlay = List.of(new StockEntry(
                "SE-overlay", BARCODE, Position.W, 1, "EV-overlay", null, null, null));

        StockCheckResult result = service.check(workingCopy, overlay, List.of(
                new StockChangeLine(BARCODE, Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine(BARCODE, Position.S, 3, "doc-1", "T-1", null)));

        assertTrue(result instanceof StockCheckResult.Prepared);
    }

    private StockOperations service() {
        return new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(FIXED_TIME),
                new StockValidator());
    }
}
