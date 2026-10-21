package nz.ac.aut.pdc.supermarket.reversal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.fixture.FakeIdGenerator;
import nz.ac.aut.pdc.supermarket.fixture.FakeProductCatalog;
import nz.ac.aut.pdc.supermarket.fixture.FakeWorkingCopy;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockBalance;
import nz.ac.aut.pdc.supermarket.stock.StockEntry;
import nz.ac.aut.pdc.supermarket.stock.StockOperations;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReversalServiceTest {

    private static final String BARCODE = "A";
    private static final String REDEMPTION_ID = "R-1";
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");
    private static final ReversalCommand COMMAND =
            new ReversalCommand("REQ-R-1", REDEMPTION_ID, "误操作冲销");

    private InMemoryRedemptionReader redemptionReader;
    private InMemoryDependencyReader dependencyReader;
    private InMemoryStockEffectReader stockEffectReader;
    private ReversalService service;
    private FakeWorkingCopy workingCopy;

    @Before
    public void setUp() {
        redemptionReader = new InMemoryRedemptionReader();
        redemptionReader.put(new Redemption(
                REDEMPTION_ID, "DL-1", "SL-1", BARCODE, "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-1", null, false, false));
        dependencyReader = new InMemoryDependencyReader();
        stockEffectReader = new InMemoryStockEffectReader();
        stockEffectReader.put(REDEMPTION_ID, List.of(new StockEntry(
                "SE-1", BARCODE, Position.S, 1, "EV-1", null, "SL-1", null)));

        StockOperations stockOperations = new StockService(
                new FakeProductCatalog(Set.of(BARCODE)),
                new FakeIdGenerator(NOW),
                new StockValidator());
        service = new ReversalService(
                redemptionReader, dependencyReader, stockEffectReader, stockOperations,
                new FakeIdGenerator(NOW));
        workingCopy = new FakeWorkingCopy(1, List.of(
                new StockBalance(BARCODE, Position.S, 5)));
    }

    @Test
    public void managerCanPrepareReleaseAndReversedStock() {
        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Prepared);
        ReversalPlan plan = ((ReversalResult.Prepared) result).plan();
        assertEquals(REDEMPTION_ID, plan.releaseRedemption().reverseOfId());
        assertEquals(Effect.RELEASE, plan.releaseRedemption().effect());
        assertEquals(1, plan.stockEntries().size());
        assertEquals(-1, plan.stockEntries().get(0).delta());
        assertEquals("SE-1", plan.stockEntries().get(0).reverseOfId());
        assertEquals("REV-EV-1", plan.stockEntries().get(0).eventKey());
    }

    @Test
    public void clerkCannotReverse() {
        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.CLERK);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("FORBIDDEN", ((ReversalResult.Rejected) result).code());
    }

    @Test
    public void dependentRecordsBlockReversal() {
        dependencyReader.put(REDEMPTION_ID, List.of("AF-2"));

        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("DEPENDENCY_BLOCKED", ((ReversalResult.Rejected) result).code());
    }

    @Test
    public void alreadyReversedIsConflict() {
        redemptionReader.putReversal(REDEMPTION_ID, new Redemption(
                "R-2", "DL-2", "SL-1", BARCODE, "AF-1",
                RedemptionKind.RETURN, Effect.RELEASE, 1, new BigDecimal("3.33"),
                "REQ-2", REDEMPTION_ID, false, false));

        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("EVENT_CONFLICT", ((ReversalResult.Rejected) result).code());
    }

    @Test
    public void insufficientStockIsRejected() {
        workingCopy = new FakeWorkingCopy(1, List.of(
                new StockBalance(BARCODE, Position.S, 0)));

        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("INSUFFICIENT_STOCK", ((ReversalResult.Rejected) result).code());
    }

    @Test
    public void missingRedemptionIsRejected() {
        redemptionReader.remove(REDEMPTION_ID);

        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("REFERENCE_INVALID", ((ReversalResult.Rejected) result).code());
    }

    @Test
    public void missingStockEffectIsRejected() {
        stockEffectReader.put(REDEMPTION_ID, List.of());

        ReversalResult result = service.reverse(workingCopy, COMMAND, Role.MANAGER);

        assertTrue(result instanceof ReversalResult.Rejected);
        assertEquals("REFERENCE_INVALID", ((ReversalResult.Rejected) result).code());
    }

    private static final class InMemoryRedemptionReader implements RedemptionReader {
        private final Map<String, Redemption> redemptions = new HashMap<>();
        private final Map<String, Redemption> reversals = new HashMap<>();

        void put(Redemption redemption) {
            redemptions.put(redemption.redemptionId(), redemption);
        }

        void remove(String redemptionId) {
            redemptions.remove(redemptionId);
        }

        void putReversal(String originalId, Redemption release) {
            reversals.put(originalId, release);
        }

        @Override
        public Optional<Redemption> find(String redemptionId) {
            return Optional.ofNullable(redemptions.get(redemptionId));
        }

        @Override
        public Optional<Redemption> findReversalOf(String redemptionId) {
            return Optional.ofNullable(reversals.get(redemptionId));
        }
    }

    private static final class InMemoryDependencyReader implements ReversalDependencyReader {
        private final Map<String, List<String>> dependents = new HashMap<>();

        void put(String redemptionId, List<String> ids) {
            dependents.put(redemptionId, ids);
        }

        @Override
        public List<String> dependentRecordIds(String redemptionId) {
            return dependents.getOrDefault(redemptionId, List.of());
        }
    }

    private static final class InMemoryStockEffectReader implements StockEffectReader {
        private final Map<String, List<StockEntry>> effects = new HashMap<>();

        void put(String redemptionId, List<StockEntry> entries) {
            effects.put(redemptionId, entries);
        }

        @Override
        public List<StockEntry> entriesFor(String redemptionId) {
            return effects.getOrDefault(redemptionId, List.of());
        }
    }
}
