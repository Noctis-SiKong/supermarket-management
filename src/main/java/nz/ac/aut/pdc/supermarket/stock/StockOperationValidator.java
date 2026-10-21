package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/** B 补货/报损六参校验器：用原始请求和身份复核，拒绝夹带变更。 */
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
        if (!allowedRole(actor)) {
            return Result.rejected("FORBIDDEN", "Role is not allowed for " + operation);
        }
        Requested requested;
        try {
            Map<?, ?> body = request.as(Map.class);
            requested = new Requested(
                    Objects.toString(body.get("barcode"), null),
                    ((Number) body.get("quantity")).intValue(),
                    body.get("position") == null ? null : Position.valueOf(body.get("position").toString()));
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Invalid original stock request");
        }
        if (requested.barcode() == null || requested.barcode().isBlank()
                || requested.quantity() < 1 || requested.quantity() > StockValidator.MAX_QUANTITY) {
            return Result.rejected("INVALID_INPUT", "Invalid requested barcode/quantity");
        }
        if (changes.patches().stream().anyMatch(p -> true) || !changes.removals().isEmpty()) {
            return Result.rejected("INVALID_INPUT", "Stock operations cannot patch or remove records");
        }
        Set<String> allowedTables = operation.equals(StockChangeSetAssembler.LOSS)
                ? Set.of("DOCUMENT", "DOC_LINE", "DOC_EVENT", "STOCK_ENTRY", "LOSS_RECORD")
                : Set.of("DOCUMENT", "DOC_LINE", "DOC_EVENT", "STOCK_ENTRY");
        if (changes.inserts().stream().anyMatch(r -> !allowedTables.contains(r.table()))) {
            return Result.rejected("INVALID_INPUT", "Stock operation carries unrelated records");
        }
        List<RecordData> documents = inserts(changes, "DOCUMENT");
        if (documents.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one document required");
        }
        DocumentHeader header = documents.get(0).as(DocumentHeader.class);
        if (!operation.equals(header.kind()) || !"CONFIRMED".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "Document kind/status mismatch");
        }
        if (!header.documentId().equals(op.targetId())
                || !actor.accountId().equals(header.confirmedBy())
                || !actor.accountId().equals(header.createdBy())) {
            return Result.rejected("REFERENCE_INVALID", "Document target/actor mismatch");
        }
        List<RecordData> lines = inserts(changes, "DOC_LINE");
        if (lines.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one DOC_LINE required");
        }
        DocumentLine line = lines.get(0).as(DocumentLine.class);
        if (!line.documentId().equals(header.documentId())
                || !line.barcode().equals(requested.barcode())
                || line.quantity() != requested.quantity()) {
            return Result.rejected("REFERENCE_INVALID", "Line does not implement original request");
        }
        List<RecordData> stocks = inserts(changes, "STOCK_ENTRY");
        if (operation.equals(StockChangeSetAssembler.REPLENISHMENT)) {
            if (stocks.size() != 2) {
                return Result.rejected("INVALID_INPUT", "Replenishment requires two stock legs");
            }
            Set<String> transferKeys = new HashSet<>();
            boolean hasW = false;
            boolean hasS = false;
            for (RecordData record : stocks) {
                var entry = record.as(nz.ac.aut.pdc.shared.StockEntry.class);
                if (!line.lineId().equals(entry.sourceLineId())) {
                    return Result.rejected("REFERENCE_INVALID", "Stock source line mismatch");
                }
                transferKeys.add(entry.transferKey());
                if (entry.position() == nz.ac.aut.pdc.shared.StockEntry.Position.W
                        && entry.quantityDelta() == -requested.quantity()) {
                    hasW = true;
                } else if (entry.position() == nz.ac.aut.pdc.shared.StockEntry.Position.S
                        && entry.quantityDelta() == requested.quantity()) {
                    hasS = true;
                }
            }
            if (!hasW || !hasS || transferKeys.size() != 1 || transferKeys.contains(null)) {
                return Result.rejected("INVALID_INPUT", "Replenishment legs do not implement request");
            }
        } else {
            if (stocks.size() != 1) {
                return Result.rejected("INVALID_INPUT", "Loss requires one stock leg");
            }
            var entry = stocks.get(0).as(nz.ac.aut.pdc.shared.StockEntry.class);
            if (requested.position() == null
                    || !line.lineId().equals(entry.sourceLineId())
                    || !requested.position().name().equals(entry.position().name())
                    || entry.quantityDelta() != -requested.quantity()) {
                return Result.rejected("REFERENCE_INVALID", "Loss stock leg does not implement request");
            }
            List<RecordData> losses = inserts(changes, "LOSS_RECORD");
            if (losses.size() != 1) {
                return Result.rejected("INVALID_INPUT", "Loss requires one LOSS_RECORD");
            }
            LossRecord record = losses.get(0).as(LossRecord.class);
            if (!line.lineId().equals(record.sourceLineId())
                    || !requested.barcode().equals(record.barcode())
                    || record.quantity() != requested.quantity()
                    || record.position() != requested.position()) {
                return Result.rejected("REFERENCE_INVALID", "Loss record does not implement request");
            }
        }
        if (inserts(changes, "DOC_EVENT").size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one DOC_EVENT required");
        }
        return Result.value(null);
    }

    private boolean allowedRole(OperationContext.ActorContext actor) {
        if (actor == null || actor.role() == null) {
            return false;
        }
        if (operation.equals(StockChangeSetAssembler.REPLENISHMENT)) {
            return actor.role() == OperationContext.Role.CLERK
                    || actor.role() == OperationContext.Role.MANAGER;
        }
        return actor.role() == OperationContext.Role.MANAGER;
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }

    private record Requested(String barcode, int quantity, Position position) {
    }
}
