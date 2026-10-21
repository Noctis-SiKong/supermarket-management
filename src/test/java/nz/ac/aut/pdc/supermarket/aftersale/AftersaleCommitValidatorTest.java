package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.DraftDeletion;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AftersaleCommitValidatorTest {

    private final AftersaleCommitValidator validator =
            new AftersaleCommitValidator(new SharedStockAdapter());
    private final OperationContext op = new OperationContext(
            null, "REQ-1", AftersaleChangeSetAssembler.OPERATION, "AF-1");
    private final OperationContext.ActorContext actor =
            new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK);
        private final WorkingCopy baseline = new WorkingCopy("wc-1", 1, List.of(
            RecordData.of("DOCUMENT", "S-1", new DocumentHeader(
                    "S-1", "SALE", "CONFIRMED", "REQ-0", "FP", "acc-1",
                    "2026-10-01T00:00:00Z", "acc-1", "2026-10-01T00:00:00Z", null)),
            RecordData.of("DOC_LINE", "SL-1",
                    new DocumentLine("SL-1", "S-1", "SALE", "A", 1))));
    private final Instant acceptedAt = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void validAftersalePasses() {
        Result<Void> result = validator.validate(
                op, actor, request(), baseline, changes(true), acceptedAt);

        assertEquals(Result.Status.VALUE, result.status());
    }

    @Test
    public void missingRedemptionIsRejected() {
        Result<Void> result = validator.validate(
                op, actor, request(), baseline, changes(false), acceptedAt);

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("INVALID_INPUT", result.code());
    }

    @Test
    public void missingOtherReasonTextIsRejected() {
        ChangeSet changes = new ChangeSet.Builder()
                .add(RecordData.of("DOCUMENT", "AF-1", header()))
                .add(RecordData.of("REDEMPTION", "R-1", redemption(" ")))
                .build();

        Result<Void> result = validator.validate(op, actor, request(), baseline, changes, acceptedAt);

        assertEquals(Result.Status.REJECTED, result.status());
    }

    @Test
    public void missingOriginalSaleLineIsRejected() {
        Result<Void> result = validator.validate(
                op, actor, request(), new WorkingCopy("wc-empty", 1, List.of()),
                changes(true), acceptedAt);

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("REFERENCE_INVALID", result.code());
    }

    @Test
    public void sameOriginalLineMultipleRedemptionsAreKept() {
        BusinessRequest twoLines = BusinessRequest.of(new AftersaleCommand(
                "REQ-1", null, List.of(
                        new AftersaleCommandLine(
                                "SL-1", RedemptionKind.RETURN, 1, false, false,
                                AftersaleReasonKind.OTHER, "其他原因", null, 0, false),
                        new AftersaleCommandLine(
                                "SL-1", RedemptionKind.RETURN, 1, false, false,
                                AftersaleReasonKind.OTHER, "其他原因", null, 0, false))));
        ChangeSet changes = new ChangeSet.Builder()
                .add(RecordData.of("DOCUMENT", "AF-1", header()))
                .add(RecordData.of("REDEMPTION", "R-1", redemption("R-1", "其他原因")))
                .add(RecordData.of("REDEMPTION", "R-2", redemption("R-2", "其他原因")))
                .build();

        Result<Void> result = validator.validate(
                op, actor, twoLines, baseline, changes, acceptedAt);

        assertEquals(Result.Status.VALUE, result.status());
    }

    @Test
    public void registryContainsAftersaleAndDraftDeletion() {
        Map<String, ?> validators = BCommitValidators.create(new SharedStockAdapter());

        assertTrue(validators.containsKey(AftersaleChangeSetAssembler.OPERATION));
        assertTrue(validators.containsKey(DraftDeletion.OPERATION));
    }

    private BusinessRequest request() {
        return BusinessRequest.of(new AftersaleCommand(
                "REQ-1", null, List.of(new AftersaleCommandLine(
                        "SL-1", RedemptionKind.RETURN, 1, false, false,
                        AftersaleReasonKind.OTHER, "其他原因", null, 0, false))));
    }

    private ChangeSet changes(boolean withRedemption) {
        ChangeSet.Builder builder = new ChangeSet.Builder()
                .add(RecordData.of("DOCUMENT", "AF-1", header()));
        if (withRedemption) {
            builder.add(RecordData.of("REDEMPTION", "R-1", redemption("其他原因")));
        }
        return builder.build();
    }

    private DocumentHeader header() {
        return new DocumentHeader(
                "AF-1", "AFTERSALE", "CONFIRMED", "REQ-1", "FP",
                "acc-1", null, "acc-1", null, null);
    }

    private Redemption redemption(String reasonText) {
        return redemption("R-1", reasonText);
    }

    private Redemption redemption(String redemptionId, String reasonText) {
        return new Redemption(
                redemptionId, redemptionId, "SL-1", "A", "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-1", null, false, false, reasonText);
    }
}




