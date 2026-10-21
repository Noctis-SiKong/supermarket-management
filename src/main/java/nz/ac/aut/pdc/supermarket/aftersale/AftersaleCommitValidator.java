package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

/**
 * B 主责的 AFTERSALE 六参校验器：在 A 的锁内复核完整 ChangeSet。
 *
 * <p>检查单据/核销/替换品的基本一致性、CR07-10 原因文本和库存候选；
 * A 仍负责身份、引用、版本、闭合组等公共红线。</p>
 */
public final class AftersaleCommitValidator implements SubmissionPort.CommitValidator {

    private final SharedStockAdapter stockAdapter;

    public AftersaleCommitValidator(SharedStockAdapter stockAdapter) {
        this.stockAdapter = Objects.requireNonNull(stockAdapter, "stockAdapter");
    }

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
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(changes, "changes");

        RecordData baselineDocument = baseline.find("DOCUMENT", op.targetId()).orElse(null);
        final DocumentHeader header;
        if (baselineDocument != null
                && "DRAFT".equals(baselineDocument.as(DocumentHeader.class).status())) {
            if (!inserts(changes, "DOCUMENT").isEmpty()) {
                return Result.rejected("INVALID_INPUT", "Draft completion must patch the existing document");
            }
            ChangeSet.Patch patch = changes.patches().stream()
                    .filter(p -> p.table().equals("DOCUMENT") && p.id().equals(op.targetId()))
                    .findFirst().orElse(null);
            if (patch == null) {
                return Result.rejected("INVALID_INPUT", "Draft completion requires DOCUMENT patch");
            }
            ObjectNode fields = (ObjectNode) JsonCodec.tree(patch.changedFields());
            if (!"CONFIRMED".equals(fields.path("status").asText())) {
                return Result.rejected("INVALID_INPUT", "Draft completion must set CONFIRMED");
            }
            boolean traced = baseline.records("DOC_EVENT").stream()
                    .anyMatch(r -> r.as(DocEvent.class).documentId().equals(op.targetId())
                            && "READ_FOR_AFTERSALE".equals(r.as(DocEvent.class).action()));
            if (!traced) {
                return Result.rejected("DRAFT_HAS_NO_READ_TRACE",
                        "Aftersale completion requires saved read trace");
            }
            header = new DocumentHeader(
                    op.targetId(), "AFTERSALE", "CONFIRMED", op.requestKey(),
                    baselineDocument.as(DocumentHeader.class).requestFingerprint(),
                    baselineDocument.as(DocumentHeader.class).createdBy(),
                    baselineDocument.as(DocumentHeader.class).createdAt(),
                    actor.accountId(), null, null);
        } else {
            List<RecordData> documents = inserts(changes, "DOCUMENT");
            if (documents.size() != 1) {
                return Result.rejected("INVALID_INPUT", "Exactly one AFTERSALE document required");
            }
            try {
                header = documents.get(0).as(DocumentHeader.class);
            } catch (RuntimeException ex) {
                return Result.rejected("INVALID_INPUT", "Invalid AFTERSALE document");
            }
        }
        if (!"AFTERSALE".equals(header.kind()) || !"CONFIRMED".equals(header.status())) {
            return Result.rejected("INVALID_INPUT", "AFTERSALE document must be CONFIRMED");
        }
        if (!header.documentId().equals(op.targetId())) {
            return Result.rejected("REFERENCE_INVALID", "Operation target is not the aftersale document");
        }
        if (!actor.accountId().equals(header.confirmedBy())) {
            return Result.rejected("FORBIDDEN", "ConfirmedBy must be the authenticated actor");
        }
        if (request == null || acceptedAt == null) {
            return Result.rejected("INVALID_INPUT", "Original request and acceptedAt required");
        }
        final AftersaleCommand command;
        try {
            command = request.as(AftersaleCommand.class);
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Invalid original aftersale request");
        }
        if (!command.requestKey().equals(op.requestKey())) {
            return Result.rejected("REQUEST_KEY_CONFLICT", "Original request key mismatch");
        }
        List<AftersaleCommandLine> unmatched = new ArrayList<>(command.lines());
        List<AftersaleCommandLine> exchanges = command.lines().stream()
                .filter(line -> line.kind() == RedemptionKind.EXCHANGE).toList();
        Map<String, AftersaleCommandLine> lineByRedemption = new HashMap<>();
        List<Redemption> redemptions = new ArrayList<>();
        for (RecordData record : inserts(changes, "REDEMPTION")) {
            final Redemption redemption;
            try {
                redemption = record.as(Redemption.class);
            } catch (RuntimeException ex) {
                return Result.rejected("INVALID_INPUT", "Cannot read redemption: " + ex.getMessage());
            }
            if (!header.documentId().equals(redemption.aftersaleId())) {
                return Result.rejected("REFERENCE_INVALID", "Redemption aftersaleId mismatch");
            }
            AftersaleCommandLine requestedLine = takeMatching(unmatched, redemption);
            if (requestedLine == null) {
                return Result.rejected("REFERENCE_INVALID",
                        "Redemption does not implement original request line");
            }
            lineByRedemption.put(redemption.redemptionId(), requestedLine);
            if (!redemption.redemptionId().equals(redemption.sourceLineId())) {
                return Result.rejected("REFERENCE_INVALID", "Redemption source line must be its business line");
            }
            if (redemption.amount().signum() < 0 || redemption.quantity() <= 0) {
                return Result.rejected("INVALID_INPUT", "Redemption amount/quantity invalid");
            }
            redemptions.add(redemption);
        }
        if (redemptions.isEmpty()) {
            return Result.rejected("INVALID_INPUT", "AfterSale requires at least one redemption");
        }
        if (!unmatched.isEmpty()) {
            return Result.rejected("REFERENCE_INVALID", "Some requested aftersale lines have no redemption");
        }
        Set<String> replacedRedemptions = new HashSet<>();
        for (RecordData record : inserts(changes, "REPLACEMENT")) {
            Replacement replacement = record.as(Replacement.class);
            if (!header.documentId().equals(replacement.aftersaleId())) {
                return Result.rejected("REFERENCE_INVALID", "Replacement aftersaleId mismatch");
            }
            AftersaleCommandLine requestedLine = lineByRedemption.get(replacement.redemptionId());
            if (requestedLine == null
                    || !Objects.equals(replacement.barcode(), requestedLine.replacementBarcode())
                    || replacement.quantity() != requestedLine.replacementQty()) {
                return Result.rejected("REFERENCE_INVALID", "Replacement does not implement original request");
            }
            if (!replacedRedemptions.add(replacement.redemptionId())) {
                return Result.rejected("INVALID_INPUT", "Duplicate replacement for redemption");
            }
            if (replacement.replacementAmount().signum() < 0) {
                return Result.rejected("INVALID_INPUT", "Replacement amount invalid");
            }
        }
        if (replacedRedemptions.size() != exchanges.size()) {
            return Result.rejected("REFERENCE_INVALID", "Every exchange line requires one replacement");
        }

