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

/** AC-52/83 block dependent warehouse deductions; AC-84 does not block unrelated later positive receipts.
  */
public class ReversalReceiptDependencyTest {

    private final IdClock ids = new IdClock(
            Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneOffset.UTC));
    private final OperationContext.ActorContext actor = new OperationContext.ActorContext(
            "mgr", OperationContext.Role.MANAGER);
    private final ReversalCommitValidator validator = new ReversalCommitValidator();
    private final ReversalChangeSetAssembler assembler = new ReversalChangeSetAssembler();

    @Test
    public void ac52AndAc83LaterWarehouseOutflowBlocksOldReceiptReversal() {
        OperationContext op = operation("11111111-1111-1111-1111-111111111111");
        BusinessRequest request = request("11111111-1111-1111-1111-111111111111");
        WorkingCopy baseline = warehouseBaseline(true);
        ChangeSet changes = assembler.assemble(
                warehouseBaseline(false), "RCV-1", op, actor, request, ids);

        Instant accepted=ids.now();
        changes=nz.ac.aut.pdc.infrastructure.SubmissionTime.assign(baseline,changes,accepted);
        Result<Void> result = validator.validate(
                op, actor, request, baseline, changes, accepted);

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("DEPENDENCY_BLOCKED", result.code());
    }

    @Test
    public void ac84LaterPositiveReceiptDoesNotBlock() {
        OperationContext op = operation("22222222-2222-2222-2222-222222222222");
        BusinessRequest request = request("22222222-2222-2222-2222-222222222222");
        WorkingCopy baseline = warehouseBaseline(false);
        ChangeSet changes = assembler.assemble(
                warehouseBaseline(false), "RCV-1", op, actor, request, ids);

        Instant accepted=ids.now();
        changes=nz.ac.aut.pdc.infrastructure.SubmissionTime.assign(baseline,changes,accepted);
        Result<Void> result = validator.validate(
                op, actor, request, baseline, changes, accepted);

        assertEquals(result.code(), Result.Status.VALUE, result.status());
    }

    private OperationContext operation(String key) {
        return new OperationContext(null, key, ReversalChangeSetAssembler.OPERATION, "RCV-1");
    }

    private BusinessRequest request(String key) {
        return BusinessRequest.of(new DocumentReversalCommand("RCV-1", "整单冲销", key));
    }

    private WorkingCopy warehouseBaseline(boolean laterOutflow) {
        List<RecordData> records = new ArrayList<>();
        records.add(RecordData.of("DOCUMENT", "RCV-1", new DocumentHeader(
                "RCV-1", "RECEIPT", "APPROVED", "44444444-4444-4444-4444-444444444444",
                "FP", "clerk", "2026-10-01T00:00:00Z", "clerk", "2026-10-01T00:00:00Z", null)));
        records.add(RecordData.of("DOC_LINE", "L1",
                new DocumentLine("L1", "RCV-1", "RECEIPT", "00123456", 10)));
        records.add(RecordData.of("STOCK_ENTRY", "E1", new StockEntry(
                "E1", "L1", StockEntry.Position.W, 10, "EV1", null, null, null, 1)));
        records.add(RecordData.of("DOCUMENT", "RCV-2", new DocumentHeader(
                "RCV-2", "RECEIPT", "APPROVED", "55555555-5555-5555-5555-555555555555",
                "FP", "clerk", "2026-10-02T00:00:00Z", "clerk", "2026-10-02T00:00:00Z", null)));
        records.add(RecordData.of("DOC_LINE", "L2",
                new DocumentLine("L2", "RCV-2", "RECEIPT", "00123456", laterOutflow ? 3 : 5)));
        records.add(RecordData.of("STOCK_ENTRY", "E2", new StockEntry(
                "E2", "L2", StockEntry.Position.W, laterOutflow ? -3 : 5,
                "EV2", null, null, null, 2)));
        records.add(RecordData.of("RECEIPT","RCV-1",new nz.ac.aut.pdc.supermarket.app.AOperations.Receipt("RCV-1","APPROVED")));
        return new WorkingCopy("wc-1", 2, records);
    }
}
