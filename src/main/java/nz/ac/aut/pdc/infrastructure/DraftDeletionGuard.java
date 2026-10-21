package nz.ac.aut.pdc.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import java.util.*;

/** Structural red lines only. The registered B validator owns eligibility, permissions and effects. */
final class DraftDeletionGuard {
    private DraftDeletionGuard() {}
    static void check(OperationContext op, BusinessRequest request, WorkingCopy baseline, ChangeSet changes) {
        DraftDeletion target = request.as(DraftDeletion.class);
        require(Objects.equals(op.targetId(),target.documentId()),"REFERENCE_INVALID");
        RecordData document = baseline.find("DOCUMENT",target.documentId()).orElseThrow(() -> new IllegalArgumentException("REFERENCE_INVALID"));
        require(target.expectedVersion() == 0 || document.version() == target.expectedVersion(),"STALE_WORKING_COPY");
        require(document.data().path("status").asText().equals("DRAFT"),"INVALID_INPUT");
        require(!document.data().hasNonNull("confirmedAt") && !document.data().hasNonNull("confirmedBy"),"DRAFT_HAS_EFFECTS");
        require(changes.inserts().isEmpty() && changes.patches().isEmpty(),"INVALID_INPUT");
        Set<String> group = new HashSet<>(List.of(document.key()));
        baseline.find("AFTERSALE",target.documentId()).ifPresent(r -> group.add(r.key()));
        Set<String> lines = new HashSet<>();
        for (RecordData r : baseline.records("DOC_LINE")) if (r.data().path("documentId").asText().equals(target.documentId())) {
            group.add(r.key()); lines.add(r.id());
        }
        for (RecordData r : baseline.records("DOC_EVENT")) if (r.data().path("documentId").asText().equals(target.documentId())) {
            require(!r.data().path("action").asText().equals("READ_FOR_AFTERSALE"),"DRAFT_HAS_READ_TRACE");
            group.add(r.key());
        }
        // A replacement draft extension may exist, but a posted ledger/loss/review is never deletable.
        for (RecordData r : baseline.records("REPLACEMENT")) if (lines.contains(r.id())) group.add(r.key());
        for (RecordData r : baseline.allRecords()) if (Set.of("STOCK_ENTRY","MONEY_ENTRY","REDEMPTION","LOSS_RECORD","RECEIPT_REVIEW","REVERSAL","LOSS_CORRECTION").contains(r.table())) {
            require(!r.data().path("documentId").asText().equals(target.documentId())
                && !r.data().path("aftersaleId").asText().equals(target.documentId())
                && !r.data().path("targetDocumentId").asText().equals(target.documentId())
                && !lines.contains(r.data().path("sourceLineId").asText()),"DRAFT_HAS_EFFECTS");
        }
        Set<String> supplied = new HashSet<>();
        for (ChangeSet.Removal r : changes.removals()) {
            require(supplied.add(r.key()),"EVENT_CONFLICT");
            RecordData old = baseline.find(r.table(),r.id()).orElseThrow(() -> new IllegalArgumentException("REFERENCE_INVALID"));
            require(old.version() == r.expectedVersion(),"STALE_WORKING_COPY");
        }
        require(supplied.equals(group),"INVALID_DELETION_GROUP");
        WorkingCopy after = baseline.overlay(changes);
        Set<String> removedReferences = new HashSet<>(supplied);
        removedReferences.add("AFTERSALE:"+target.documentId());
        for (RecordData r : after.allRecords()) checkReferences(r.data(),removedReferences);
    }
    /** All existing ERD reference fields; recurse into structured event detail snapshots as well. */
    private static final Map<String,String> REFERENCES = Map.ofEntries(
        Map.entry("documentId","DOCUMENT"), Map.entry("targetDocumentId","DOCUMENT"),
        Map.entry("aftersaleId","AFTERSALE"), Map.entry("targetAftersaleId","AFTERSALE"), Map.entry("aftersaleRef","DOCUMENT"),
        Map.entry("sourceLineId","DOC_LINE"), Map.entry("lineId","DOC_LINE"),
        Map.entry("receiptId","RECEIPT"), Map.entry("receiptReviewId","RECEIPT_REVIEW"),
        Map.entry("originalSaleId","SALE"), Map.entry("originalSaleLineId","SALE_LINE"),
        Map.entry("qualityRedemptionId","REDEMPTION"), Map.entry("lossId","LOSS_RECORD"));
    private static void checkReferences(JsonNode node, Set<String> removed) {
        if (node.isArray()) { node.forEach(n -> checkReferences(n,removed)); return; }
        if (!node.isObject()) return;
        for (var field : node.properties()) {
            String table = REFERENCES.get(field.getKey()); JsonNode value = field.getValue();
            if (table != null && value.isTextual()) require(!removed.contains(table+":"+value.asText()),"REFERENCE_INVALID");
            if (Set.of("detailSnapshot","beforeAfterSnapshot").contains(field.getKey()) && value.isTextual()) {
                String text = value.asText().stripLeading();
                if (text.startsWith("{") || text.startsWith("[")) checkReferences(JsonCodec.tree(text),removed);
            } else checkReferences(value,removed);
        }
    }
    private static void require(boolean condition,String code) { if (!condition) throw new IllegalArgumentException(code); }
}
