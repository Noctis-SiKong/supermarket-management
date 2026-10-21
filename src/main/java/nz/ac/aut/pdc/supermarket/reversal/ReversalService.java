package nz.ac.aut.pdc.supermarket.reversal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.stock.StockChangeLine;
import nz.ac.aut.pdc.supermarket.stock.StockCheckResult;
import nz.ac.aut.pdc.supermarket.stock.StockEntry;
import nz.ac.aut.pdc.supermarket.stock.StockOperations;
import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/**
 * 冲销编排骨架（WP-06）：高权限校验 → D1 依赖/重复冲销检查 → 生成 RELEASE
 * 核销与反向库存候选。不保存、不提交。
 *
 * <p>D1：若存在引用原核销的后续记录，则拒绝直接冲销；本骨架只做只读检查，
 * 不自动级联重建。B 新增业务码 `DEPENDENCY_BLOCKED`/`REVERSAL_FAILED` 需通知 A 登记。</p>
 */
public final class ReversalService {

    private final RedemptionReader redemptionReader;
    private final ReversalDependencyReader dependencyReader;
    private final StockEffectReader stockEffectReader;
    private final StockOperations stockOperations;
    private final StockPorts.IdGenerator idGenerator;

    public ReversalService(
            RedemptionReader redemptionReader,
            ReversalDependencyReader dependencyReader,
            StockEffectReader stockEffectReader,
            StockOperations stockOperations,
            StockPorts.IdGenerator idGenerator) {
        this.redemptionReader = Objects.requireNonNull(redemptionReader, "redemptionReader");
        this.dependencyReader = Objects.requireNonNull(dependencyReader, "dependencyReader");
        this.stockEffectReader = Objects.requireNonNull(stockEffectReader, "stockEffectReader");
        this.stockOperations = Objects.requireNonNull(stockOperations, "stockOperations");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public ReversalResult reverse(
            StockPorts.WorkingCopyView workingCopy, ReversalCommand command, Role actorRole) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(command, "command");
        if (actorRole != Role.MANAGER && actorRole != Role.ADMIN) {
            return new ReversalResult.Rejected(
                    "FORBIDDEN", "actorRole", "冲销需要店长或管理员权限");
        }
        try {
            Optional<Redemption> originalOptional = redemptionReader.find(command.redemptionId());
            if (originalOptional.isEmpty()) {
                return new ReversalResult.Rejected(
                        "REFERENCE_INVALID", "redemptionId", "原核销不存在");
            }
            Redemption original = originalOptional.get();
            if (original.effect() == Effect.RELEASE) {
                return new ReversalResult.Rejected(
                        "INVALID_INPUT", "redemptionId", "RELEASE 记录不能再次冲销");
            }
            if (redemptionReader.findReversalOf(original.redemptionId()).isPresent()) {
                return new ReversalResult.Rejected(
                        "EVENT_CONFLICT", "redemptionId", "该核销已有有效冲销记录");
            }
            List<String> dependents = dependencyReader.dependentRecordIds(original.redemptionId());
            if (dependents != null && !dependents.isEmpty()) {
                return new ReversalResult.Rejected(
                        "DEPENDENCY_BLOCKED", "redemptionId",
                        "存在引用该核销的后续记录，不能直接冲销：" + String.join(",", dependents));
            }
            List<StockEntry> effects = stockEffectReader.entriesFor(original.redemptionId());
            if (effects == null || effects.isEmpty()) {
                return new ReversalResult.Rejected(
                        "REFERENCE_INVALID", "stockEffect", "原核销没有可冲销的库存流水");
            }

            Redemption release = new Redemption(
                    idGenerator.nextId("REV-RED"),
                    original.sourceLineId(),
                    original.originalSaleLineId(),
                    original.originalBarcode(),
                    original.aftersaleId(),
                    original.kind(),
                    Effect.RELEASE,
                    original.quantity(),
                    original.amount(),
                    command.requestKey(),
                    original.redemptionId(),
                    original.opened(),
                    original.qualityProblem());

            List<StockChangeLine> changes = new ArrayList<>(effects.size());
            for (StockEntry effect : effects) {
                changes.add(new StockChangeLine(
                        effect.barcode(),
                        effect.position(),
                        -effect.delta(),
                        original.redemptionId(),
                        effect.transferKey(),
                        effect.id(),
                        "REV-" + effect.eventKey(),
                        effect.receiptReviewId()));
            }
            StockCheckResult checked = stockOperations.check(workingCopy, List.of(), changes);
            if (checked instanceof StockCheckResult.Rejected rejected) {
                return new ReversalResult.Rejected(
                        rejected.code(), rejected.field(), rejected.message());
            }
            if (checked instanceof StockCheckResult.Failed failed) {
                return new ReversalResult.Failed(failed.code(), failed.message());
            }
            StockCheckResult.Prepared prepared = (StockCheckResult.Prepared) checked;
            return new ReversalResult.Prepared(
                    new ReversalPlan(original.redemptionId(), release, prepared.entries()));
        } catch (RuntimeException ex) {
            return new ReversalResult.Failed("REVERSAL_FAILED", "冲销处理暂时不可用");
        }
    }
}
