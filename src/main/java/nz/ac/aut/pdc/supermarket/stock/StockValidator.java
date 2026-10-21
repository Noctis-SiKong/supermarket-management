package nz.ac.aut.pdc.supermarket.stock;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure IF-07 rules: quantity, positions, paired-transfer conservation and nonnegative source balances; supplied balances only, no external reads/saving.
 */
public final class StockValidator {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 9999;

    /** Check complete movements against product/position balances (missing=zero); first rejection or empty.
     */
    public Optional<StockRejection> validate(
            List<StockChangeLine> changes, Map<BalanceKey, Integer> balancesBefore) {

        if (changes == null || changes.isEmpty()) {
            return Optional.of(new StockRejection("INVALID_INPUT", "lines", "变动组不能为空"));
        }

        for (StockChangeLine line : changes) {
            if (line == null || line.barcode().isBlank()) {
                return Optional.of(new StockRejection("INVALID_INPUT", "barcode", "商品条码不能为空"));
            }
            int quantity = Math.abs(line.delta());
            if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
                return Optional.of(new StockRejection(
                        "INVALID_INPUT", "quantity", "业务数量必须为 1–9999 的整数"));
            }
        }

        Optional<StockRejection> transferRejection = validateTransfers(changes);
        if (transferRejection.isPresent()) {
            return transferRejection;
        }

        Map<BalanceKey, Integer> netDeltas = new HashMap<>();
        for (StockChangeLine line : changes) {
            BalanceKey key = new BalanceKey(line.barcode(), line.position());
            netDeltas.merge(key, line.delta(), Math::addExact);
        }

        for (Map.Entry<BalanceKey, Integer> entry : netDeltas.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().barcode() + ":" + e.getKey().position()))
                .toList()) {
            if (entry.getValue() >= 0) {
                continue;
            }
            BalanceKey key = entry.getKey();
            int available = balancesBefore.getOrDefault(key, 0);
            if (available + entry.getValue() < 0) {
                return Optional.of(new StockRejection(
                        "INSUFFICIENT_STOCK",
                        key.barcode() + ":" + key.position(),
                        "来源位置库存不足：可用 " + available + "，本次需要 " + (-entry.getValue())));
            }
        }
        return Optional.empty();
    }

    private Optional<StockRejection> validateTransfers(List<StockChangeLine> changes) {
        Map<String, List<StockChangeLine>> groups = changes.stream()
                .filter(line -> line.transferKey() != null && !line.transferKey().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(StockChangeLine::transferKey));

        for (Map.Entry<String, List<StockChangeLine>> group : groups.entrySet()) {
            List<StockChangeLine> lines = group.getValue();
            if (lines.size() != 2) {
                return Optional.of(unbalanced(group.getKey(), "转移必须恰好由两条流水组成"));
            }
            StockChangeLine first = lines.get(0);
            StockChangeLine second = lines.get(1);
            boolean sameBarcode = first.barcode().equals(second.barcode());
            boolean oppositePositions = first.position() != second.position();
            boolean sumsToZero = first.delta() + second.delta() == 0;
            if (!sameBarcode || !oppositePositions || !sumsToZero) {
                return Optional.of(unbalanced(group.getKey(), "转移两腿必须同商品、异位置且数量互为相反数"));
            }
        }
        return Optional.empty();
    }

    private StockRejection unbalanced(String transferKey, String message) {
        return new StockRejection("TRANSFER_UNBALANCED", transferKey, message);
    }

    /** Product/position validation key.
  */
    public record BalanceKey(String barcode, Position position) {
        public BalanceKey {
            if (barcode == null || barcode.isBlank()) {
                throw new IllegalArgumentException("barcode must not be blank");
            }
            if (position == null) {
                throw new IllegalArgumentException("position must not be null");
            }
        }
    }

    /** Rejection with a stable shared section 4 error code.
  */
    public record StockRejection(String code, String field, String message) {
    }
}
