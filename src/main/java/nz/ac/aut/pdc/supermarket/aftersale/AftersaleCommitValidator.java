package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentHeader;
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

        List<RecordData> documents = inserts(changes, "DOCUMENT");
        if (documents.size() != 1) {
            return Result.rejected("INVALID_INPUT", "Exactly one AFTERSALE document required");
        }
        DocumentHeader header;
        try {
            header = documents.get(0).as(DocumentHeader.class);
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Invalid AFTERSALE document");
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

        List<Redemption> redemptions = new ArrayList<>();
        for (RecordData record : inserts(changes, "REDEMPTION")) {
            Redemption redemption = record.as(Redemption.class);
            if (!header.documentId().equals(redemption.aftersaleId())) {
                return Result.rejected("REFERENCE_INVALID", "Redemption aftersaleId mismatch");
            }
            if (redemption.amount().signum() < 0 || redemption.quantity() < 0) {
                return Result.rejected("INVALID_INPUT", "Redemption amount/quantity invalid");
            }
            if (!redemption.qualityProblem()
                    && (redemption.reasonText() == null || redemption.reasonText().isBlank())) {
                return Result.rejected("INVALID_INPUT", "OTHER reason requires reasonText");
            }
            redemptions.add(redemption);
        }
        if (redemptions.isEmpty()) {
            return Result.rejected("INVALID_INPUT", "AfterSale requires at least one redemption");
        }
        for (RecordData record : inserts(changes, "REPLACEMENT")) {
            Replacement replacement = record.as(Replacement.class);
            if (!header.documentId().equals(replacement.aftersaleId())) {
                return Result.rejected("REFERENCE_INVALID", "Replacement aftersaleId mismatch");
            }
            if (replacement.replacementAmount().signum() < 0) {
                return Result.rejected("INVALID_INPUT", "Replacement amount invalid");
            }
        }

        Result<Void> stockCheck = stockAdapter.review(baseline, request, changes);
        if (!stockCheck.hasValue()) {
            return stockCheck;
        }
        return Result.value(null);
    }

    private List<RecordData> inserts(ChangeSet changes, String table) {
        return changes.inserts().stream().filter(r -> r.table().equals(table)).toList();
    }
}
