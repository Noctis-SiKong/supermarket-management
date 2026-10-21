package nz.ac.aut.pdc.supermarket.stock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.app.AOperations;

/**
 * B 的 IF-07 在 A 的 StockDependency 适配要求上的真实实现。
 *
 * <p>只读 A WorkingCopy，接受 A shared.StockEntry 候选组；用 B 的库存规则校验后
 * 返回本次新增的 STOCK_ENTRY ChangeSet。不合并 overlay、不提交、不写文件。</p>
 */
public final class SharedStockAdapter implements AOperations.StockDependency {

    private final StockValidator validator;

    public SharedStockAdapter() {
        this(new StockValidator());
    }

    public SharedStockAdapter(StockValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public Result<ChangeSet> prepare(
            WorkingCopy workingCopy, ChangeSet nonStockOverlay, List<StockEntry> completeGroup) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(nonStockOverlay, "nonStockOverlay");
        Objects.requireNonNull(completeGroup, "completeGroup");
        try {
            WorkingCopy base = workingCopy.overlay(nonStockOverlay);
            List<StockChangeLine> lines = toLines(base, completeGroup);
            Optional<StockValidator.StockRejection> rejection =
                    validator.validate(lines, SharedStockProjection.balanceMap(base));
            if (rejection.isPresent()) {
                StockValidator.StockRejection violation = rejection.get();
                return Result.rejected(violation.code(), violation.field() + ": " + violation.message());
            }
            ChangeSet.Builder out = new ChangeSet.Builder();
            for (StockEntry entry : completeGroup) {
                out.add(RecordData.of("STOCK_ENTRY", entry.stockEntryId(), entry));
            }
            return Result.prepared(out.build());
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Stock candidate rejected: " + ex.getMessage());
        }
    }

    @Override
    public Result<Void> review(
            WorkingCopy workingCopy, BusinessRequest original, ChangeSet completeChanges) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(completeChanges, "completeChanges");
        try {
            List<StockEntry> changed = new ArrayList<>();
            List<RecordData> nonStockInserts = new ArrayList<>();
            for (RecordData record : completeChanges.inserts()) {
                if (record.table().equals("STOCK_ENTRY")) {
                    changed.add(record.as(StockEntry.class));
                } else {
                    nonStockInserts.add(record);
                }
            }
            if (changed.isEmpty()) {
                return Result.value(null);
            }
            boolean stockPatchOrRemoval = completeChanges.patches().stream()
                    .anyMatch(p -> p.table().equals("STOCK_ENTRY"))
                    || completeChanges.removals().stream()
                    .anyMatch(r -> r.table().equals("STOCK_ENTRY"));
            if (stockPatchOrRemoval) {
                return Result.rejected("INVALID_INPUT", "STOCK_ENTRY only supports inserts");
            }
            WorkingCopy base = workingCopy.overlay(new ChangeSet(
                    nonStockInserts, completeChanges.patches(), completeChanges.removals()));
            Optional<StockValidator.StockRejection> rejection = validator.validate(
                    toLines(base, changed), SharedStockProjection.balanceMap(base));
            if (rejection.isPresent()) {
                StockValidator.StockRejection violation = rejection.get();
                return Result.rejected(violation.code(), violation.field() + ": " + violation.message());
            }
            return Result.value(null);
        } catch (RuntimeException ex) {
            return Result.rejected("INVALID_INPUT", "Stock review rejected: " + ex.getMessage());
        }
    }

    @Override
    public void validateRecovered(WorkingCopy state) throws IOException {
        Objects.requireNonNull(state, "state");
        try {
            SharedStockProjection.balanceMap(state);
        } catch (RuntimeException ex) {
            throw new IOException("B stock recovery check failed", ex);
        }
    }

    private List<StockChangeLine> toLines(WorkingCopy workingCopy, List<StockEntry> entries) {
        List<StockChangeLine> lines = new ArrayList<>(entries.size());
        for (StockEntry entry : entries) {
            String barcode = SharedStockProjection.barcode(workingCopy, entry.sourceLineId());
            lines.add(new StockChangeLine(
                    barcode,
                    SharedStockProjection.position(entry.position()),
                    entry.quantityDelta(),
                    entry.sourceLineId(),
                    entry.transferKey(),
                    entry.reverseOfId(),
                    entry.eventKey(),
                    entry.receiptReviewId()));
        }
        return lines;
    }
}

