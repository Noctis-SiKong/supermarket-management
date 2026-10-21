package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.DraftDeletion;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;

import static org.junit.Assert.assertEquals;

public class AftersaleDraftDeletionValidatorTest {

    private final AftersaleDraftDeletionValidator validator = new AftersaleDraftDeletionValidator();
    private final OperationContext op = new OperationContext(
            null, "REQ-1", DraftDeletion.OPERATION, "D-1");
    private final OperationContext.ActorContext clerk =
            new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK);
    private final Instant acceptedAt = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void deletesCompleteDraftGroupForCreator() {
        WorkingCopy baseline = baseline(false, false);

        Result<Void> result = validator.validate(
                op, clerk, request(), baseline, deletions(baseline), acceptedAt);

        assertEquals(Result.Status.VALUE, result.status());
    }

    @Test
    public void nonCreatorClerkIsForbidden() {
        OperationContext.ActorContext other =
                new OperationContext.ActorContext("acc-2", OperationContext.Role.CLERK);
        WorkingCopy baseline = baseline(false, false);

        Result<Void> result = validator.validate(
                op, other, request(), baseline, deletions(baseline), acceptedAt);

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("FORBIDDEN", result.code());
    }

    @Test
    public void readTraceBlocksDeletion() {
        WorkingCopy baseline = baseline(true, false);

        Result<Void> result = validator.validate(
                op, clerk, request(), baseline, deletions(baseline), acceptedAt);

        assertEquals("DRAFT_HAS_READ_TRACE", result.code());
    }

    @Test
    public void postedEffectsBlockDeletion() {
        WorkingCopy baseline = baseline(false, true);

        Result<Void> result = validator.validate(
                op, clerk, request(), baseline, deletions(baseline), acceptedAt);

        assertEquals("DRAFT_HAS_EFFECTS", result.code());
    }

    @Test
    public void emptyGroupIsRejected() {
        WorkingCopy baseline = baseline(false, false);

        Result<Void> result = validator.validate(
                op, clerk, request(), baseline, ChangeSet.empty(), acceptedAt);

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("INVALID_DELETION_GROUP", result.code());
    }

    private BusinessRequest request() {
        return BusinessRequest.of(new DraftDeletion("D-1", 1));
    }

    private WorkingCopy baseline(boolean readTrace, boolean postedEffect) {
        var records = new ArrayList<RecordData>();
        records.add(RecordData.of("DOCUMENT", "D-1", new DocumentHeader(
                "D-1", "AFTERSALE", "DRAFT", "REQ-0", "FP", "acc-1",
                "2026-10-21T03:00:00Z", null, null, null)));
        records.add(RecordData.of("AFTERSALE", "D-1", Map.of("status", "DRAFT")));
        records.add(RecordData.of("DOC_LINE", "L-1",
                new DocumentLine("L-1", "D-1", "AFTERSALE", "A", 1)));
        records.add(RecordData.of("DOC_EVENT", "E-1", new DocEvent(
                "E-1", "D-1", 1, readTrace ? "READ_FOR_AFTERSALE" : "CREATE",
                "acc-1", "2026-10-21T03:00:00Z", "{}")));
        if (postedEffect) {
            records.add(RecordData.of("REDEMPTION", "R-1", Map.of("aftersaleId", "D-1")));
        }
        return new WorkingCopy("wc-1", 1, records);
    }

    private ChangeSet deletions(WorkingCopy baseline) {
        ChangeSet.Builder builder = new ChangeSet.Builder();
        for (RecordData record : baseline.allRecords()) {
            builder.remove(new ChangeSet.Removal(record.table(), record.id(), record.version()));
        }
        return builder.build();
    }
}

