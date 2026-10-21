package nz.ac.aut.pdc.supermarket.stock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * IF-07 的骨架实现：读取余额、校验变动、生成候选流水。
 *
 * <p>职责：编排只读读取 + 纯规则校验 + 候选组装；不保存、不分配 commitOrdinal、
 * 不调用提交入口。依赖全部通过构造器注入，便于替换为 A 的真实实现。</p>
 */
public final class StockService implements StockOperations {

    private final StockPorts.ProductCatalog productCatalog;
    private final StockPorts.IdGenerator idGenerator;
    private final StockValidator validator;

    public StockService(
            StockPorts.ProductCatalog productCatalog,
            StockPorts.IdGenerator idGenerator,
            StockValidator validator) {
        this.productCatalog = Objects.requireNonNull(productCatalog, "productCatalog");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public List<StockBalance> balances(
            StockPorts.WorkingCopyView workingCopy, Collection<String> barcodes) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(barcodes, "barcodes");
        return workingCopy.balances(barcodes).stream()
                .sorted(Comparator.comparing(StockBalance::barcode)
                        .thenComparing(StockBalance::position))
                .toList();
    }

    @Override
    public StockCheckResult check(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            List<StockChangeLine> changes) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(overlayEntries, "overlayEntries");
        if (changes == null) {
            return new StockCheckResult.Rejected("INVALID_INPUT", "lines", "变动组不能为空");
        }
        try {
            List<StockChangeLine> requestLines = List.copyOf(changes);
            Optional<StockCheckResult.Rejected> referenceRejection = validateReferences(requestLines);
            if (referenceRejection.isPresent()) {
                return referenceRejection.get();
            }

            Map<StockValidator.BalanceKey, Integer> balancesBefore = balancesBefore(workingCopy, overlayEntries, requestLines);
            Optional<StockValidator.StockRejection> violation = validator.validate(requestLines, balancesBefore);
            if (violation.isPresent()) {
                StockValidator.StockRejection rejection = violation.get();
                return new StockCheckResult.Rejected(rejection.code(), rejection.field(), rejection.message());
            }

            List<StockEntry> prepared = new ArrayList<>(requestLines.size());
            for (StockChangeLine line : requestLines) {
                String eventKey = line.eventKey() == null || line.eventKey().isBlank()
                        ? idGenerator.nextId("EV")
                        : line.eventKey();
                prepared.add(new StockEntry(
                        idGenerator.nextId("SE"),
                        line.barcode(),
                        line.position(),
                        line.delta(),
                        eventKey,
                        line.transferKey(),
                        line.sourceLineId(),
                        line.reverseOfId(),
                        line.receiptReviewId()));
            }
            return new StockCheckResult.Prepared(prepared, balancesAfter(balancesBefore, requestLines));
        } catch (RuntimeException ex) {
            return new StockCheckResult.Failed("STOCK_CHECK_FAILED", "库存核对暂时不可用");
        }
    }

    private Optional<StockCheckResult.Rejected> validateReferences(List<StockChangeLine> changes) {
        for (StockChangeLine line : changes) {
            if (productCatalog.productName(line.barcode()).isEmpty()) {
                return Optional.of(new StockCheckResult.Rejected(
                        "REFERENCE_INVALID", line.barcode(), "商品不存在：" + line.barcode()));
            }
        }
        return Optional.empty();
    }

    private Map<StockValidator.BalanceKey, Integer> balancesBefore(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            List<StockChangeLine> changes) {
        List<String> barcodes = changes.stream().map(StockChangeLine::barcode).distinct().toList();
        Map<StockValidator.BalanceKey, Integer> balances = new HashMap<>();
        for (StockBalance balance : workingCopy.balances(barcodes)) {
            balances.put(new StockValidator.BalanceKey(balance.barcode(), balance.position()), balance.quantity());
        }
        for (StockEntry entry : overlayEntries) {
            StockValidator.BalanceKey key = new StockValidator.BalanceKey(entry.barcode(), entry.position());
            balances.merge(key, entry.delta(), Integer::sum);
        }
        return balances;
    }

    private List<StockBalance> balancesAfter(
            Map<StockValidator.BalanceKey, Integer> balancesBefore,
            List<StockChangeLine> changes) {
        Map<StockValidator.BalanceKey, Integer> after = new HashMap<>(balancesBefore);
        for (StockChangeLine line : changes) {
            StockValidator.BalanceKey key = new StockValidator.BalanceKey(line.barcode(), line.position());
            after.merge(key, line.delta(), Integer::sum);
        }
        return after.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(StockValidator.BalanceKey::barcode)
                                .thenComparing(StockValidator.BalanceKey::position)))
                .map(entry -> new StockBalance(
                        entry.getKey().barcode(), entry.getKey().position(), entry.getValue()))
                .toList();
    }
}
