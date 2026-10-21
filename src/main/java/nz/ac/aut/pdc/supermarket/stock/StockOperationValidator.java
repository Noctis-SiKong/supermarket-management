package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B 补货/报损六参校验器：核对单据、行、库存腿与报损事实。 */
public final class StockOperationValidator implements SubmissionPort.CommitValidator {

    private final String operation;

    public StockOperationValidator(String operation) {
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
            return Result.rejected("INVALID_INPUT", "Wrong stock operation");
        }
        List<RecordData> documents = inserts(changes, "DOCUMENT");
        if (documents.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one document required");
        }
        DocumentHeader header = documents.get(0).as(DocumentHeader.class);
        if (!operation.equals(header.kind()) || !"CONFIRMED".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "Document kind/status mismatch");
        }
        if (!header.documentId().equals(op.targetId())) {
            return Result.rejected("REFERENCE_INVALID", "Operation target mismatch");
        }
        List<RecordData> lines = inserts(changes, "DOC_LINE");
        if (lines.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one DOC_LINE required");
        }
        DocumentLine line = lines.get(0).as(DocumentLine.class);
        if (!line.documentId().equals(header.documentId())) {
            return Result.rejected("REFERENCE_INVALID", "Line document mismatch");
        }
        List<RecordData> stocks = inserts(changes, "STOCK_ENTRY");
        if (operation.equals(StockChangeSetAssembler.REPLENISHMENT)) {
            if (stocks.size() != 2) {
                return Result.rejected("INVALID_INPUT", "Replenishment requires two stock legs");
            }
            Set<String> transferKeys = new HashSet<>();
            int sum = 0;
            for (RecordData record : stocks) {
                var entry = record.as(nz.ac.aut.pdc.shared.StockEntry.class);
                if (!line.lineId().equals(entry.sourceLineId())) {
                    return Result.rejected("REFERENCE_INVALID", "Stock source line mismatch");
                }
                sum += entry.quantityDelta();
                transferKeys.add(entry.transferKey());
            }
            if (sum != 0 || transferKeys.size() != 1 || transferKeys.contains(null)) {
                return Result.rejected("INVALID_INPUT", "Replenishment legs must balance and share transferKey");
            }
        } else {
            if (stocks.size() != 1
                    || stocks.get(0).as(nz.ac.aut.pdc.shared.StockEntry.class).quantityDelta() >= 0) {
                return Result.rejected("INVALID_INPUT", "Loss requires one negative stock leg");
            }
            List<RecordData> losses = inserts(changes, "LOSS_RECORD");
            if (losses.size() != 1) {
                return Result.rejected("INVALID_INPUT", "Loss requires one LOSS_RECORD");
            }
            LossRecord record = losses.get(0).as(LossRecord.class);
            if (!line.lineId().equals(record.sourceLineId())
                    || record.quantity() != line.quantity()) {
                return Result.rejected("REFERENCE_INVALID", "Loss record does not match line");
            }
        }
        if (changes.inserts().stream().anyMatch(r ->
                List.of("MONEY_ENTRY", "REDEMPTION", "REPLACEMENT").contains(r.table()))) {
            return Result.rejected("INVALID_INPUT", "Stock operation must not carry aftersale facts");
        }
        if (inserts(changes, "DOC_EVENT").size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one DOC_EVENT required");
        }
        return Result.value(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }
}