        Result<Void> stockCheck = stockAdapter.review(baseline, request, changes);
        if (!stockCheck.hasValue()) {
            return stockCheck;
        }
        return Result.value(null);
    }

    private AftersaleCommandLine takeMatching(
            List<AftersaleCommandLine> candidates, Redemption redemption) {
        for (int i = 0; i < candidates.size(); i++) {
            AftersaleCommandLine line = candidates.get(i);
            if (!line.saleLineId().equals(redemption.originalSaleLineId())
                    || line.kind() != redemption.kind()
                    || line.quantity() != redemption.quantity()
                    || line.opened() != redemption.opened()
                    || line.qualityProblem() != redemption.qualityProblem()) {
                continue;
            }
            if (line.reasonKind() == AftersaleReasonKind.OTHER
                    && !Objects.equals(line.reasonText(), redemption.reasonText())) {
                continue;
            }
            candidates.remove(i);
            return line;
        }
        return null;
    }

    private Result<Void> validateAftersaleWindow(
            WorkingCopy baseline, List<Redemption> redemptions, Instant acceptedAt) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate acceptedDate = acceptedAt.atZone(zone).toLocalDate();
        for (Redemption redemption : redemptions) {
            var saleLine = baseline.find("DOC_LINE", redemption.originalSaleLineId()).orElse(null);
            if (saleLine == null) {
                return Result.rejected("REFERENCE_INVALID", "Original sale line missing");
            }
            DocumentLine line = saleLine.as(DocumentLine.class);
            var document = baseline.find("DOCUMENT", line.documentId()).orElse(null);
            if (document == null) {
                return Result.rejected("REFERENCE_INVALID", "Original sale document missing");
            }
            DocumentHeader saleHeader = document.as(DocumentHeader.class);
            if (!"SALE".equals(saleHeader.kind()) || !"CONFIRMED".equals(saleHeader.status())
                    || saleHeader.confirmedAt() == null) {
                return Result.rejected("REFERENCE_INVALID", "Original line is not a confirmed sale");
            }
            LocalDate saleDate = Instant.parse(saleHeader.confirmedAt()).atZone(zone).toLocalDate();
            if (ChronoUnit.DAYS.between(saleDate, acceptedDate) > 30) {
                return Result.rejected("AFTERSALE_WINDOW_EXPIRED", "Outside 30-day aftersale window");
            }
        }
        return Result.value(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }
}
