package nz.ac.aut.pdc.supermarket.reversal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.WorkingCopy;

import static org.junit.Assert.assertEquals;

public class ReversalCommitValidatorTest {

    private final IdClock ids = new IdClock(
            Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneOffset.UTC));
    private final OperationContext op = new OperationContext(
            null, "11111111-1111-1111-1111-111111111111", ReversalChangeSetAssembler.OPERATION, "SALE-1");
    private final OperationContext.ActorContext actor = new OperationContext.ActorContext(
            "mgr", OperationContext.Role.MANAGER);
    private final BusinessRequest request = BusinessRequest.of(
            new DocumentReversalCommand("SALE-1", "整单冲销", "11111111-1111-1111-1111-111111111111"));
    private final ReversalCommitValidator validator = new ReversalCommitValidator();

    @Test
    public void laterStockMovementBlocksReversal() {
        WorkingCopy baseline = baseline("CONFIRMED", true);
        ChangeSet changes = assembler().assemble(
                baseline("CONFIRMED", false), "SALE-1", op, actor, request, ids);

        Result<Void> result = validator.validate(
                op, actor, request, baseline, changes, Instant.now());

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("DEPENDENCY_BLOCKED", result.code());
    }

    @Test
    public void alreadyVoidedDocumentCannotBeReversedAgain() {
        WorkingCopy baseline = baseline("VOIDED", false);
        ChangeSet changes = assembler().assemble(
                baseline("CONFIRMED", false), "SALE-1", op, actor, request, ids);

        Result<Void> result = validator.validate(
                op, actor, request, baseline, changes, Instant.now());

        assertEquals(Result.Status.REJECTED, result.status());
    }

    private ReversalChangeSetAssembler assembler() {
        return new ReversalChangeSetAssembler();
    }

    private WorkingCopy baseline(String status, boolean laterMovement) {
        List<RecordData> records = new ArrayList<>();
        records.add(RecordData.of("DOCUMENT", "SALE-1", new DocumentHeader(
                "SALE-1", "SALE", status, "22222222-2222-2222-2222-222222222222", "FP", "clerk",
                "2026-10-01T00:00:00Z", "clerk", "2026-10-01T00:00:00Z", null)));
        records.add(RecordData.of("DOC_LINE", "L1",
                new DocumentLine("L1", "SALE-1", "SALE", "00123456", 1)));
        records.add(RecordData.of("STOCK_ENTRY", "E1", new StockEntry(
                "E1", "L1", StockEntry.Position.S, -1, "EV1",
                null, null, null, 1)));
        if (laterMovement) {
            records.add(RecordData.of("DOCUMENT", "SALE-2", new DocumentHeader(
                    "SALE-2", "SALE", "CONFIRMED", "33333333-3333-3333-3333-333333333333", "FP", "clerk",
                    "2026-10-02T00:00:00Z", "clerk", "2026-10-02T00:00:00Z", null)));
            records.add(RecordData.of("DOC_LINE", "L2",
                    new DocumentLine("L2", "SALE-2", "SALE", "00123456", 1)));
            records.add(RecordData.of("STOCK_ENTRY", "E2", new StockEntry(
                    "E2", "L2", StockEntry.Position.S, -1, "EV2",
                    null, null, null, 2)));
        }
        return new WorkingCopy("wc-1", 2, records);
    }
}

