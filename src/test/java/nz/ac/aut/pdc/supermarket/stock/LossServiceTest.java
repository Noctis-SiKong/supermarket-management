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

public class LossServiceTest {

    private static final String BARCODE = "6901234567890";
    private static final Instant FIXED_TIME = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void recordLossDeductsSourcePositionAndCreatesLossRecord() {
        LossService service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(3, List.of(
                new StockBalance(BARCODE, Position.S, 5),
                new StockBalance(BARCODE, Position.W, 10)));

        LossResult result = service.recordLoss(workingCopy, List.of(), BARCODE, Position.S, 2);

        assertTrue(result instanceof LossResult.Prepared);
        LossResult.Prepared prepared = (LossResult.Prepared) result;
        assertEquals(2, prepared.lossRecord().quantity());
        assertEquals(LossOrigin.STORE, prepared.lossRecord().origin());
        assertEquals(LossState.PENDING, prepared.lossRecord().dispositionState());
        assertEquals(1, prepared.entries().size());
        Map<Position, Integer> after = prepared.balancesAfter().stream()
                .collect(Collectors.toMap(StockBalance::position, StockBalance::quantity));
        assertEquals(3, after.get(Position.S).intValue());
        assertEquals(10, after.get(Position.W).intValue());
    }

    @Test
    public void recordLossRejectsInsufficientSourcePosition() {
        LossService service = service();
        FakeWorkingCopy workingCopy = new FakeWorkingCopy(3, List.of(
                new StockBalance(BARCODE, Position.S, 1)));

        LossResult result = service.recordLoss(workingCopy, List.of(), BARCODE, Position.S, 2);

        assertTrue(result instanceof LossResult.Rejected);
        assertEquals("INSUFFICIENT_STOCK", ((LossResult.Rejected) result).code());
    }

    @Test
    public void recordLossRejectsMissingPosition() {
        LossService service = service();

        LossResult result = service.recordLoss(
                new FakeWorkingCopy(1, List.of()), List.of(), BARCODE, null, 1);

        assertTrue(result instanceof LossResult.Rejected);
        assertEquals("INVALID_INPUT", ((LossResult.Rejected) result).code());
    }

    @Test
    public void recordLossRejectsInvalidQuantity() {
        LossService service = service();

        LossResult result = service.recordLoss(
                new FakeWorkingCopy(1, List.of()), List.of(), BARCODE, Position.S, 0);

        assertTrue(result instanceof LossResult.Rejected);
        assertEquals("INVALID_INPUT", ((LossResult.Rejected) result).code());
    }

    private LossService service() {
        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(FIXED_TIME),
                new StockValidator());
        return new LossService(stockOperations, new FakeIdGenerator(FIXED_TIME));
    }
}
