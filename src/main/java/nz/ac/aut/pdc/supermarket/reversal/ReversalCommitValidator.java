package nz.ac.aut.pdc.supermarket.reversal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** 整单冲销六参校验器：权限、目标状态、完整反向关系与原子组。 */
public final class ReversalCommitValidator implements SubmissionPort.CommitValidator {

    private static final Set<String> INSERT_TABLES = Set.of(
            "DOCUMENT", "REVERSAL", "DOC_LINE", "DOC_EVENT",
            "STOCK_ENTRY", "MONEY_ENTRY", "REDEMPTION");

    @Override
    public Result<Void> validate(
            OperationContext op,
            OperationContext.ActorContext actor,
            BusinessRequest request,
            WorkingCopy baseline,
            ChangeSet changes,
            Instant acceptedAt) {
        if (actor == null || (actor.role() != OperationContext.Role.MANAGER
                && actor.role() != OperationContext.Role.ADMIN)) {
            return Result.rejected("FORBIDDEN", "Reversal requires manager/admin");
        }
        final DocumentReversalCommand command;
        try {
            command = request.as(DocumentReversalCommand.class);
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Invalid reversal request");
        }
        if (!command.targetDocumentId().equals(op.targetId())) {
            return Result.rejected("REFERENCE_INVALID", "Operation target mismatch");
        }
        RecordData target = baseline.find("DOCUMENT", command.targetDocumentId()).orElse(null);
        if (target == null || !"CONFIRMED".equals(target.as(DocumentHeader.class).status())) {
            return Result.rejected("REFERENCE_INVALID", "Target is not a confirmed document");
        }
        if (!changes.removals().isEmpty()) {
            return Result.rejected("INVALID_INPUT", "Reversal must not remove records");
        }
        String dependency = dependencyBlocker(baseline, command.targetDocumentId());
        if (dependency != null) {
            return Result.rejected("DEPENDENCY_BLOCKED", dependency);
        }
        List<RecordData> documents = inserts(changes, "DOCUMENT");
        if (documents.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one reversal document required");
        }
        DocumentHeader header = documents.get(0).as(DocumentHeader.class);
        if (!"REVERSAL".equals(header.kind()) || !"CONFIRMED".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "Reversal document kind/status mismatch");
        }
        if (!actor.accountId().equals(header.confirmedBy())) {
            return Result.rejected("FORBIDDEN", "Reversal actor mismatch");
        }
        List<RecordData> reversals = inserts(changes, "REVERSAL");
        if (reversals.size() != 1
                || !command.targetDocumentId().equals(
                        reversals.get(0).data().path("target_document_id").asText())) {
            return Result.rejected("REFERENCE_INVALID", "REVERSAL target mismatch");
        }
        ChangeSet.Patch targetPatch = changes.patches().stream()
                .filter(p -> p.table().equals("DOCUMENT") && p.id().equals(command.targetDocumentId()))
                .findFirst().orElse(null);
        if (targetPatch == null
                || !"VOIDED".equals(((ObjectNode) JsonCodec.tree(targetPatch.changedFields()))
                        .path("status").asText())) {
            return Result.rejected("INVALID_INPUT", "Target must be patched to VOIDED");
        }
        if (changes.inserts().stream().anyMatch(r -> !INSERT_TABLES.contains(r.table()))) {
            return Result.rejected("INVALID_INPUT", "Reversal carries unrelated records");
        }
        for (RecordData record : inserts(changes, "STOCK_ENTRY")) {
            StockEntry reverse = record.as(StockEntry.class);
            if (reverse.reverseOfId() == null) {
                continue;
            }
            StockEntry original = baseline.find("STOCK_ENTRY", reverse.reverseOfId())
                    .orElseThrow(() -> new IllegalArgumentException("Missing original stock"))
                    .as(StockEntry.class);
            if (original.quantityDelta() != -reverse.quantityDelta()
                    || original.position() != reverse.position()) {
                return Result.rejected("REFERENCE_INVALID", "Stock reversal does not match original");
            }
        }
        for (RecordData record : inserts(changes, "MONEY_ENTRY")) {
            MoneyEntry reverse = record.as(MoneyEntry.class);
            if (reverse.reverseOfId() == null) {
                return Result.rejected("INVALID_INPUT", "Reversal money needs reverseOfId");
            }
            MoneyEntry original = baseline.find("MONEY_ENTRY", reverse.reverseOfId())
                    .orElseThrow(() -> new IllegalArgumentException("Missing original money"))
                    .as(MoneyEntry.class);
            if (original.amountSigned().add(reverse.amountSigned()).signum() != 0) {
                return Result.rejected("REFERENCE_INVALID", "Money reversal amount mismatch");
            }
        }
        for (RecordData record : inserts(changes, "REDEMPTION")) {
            Redemption reverse = record.as(Redemption.class);
            if (reverse.effect() != Effect.RELEASE || reverse.reverseOfId() == null) {
                return Result.rejected("INVALID_INPUT", "Reversal redemption must be RELEASE");
            }
            Redemption original = baseline.find("REDEMPTION", reverse.reverseOfId())
                    .orElseThrow(() -> new IllegalArgumentException("Missing original redemption"))
                    .as(Redemption.class);
            if (original.quantity() != reverse.quantity()
                    || original.amount().compareTo(reverse.amount()) != 0) {
                return Result.rejected("REFERENCE_INVALID", "Redemption release does not match original");
            }
        }
        return Result.value(null);
    }

    private String barcodeOf(WorkingCopy baseline, String sourceLineId) {
        return baseline.find("DOC_LINE", sourceLineId)
                .map(r -> r.as(DocumentLine.class).barcode())
                .orElse(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }

    private String dependencyBlocker(WorkingCopy baseline, String targetDocumentId) {
        Set<String> targetLines = baseline.records("DOC_LINE").stream()
                .filter(r -> targetDocumentId.equals(r.as(DocumentLine.class).documentId()))
                .map(RecordData::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!targetLines.isEmpty()) {
            for (RecordData record : baseline.records("REDEMPTION")) {
                Redemption redemption = record.as(Redemption.class);
                if (targetLines.contains(redemption.originalSaleLineId())
                        && redemption.reverseOfId() == null) {
                    return "Later aftersale redemption depends on original sale";
                }
            }
            for (RecordData record : baseline.records("STOCK_ENTRY")) {
                StockEntry entry = record.as(StockEntry.class);
                if (!targetLines.contains(entry.sourceLineId()) || entry.quantityDelta() >= 0) {
                    continue;
                }
                String entryBarcode = barcodeOf(baseline, entry.sourceLineId());
                for (RecordData other : baseline.records("STOCK_ENTRY")) {
                    StockEntry later = other.as(StockEntry.class);
                    if (other.id().equals(record.id())
                            || later.quantityDelta() >= 0
                            || later.reverseOfId() != null
                            || !Objects.equals(barcodeOf(baseline, later.sourceLineId()), entryBarcode)
                            || later.position() != entry.position()
                            || later.commitOrdinal() <= entry.commitOrdinal()) {
                        continue;
                    }
                    return "Later effective stock movement depends on original stock entry";
                }
            }
        }
        return null;
    }
}
