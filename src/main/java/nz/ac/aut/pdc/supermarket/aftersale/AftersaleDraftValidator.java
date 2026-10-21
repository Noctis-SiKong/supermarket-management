package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.ObjectNode;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B 负责的售后草稿/读取追溯/取消六参校验器。 */
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
        return switch (operation) {
            case AftersaleDraftOperations.CREATE -> validateCreate(op, actor, baseline, changes);
            case AftersaleDraftOperations.READ -> validateRead(op, actor, baseline, changes);
            case AftersaleDraftOperations.CANCEL -> validateCancel(op, actor, baseline, changes);
            default -> Result.rejected("INVALID_INPUT", "Unknown draft operation");
        };
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

    private Result<Void> validateRead(
            OperationContext op, OperationContext.ActorContext actor,
            WorkingCopy baseline, ChangeSet changes) {
        Result<Void> draft = requireDraft(op, baseline);
        if (!draft.hasValue()) {
            return draft;
        }
        if (!changes.patches().isEmpty() || !changes.removals().isEmpty()) {
            return Result.rejected("INVALID_INPUT", "Read trace only adds events");
        }
        List<RecordData> events = inserts(changes, "DOC_EVENT");
        if (events.size() != 1
                || !"READ_FOR_AFTERSALE".equals(events.get(0).as(DocEvent.class).action())) {
            return Result.rejected("INVALID_INPUT", "Exactly one READ_FOR_AFTERSALE event required");
        }
        return Result.value(null);
    }

    private Result<Void> validateCancel(
            OperationContext op, OperationContext.ActorContext actor,
            WorkingCopy baseline, ChangeSet changes) {
        Result<Void> draft = requireDraft(op, baseline);
        if (!draft.hasValue()) {
            return draft;
        }
        List<ChangeSet.Patch> documentPatches = changes.patches().stream()
                .filter(p -> p.table().equals("DOCUMENT") && p.id().equals(op.targetId()))
                .toList();
        if (documentPatches.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Cancel requires one DOCUMENT patch");
        }
        ObjectNode fields = (ObjectNode) JsonCodec.tree(documentPatches.get(0).changedFields());
        if (!"CANCELLED".equals(fields.path("status").asText())) {
            return Result.rejected("INVALID_INPUT", "Cancel must set CANCELLED");
        }
        List<RecordData> events = inserts(changes, "DOC_EVENT");
        if (events.size() != 1
                || !"CANCEL_AFTERSALE".equals(events.get(0).as(DocEvent.class).action())) {
            return Result.rejected("INVALID_INPUT", "Exactly one CANCEL_AFTERSALE event required");
        }
        if (changes.inserts().stream().anyMatch(r ->
                List.of("STOCK_ENTRY", "MONEY_ENTRY", "REDEMPTION").contains(r.table()))) {
            return Result.rejected("INVALID_INPUT", "Cancel must not change effects");
        }
        return Result.value(null);
    }

    private Result<Void> requireDraft(OperationContext op, WorkingCopy baseline) {
        RecordData document = baseline.find("DOCUMENT", op.targetId()).orElse(null);
        if (document == null || !"DRAFT".equals(document.as(DocumentHeader.class).status())) {
            return Result.rejected("INVALID_DRAFT", "Aftersale draft not found or not DRAFT");
        }
        return Result.value(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }
}
