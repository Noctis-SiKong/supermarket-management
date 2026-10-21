package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.ObjectNode;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B-owned six-argument validator for creation, read traces and cancellation.
 *
 * B 负责的售后草稿/读取追溯/取消六参校验器。 */
public final class AftersaleDraftValidator implements SubmissionPort.CommitValidator {

    private final String operation;

    public AftersaleDraftValidator(String operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    @Override
    public Result<Void> validate(
            OperationContext op,
            OperationContext.ActorContext actor,
            BusinessRequest request,
            WorkingCopy baseline,
            ChangeSet changes,
            Instant acceptedAt) {
        if (!operation.equals(op.operation())) {
            return Result.rejected("INVALID_INPUT", "Wrong draft operation");
        }
        try {
            if(!op.targetId().equals(request.as(java.util.Map.class).get("draftId"))) return Result.rejected("REFERENCE_INVALID","Draft request target mismatch");
            ChangeSet expected;
            if(operation.equals(AftersaleDraftOperations.CREATE)) {
                var check=validateCreate(op,actor,baseline,changes); if(!check.hasValue())return check;
                var header=new DocumentHeader(op.targetId(),"AFTERSALE","DRAFT",op.requestKey(),JsonCodec.fingerprint(op.operation(),actor.accountId(),request),actor.accountId(),null,null,null,null);
                expected=new ChangeSet(java.util.List.of(RecordData.of("DOCUMENT",op.targetId(),header),RecordData.of("AFTERSALE",op.targetId(),java.util.Map.of()),
                    RecordData.of("DOC_EVENT",op.targetId()+"-CREATE",new DocEvent(op.targetId()+"-CREATE",op.targetId(),1,"CREATE",actor.accountId(),null,"{}"))),java.util.List.of());
            } else if(operation.equals(AftersaleDraftOperations.CANCEL)) {
                var check=requireOwnedDraft(op,actor,baseline);if(!check.hasValue())return check;
                boolean traced=baseline.records("DOC_EVENT").stream().anyMatch(r -> r.data().path("documentId").asText().equals(op.targetId()) && r.data().path("action").asText().equals("READ_FOR_AFTERSALE"));
                if(!traced)return Result.rejected("DRAFT_HAS_NO_READ_TRACE","No read trace; delete the eligible draft instead");
                long version=baseline.records("DOC_EVENT").stream().filter(r -> r.data().path("documentId").asText().equals(op.targetId())).mapToLong(r -> r.data().path("version").asLong()).max().orElse(0)+1;
                expected=new ChangeSet(java.util.List.of(RecordData.of("DOC_EVENT",op.targetId()+"-CANCEL",new DocEvent(op.targetId()+"-CANCEL",op.targetId(),version,"CANCEL_AFTERSALE",actor.accountId(),null,"{}"))),
                    java.util.List.of(new ChangeSet.Patch("DOCUMENT",op.targetId(),baseline.find("DOCUMENT",op.targetId()).orElseThrow().version(),"{\"status\":\"CANCELLED\"}")));
            } else return Result.rejected("INVALID_INPUT","Use the unique OriginalSaleAccess admission");
            expected=nz.ac.aut.pdc.infrastructure.SubmissionTime.assign(baseline,expected,acceptedAt);
            return nz.ac.aut.pdc.shared.CandidateEquality.same(expected,changes)?Result.value(null):Result.rejected("REFERENCE_INVALID","Unexpected lifecycle effects");
        } catch(RuntimeException e){return Result.rejected("INVALID_INPUT","Invalid draft operation");}
    }
    public Result<Void> validateTargetCreation(OperationContext op,OperationContext.ActorContext actor,WorkingCopy wc,ChangeSet changes) {
        return validateCreate(op,actor,wc,changes);
    }

    private Result<Void> validateCreate(
            OperationContext op, OperationContext.ActorContext actor,
            WorkingCopy baseline, ChangeSet changes) {
        if (op.targetId() == null || op.targetId().isBlank()
                || baseline.find("DOCUMENT", op.targetId()).isPresent()) {
            return Result.rejected("REFERENCE_INVALID", "Draft target must be new");
        }
        List<RecordData> documents = inserts(changes, "DOCUMENT");
        if (documents.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Draft creation requires one DOCUMENT");
        }
        DocumentHeader header = documents.get(0).as(DocumentHeader.class);
        if (!"AFTERSALE".equals(header.kind()) || !"DRAFT".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "Draft must be AFTERSALE DRAFT");
        }
        if (!actor.accountId().equals(header.createdBy())) {
            return Result.rejected("FORBIDDEN", "Draft creator must be the authenticated actor");
        }
        if (inserts(changes, "AFTERSALE").size() != 1) {
            return Result.rejected("INVALID_INPUT", "Draft requires AFTERSALE extension");
        }
        return Result.value(null);
    }

    public Result<Void> requireOwnedDraft(
            OperationContext op, OperationContext.ActorContext actor, WorkingCopy baseline) {
        RecordData document = baseline.find("DOCUMENT", op.targetId()).orElse(null);
        if (document == null || !"AFTERSALE".equals(document.as(DocumentHeader.class).kind()) || baseline.find("AFTERSALE",op.targetId()).isEmpty() || !"DRAFT".equals(document.as(DocumentHeader.class).status())) {
            return Result.rejected("INVALID_DRAFT", "Aftersale draft not found or not DRAFT");
        }
        if (!actor.accountId().equals(document.as(DocumentHeader.class).createdBy())) {
            return Result.rejected("FORBIDDEN", "Only the draft creator may read or cancel it");
        }
        return Result.value(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }
}


