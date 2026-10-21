package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DraftDeletion;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B validates permissions, business deletability, read traces and candidate shape; A guards versions, complete deletion groups and reference closure. Six arguments follow v1.0.4.
 *
 *
 * B 主责的售后草稿整组删除校验器（AFTERSALE_DRAFT_DELETE）。
 *
 * <p>A 的 DraftDeletionGuard 负责 DRAFT/版本/闭合组/引用红线；本类补 B 侧
 * 角色、草稿可删性、读取追溯与变更组形状检查。六参签名必须与 v1.0.4 一致。</p>
 */
public final class AftersaleDraftDeletionValidator implements SubmissionPort.CommitValidator {

    private static final Set<String> ALLOWED_REMOVAL_TABLES =
            Set.of("DOCUMENT", "AFTERSALE", "DOC_LINE", "DOC_EVENT", "REPLACEMENT");
    private static final Set<String> POSTED_TABLES = Set.of(
            "STOCK_ENTRY", "MONEY_ENTRY", "REDEMPTION", "LOSS_RECORD",
            "RECEIPT_REVIEW", "REVERSAL", "LOSS_CORRECTION");

    @Override
    public Result<Void> validate(
            OperationContext op,
            OperationContext.ActorContext actor,
            BusinessRequest request,
            WorkingCopy baseline,
            ChangeSet changes,
            Instant acceptedAt) {
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(changes, "changes");

        final DraftDeletion target;
        try {
            target = request.as(DraftDeletion.class);
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Invalid draft deletion request");
        }
        if (!Objects.equals(op.targetId(), target.documentId())) {
            return Result.rejected("REFERENCE_INVALID", "Operation target does not match deletion target");
        }
        RecordData documentRecord = baseline.find("DOCUMENT", target.documentId()).orElse(null);
        if (documentRecord == null) {
            return Result.rejected("REFERENCE_INVALID", "Draft document not found");
        }
        DocumentHeader header = documentRecord.as(DocumentHeader.class);
        if(!"AFTERSALE".equals(header.kind()) || baseline.find("AFTERSALE",target.documentId()).isEmpty()) return Result.rejected("REFERENCE_INVALID","Target is not an aftersale draft");
        if (!"DRAFT".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "Only draft documents can be deleted");
        }
        if (header.confirmedAt() != null || header.confirmedBy() != null) {
            return Result.rejected("DRAFT_HAS_EFFECTS", "Confirmed document cannot be deleted");
        }
        if (actor.role() == OperationContext.Role.CLERK
                && !actor.accountId().equals(header.createdBy())) {
            return Result.rejected("FORBIDDEN", "Clerk can delete only own draft");
        }
        if (hasReadTrace(baseline, target.documentId())) {
            return Result.rejected("DRAFT_HAS_READ_TRACE", "Draft has IF-11 read trace; cancel instead");
        }
        if (hasPostedEffects(baseline, target.documentId())) {
            return Result.rejected("DRAFT_HAS_EFFECTS", "Draft has posted effects");
        }
        if (!validRemovalGroup(baseline, target, changes)) {
            return Result.rejected("INVALID_DELETION_GROUP", "Deletion group is incomplete or unexpected");
        }
        return Result.value(null);
    }

    private boolean hasReadTrace(WorkingCopy baseline, String documentId) {
        for (RecordData record : baseline.records("DOC_EVENT")) {
            DocEvent event = record.as(DocEvent.class);
            if (documentId.equals(event.documentId()) && "READ_FOR_AFTERSALE".equals(event.action())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPostedEffects(WorkingCopy baseline, String documentId) {
        for (RecordData record : baseline.allRecords()) {
            if (!POSTED_TABLES.contains(record.table())) {
                continue;
            }
            var data = record.data();
            if (documentId.equals(data.path("documentId").asText())
                    || documentId.equals(data.path("aftersaleId").asText())
                    || documentId.equals(data.path("targetDocumentId").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean validRemovalGroup(
            WorkingCopy baseline, DraftDeletion target, ChangeSet changes) {
        if (changes.inserts().size() != 0 || changes.patches().size() != 0
                || changes.removals().isEmpty()) {
            return false;
        }
        boolean documentRemoved = false;
        for (ChangeSet.Removal removal : changes.removals()) {
            if (!ALLOWED_REMOVAL_TABLES.contains(removal.table())) {
                return false;
            }
            RecordData existing = baseline.find(removal.table(), removal.id()).orElse(null);
            if (existing == null || existing.version() != removal.expectedVersion()) {
                return false;
            }
            if (removal.table().equals("DOCUMENT") && removal.id().equals(target.documentId())) {
                documentRemoved = true;
            }
        }
        return documentRemoved;
    }
}

