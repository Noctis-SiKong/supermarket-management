package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;

/** B 补货/报损候选 → A 统一 ChangeSet；只组装，不提交。 */
public final class StockChangeSetAssembler {

    public static final String REPLENISHMENT = "REPLENISHMENT";
    public static final String LOSS = "LOSS";

    public ChangeSet assembleReplenishment(
            ReplenishmentResult.Prepared prepared,
            String documentId,
            String lineId,
            OperationContext operation,
            OperationContext.ActorContext actor,
            BusinessRequest request) {
        Objects.requireNonNull(prepared, "prepared");
        StockEntry first = prepared.entries().get(0);
        int quantity = prepared.entries().stream()
                .filter(e -> e.position() == Position.S)
                .mapToInt(StockEntry::delta).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Replenishment requires S+ entry"));
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.add(RecordData.of("DOCUMENT", documentId, header(
                documentId, REPLENISHMENT, operation, actor, request)));
        out.add(RecordData.of("DOC_LINE", lineId, new DocumentLine(
                lineId, documentId, REPLENISHMENT, first.barcode(), quantity)));
        out.add(RecordData.of("DOC_EVENT", documentId + "-EVENT", new DocEvent(
                documentId + "-EVENT", documentId, 1, "CONFIRM", actor.accountId(), null, "{}")));
        addStock(out, prepared.entries(), lineId);
        return out.build();
    }

    public ChangeSet assembleLoss(
            LossResult.Prepared prepared,
            String documentId,
            String lineId,
            OperationContext operation,
            OperationContext.ActorContext actor,
            BusinessRequest request) {
        Objects.requireNonNull(prepared, "prepared");
        LossRecord record = prepared.lossRecord();
        LossRecord linked = new LossRecord(
                record.lossId(), lineId, record.barcode(), record.position(), record.quantity(),
                record.origin(), record.disposition(), record.dispositionState());
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.add(RecordData.of("DOCUMENT", documentId, header(
                documentId, LOSS, operation, actor, request)));
        out.add(RecordData.of("DOC_LINE", lineId, new DocumentLine(
                lineId, documentId, LOSS, record.barcode(), record.quantity())));
        out.add(RecordData.of("LOSS_RECORD", record.lossId(), linked));
        out.add(RecordData.of("DOC_EVENT", documentId + "-EVENT", new DocEvent(
                documentId + "-EVENT", documentId, 1, "CONFIRM", actor.accountId(), null, "{}")));
        addStock(out, prepared.entries(), lineId);
        return out.build();
    }

    private DocumentHeader header(
            String documentId, String kind, OperationContext operation,
            OperationContext.ActorContext actor, BusinessRequest request) {
        return new DocumentHeader(
                documentId, kind, "CONFIRMED", operation.requestKey(),
                JsonCodec.fingerprint(operation.operation(), actor.accountId(), request),
                actor.accountId(), null, actor.accountId(), null, null);
    }

    private void addStock(ChangeSet.Builder out, List<StockEntry> entries, String lineId) {
        for (StockEntry entry : entries) {
            out.add(RecordData.of("STOCK_ENTRY", entry.id(), new nz.ac.aut.pdc.shared.StockEntry(
                    entry.id(), lineId, nz.ac.aut.pdc.shared.StockEntry.Position.valueOf(entry.position().name()),
                    entry.delta(), entry.eventKey(), entry.receiptReviewId(), entry.reverseOfId(),
                    entry.transferKey(), 0)));
        }
    }
}
