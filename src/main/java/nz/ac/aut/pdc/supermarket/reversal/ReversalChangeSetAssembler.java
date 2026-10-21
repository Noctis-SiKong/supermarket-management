package nz.ac.aut.pdc.supermarket.reversal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** 已确认单据的整单冲销 ChangeSet：REVERSAL 单 + 反向行/库存/款项/核销释放。 */
public final class ReversalChangeSetAssembler {

    public static final String OPERATION = "REVERSAL";

    public ChangeSet assemble(
            WorkingCopy baseline,
            String targetDocumentId,
            OperationContext operation,
            OperationContext.ActorContext actor,
            BusinessRequest request,
            IdClock idClock) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actor, "actor");
        RecordData targetRecord = baseline.find("DOCUMENT", targetDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Target document missing"));
        DocumentHeader target = targetRecord.as(DocumentHeader.class);
        if (!"CONFIRMED".equals(target.status()) || "REVERSAL".equals(target.kind())) {
            throw new IllegalArgumentException("Only confirmed non-reversal documents can be reversed");
        }
        String requestKey = operation.requestKey();
        String reversalId = idClock.entityId(requestKey, "reversal");
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.add(RecordData.of("DOCUMENT", reversalId, new DocumentHeader(
                reversalId, "REVERSAL", "CONFIRMED", requestKey,
                JsonCodec.fingerprint(OPERATION, actor.accountId(), request),
                actor.accountId(), null, actor.accountId(), null, null)));
        out.add(RecordData.of("REVERSAL", reversalId, Map.of(
                "target_document_id", targetDocumentId,
                "authorized_by", actor.accountId())));
        out.add(RecordData.of("DOC_EVENT", reversalId + "-EVENT", new DocEvent(
                reversalId + "-EVENT", reversalId, 1, "REVERSAL",
                actor.accountId(), null, "{}")));

        List<RecordData> targetLines = baseline.records("DOC_LINE").stream()
                .filter(r -> targetDocumentId.equals(r.as(DocumentLine.class).documentId()))
                .toList();
        Map<String, String> reverseLineByOriginal = new LinkedHashMap<>();
        for (RecordData original : targetLines) {
            DocumentLine line = original.as(DocumentLine.class);
            String reverseLineId = idClock.entityId(requestKey, "line:" + line.lineId());
            reverseLineByOriginal.put(line.lineId(), reverseLineId);
            out.add(RecordData.of("DOC_LINE", reverseLineId, new DocumentLine(
                    reverseLineId, reversalId, "REVERSAL:" + line.lineKind(),
                    line.barcode(), line.quantity())));
        }

        Set<String> targetLineIds = reverseLineByOriginal.keySet();
        for (RecordData record : baseline.records("STOCK_ENTRY")) {
            StockEntry original = record.as(StockEntry.class);
            if (!targetLineIds.contains(original.sourceLineId()) || original.reverseOfId() != null) {
                continue;
            }
            String reverseId = idClock.entityId(requestKey, "stock:" + original.stockEntryId());
            out.add(RecordData.of("STOCK_ENTRY", reverseId, new StockEntry(
                    reverseId, reverseLineByOriginal.get(original.sourceLineId()),
                    original.position(), -original.quantityDelta(),
                    "REV:" + original.eventKey(), original.receiptReviewId(),
                    original.stockEntryId(), original.transferKey(), 0)));
        }
        for (RecordData record : baseline.records("MONEY_ENTRY")) {
            MoneyEntry original = record.as(MoneyEntry.class);
            if (!targetDocumentId.equals(original.documentId()) || original.reverseOfId() != null) {
                continue;
            }
            String reverseId = idClock.entityId(requestKey, "money:" + original.moneyEntryId());
            out.add(RecordData.of("MONEY_ENTRY", reverseId, new MoneyEntry(
                    reverseId, reversalId, original.amountSigned().negate(),
                    requestKey + ":money:" + reverseId, original.moneyEntryId())));
        }
        for (RecordData record : baseline.records("REDEMPTION")) {
            Redemption original = record.as(Redemption.class);
            if (!targetDocumentId.equals(original.aftersaleId())
                    || original.reverseOfId() != null) {
                continue;
            }
            String reverseLine = reverseLineByOriginal.get(original.sourceLineId());
            if (reverseLine == null) {
                throw new IllegalArgumentException("Redemption source line missing: " + original.redemptionId());
            }
            String releaseId = idClock.entityId(requestKey, "release:" + original.redemptionId());
            out.add(RecordData.of("REDEMPTION", releaseId, new Redemption(
                    releaseId, reverseLine, original.originalSaleLineId(), original.originalBarcode(),
                    original.aftersaleId(), original.kind(), Effect.RELEASE, original.quantity(),
                    original.amount(), requestKey + ":release:" + releaseId,
                    original.redemptionId(), original.opened(), original.qualityProblem(),
                    original.reasonText())));
        }
        out.patch(new ChangeSet.Patch("DOCUMENT", targetDocumentId, targetRecord.version(),
                JsonCodec.write(Map.of("status", "VOIDED"))));
        return out.build();
    }
}
