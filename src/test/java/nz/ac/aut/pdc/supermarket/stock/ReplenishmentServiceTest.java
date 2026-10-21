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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplenishmentServiceTest {

    private static final String BARCODE = "6901234567890";
    private static final Instant FIXED_TIME = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void replenishmentPreparesBalancedTransferAndBalances() {
        ReplenishmentService service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of(
                new StockBalance(BARCODE, Position.W, 10),
                new StockBalance(BARCODE, Position.S, 5)));

        ReplenishmentResult result = service.replenish(workingCopy, List.of(), BARCODE, 3);

        assertTrue(result instanceof ReplenishmentResult.Prepared);
        ReplenishmentResult.Prepared prepared = (ReplenishmentResult.Prepared) result;
        assertFalse(prepared.transferKey().isBlank());
        assertEquals(2, prepared.entries().size());
        Map<Position, Integer> after = prepared.balancesAfter().stream()
                .collect(Collectors.toMap(StockBalance::position, StockBalance::quantity));
        assertEquals(7, after.get(Position.W).intValue());
        assertEquals(8, after.get(Position.S).intValue());
        assertTrue(prepared.entries().stream().allMatch(entry -> prepared.transferKey().equals(entry.transferKey())));
    }

    @Test
    public void replenishmentRejectsInvalidQuantity() {
        ReplenishmentService service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of());

        ReplenishmentResult result = service.replenish(workingCopy, List.of(), BARCODE, 0);

        assertTrue(result instanceof ReplenishmentResult.Rejected);
        assertEquals("INVALID_INPUT", ((ReplenishmentResult.Rejected) result).code());
    }

    @Test
    public void replenishmentRejectsInsufficientSourcePosition() {
        ReplenishmentService service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(7, List.of(
                new StockBalance(BARCODE, Position.W, 2),
                new StockBalance(BARCODE, Position.S, 5)));

        ReplenishmentResult result = service.replenish(workingCopy, List.of(), BARCODE, 3);

        assertTrue(result instanceof ReplenishmentResult.Rejected);
        assertEquals("INSUFFICIENT_STOCK", ((ReplenishmentResult.Rejected) result).code());
    }

    @Test
    public void replenishmentRejectsUnknownProduct() {
        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of()), new FakeIdGenerator(FIXED_TIME), new StockValidator());
        ReplenishmentService service = new ReplenishmentService(stockOperations, new FakeIdGenerator(FIXED_TIME));
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(1, List.of());

        ReplenishmentResult result = service.replenish(workingCopy, List.of(), "unknown", 1);

        assertTrue(result instanceof ReplenishmentResult.Rejected);
        assertEquals("REFERENCE_INVALID", ((ReplenishmentResult.Rejected) result).code());
    }

    private ReplenishmentService service() {
        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(FIXED_TIME),
                new StockValidator());
        return new ReplenishmentService(stockOperations, new FakeIdGenerator(FIXED_TIME));
    }
}
