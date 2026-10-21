package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockChangeLine;
import nz.ac.aut.pdc.supermarket.stock.StockCheckResult;
import nz.ac.aut.pdc.supermarket.stock.StockOperations;
import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/**
 * 售后编排骨架（WP-05）：资格校验 -> 额度视图/金额计算 -> 核销/替换候选
 * -> 替换品 IF-07 库存核对 -> 结算候选。不保存、不提交。
 *
 * <p>支持同一原行在一次命令内多条明细的顺序额度扣减；资格判定为简化规则：
 * 超 30 天或已开封非质量需要 CR07-09 店长/管理员例外；例外不改变数量/金额/库存校验。</p>
 */
public final class AftersaleService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RETURN_WINDOW_DAYS = 30;

    private final OriginalSaleReader originalSaleReader;
    private final QuotaLedgerReader quotaLedgerReader;
    private final ProductSnapshotReader productSnapshotReader;
    private final StockOperations stockOperations;
    private final QuotaViewBuilder quotaViewBuilder;
    private final QuotaCalculator quotaCalculator;
    private final QualityPairValidator qualityPairValidator;
    private final AftersaleAssembler assembler;
    private final StockPorts.IdGenerator idGenerator;
    private final SettlementCalculator settlementCalculator;

    public AftersaleService(
            OriginalSaleReader originalSaleReader,
            QuotaLedgerReader quotaLedgerReader,
            ProductSnapshotReader productSnapshotReader,
            StockOperations stockOperations,
            QuotaViewBuilder quotaViewBuilder,
            QuotaCalculator quotaCalculator,
            QualityPairValidator qualityPairValidator,
            AftersaleAssembler assembler,
            StockPorts.IdGenerator idGenerator,
            SettlementCalculator settlementCalculator) {
        this.originalSaleReader = Objects.requireNonNull(originalSaleReader, "originalSaleReader");
        this.quotaLedgerReader = Objects.requireNonNull(quotaLedgerReader, "quotaLedgerReader");
        this.productSnapshotReader = Objects.requireNonNull(productSnapshotReader, "productSnapshotReader");
        this.stockOperations = Objects.requireNonNull(stockOperations, "stockOperations");
        this.quotaViewBuilder = Objects.requireNonNull(quotaViewBuilder, "quotaViewBuilder");
        this.quotaCalculator = Objects.requireNonNull(quotaCalculator, "quotaCalculator");
        this.qualityPairValidator = Objects.requireNonNull(qualityPairValidator, "qualityPairValidator");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.settlementCalculator = Objects.requireNonNull(settlementCalculator, "settlementCalculator");
    }

    /** 兼容旧调用：默认结算计算器。 */
    public AftersaleService(
            OriginalSaleReader originalSaleReader,
            QuotaLedgerReader quotaLedgerReader,
            ProductSnapshotReader productSnapshotReader,
            StockOperations stockOperations,
            QuotaViewBuilder quotaViewBuilder,
            QuotaCalculator quotaCalculator,
            QualityPairValidator qualityPairValidator,
            AftersaleAssembler assembler,
            StockPorts.IdGenerator idGenerator) {
        this(originalSaleReader, quotaLedgerReader, productSnapshotReader, stockOperations,
                quotaViewBuilder, quotaCalculator, qualityPairValidator, assembler, idGenerator,
                new SettlementCalculator());
    }

    public AftersaleResult apply(
            StockPorts.WorkingCopyView workingCopy, AftersaleCommand command, Role actorRole) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(command, "command");
        try {
            Instant now = idGenerator.now();
            String aftersaleId = idGenerator.nextId("AF");
            List<Redemption> redemptions = new ArrayList<>();
            List<Replacement> replacements = new ArrayList<>();
            List<AftersaleResult.StockEntryView> stockViews = new ArrayList<>();
            List<Settlement> settlements = new ArrayList<>();
            List<nz.ac.aut.pdc.supermarket.stock.StockEntry> stockOverlay = new ArrayList<>();
            Map<String, List<Redemption>> pendingByLine = new HashMap<>();

            for (AftersaleCommandLine line : command.lines()) {
                Optional<OriginalSaleLine> originalOptional = originalSaleReader.readLine(line.saleLineId());
                if (originalOptional.isEmpty()) {
                    return new AftersaleResult.Rejected(
                            "REFERENCE_INVALID", "saleLineId", "原销售行不存在：" + line.saleLineId());
                }
                OriginalSaleLine original = originalOptional.get();

                boolean exceptionRequired = requiresException(original, line, now);
                Optional<AftersaleResult.Rejected> eligibility =
                        checkEligibility(command, actorRole, exceptionRequired);
                if (eligibility.isPresent()) {
                    return eligibility.get();
                }

                List<Redemption> history = new ArrayList<>(
                        quotaLedgerReader.effectiveFor(original.saleLineId()));
                history.addAll(pendingByLine.getOrDefault(original.saleLineId(), List.of()));
                QuotaView view = quotaViewBuilder.build(original, history);
                if (line.quantity() > view.remainingQty()) {
                    return new AftersaleResult.Rejected(
                            "INVALID_INPUT", "quantity", "本次数量超过原行剩余额度");
                }
                boolean settlesRemaining = line.quantity() == view.remainingQty();
                BigDecimal amount = quotaCalculator.allocate(
                        line.quantity(), view, new SettlementContext(settlesRemaining));
                Redemption redemption = assembler.assembleRedemption(
                        aftersaleId,
                        command.requestKey(),
                        original,
                        line,
                        amount,
                        exceptionRequired ? command.exceptionAuthorizedBy() : null,
                        exceptionRequired ? command.exceptionReason() : null,
                        exceptionRequired ? command.relaxedRule() : null);
                redemptions.add(redemption);
                pendingByLine.computeIfAbsent(original.saleLineId(), key -> new ArrayList<>())
                        .add(redemption);

                Replacement replacement = null;
                if (line.kind() == RedemptionKind.EXCHANGE) {
                    Optional<ProductSnapshot> productOptional =
                            productSnapshotReader.find(line.replacementBarcode());
                    if (productOptional.isEmpty()) {
                        return new AftersaleResult.Rejected(
                                "REFERENCE_INVALID", "replacementBarcode", "替换品不存在");
                    }
                    ProductSnapshot product = productOptional.get();
                    BigDecimal replacementAmount = product.unitPrice()
                            .multiply(BigDecimal.valueOf(line.replacementQty()));
                    replacement = assembler.assembleReplacement(
                            aftersaleId, original, line, redemption, product, replacementAmount);
                    if (line.qualityPair()) {
                        Optional<String> pairRejection =
                                qualityPairValidator.validate(original, redemption, replacement);
                        if (pairRejection.isPresent()) {
                            return new AftersaleResult.Rejected(
                                    "INVALID_INPUT", "qualityPair", pairRejection.get());
                        }
                    }
                    replacements.add(replacement);

                    StockChangeLine stockChange = new StockChangeLine(
                            line.replacementBarcode(),
                            Position.S,
                            -line.replacementQty(),
                            redemption.redemptionId(),
                            null,
                            null,
                            "EV-AF-" + replacement.lineId(),
                            null);
                    StockCheckResult stockResult =
                            stockOperations.check(workingCopy, stockOverlay, List.of(stockChange));
                    if (stockResult instanceof StockCheckResult.Rejected rejected) {
                        return new AftersaleResult.Rejected(
                                rejected.code(), rejected.field(), rejected.message());
                    }
                    if (stockResult instanceof StockCheckResult.Failed failed) {
                        return new AftersaleResult.Failed(failed.code(), failed.message());
                    }
                    StockCheckResult.Prepared prepared = (StockCheckResult.Prepared) stockResult;
                    stockOverlay.addAll(prepared.entries());
                    for (var entry : prepared.entries()) {
                        stockViews.add(new AftersaleResult.StockEntryView(
                                entry.id(), entry.barcode(), entry.position(), entry.delta(),
                                entry.eventKey(), entry.transferKey()));
                    }
                }
                settlements.add(settlementCalculator.calculate(redemption, replacement));
            }
            return new AftersaleResult.Prepared(redemptions, replacements, stockViews, settlements);
        } catch (RuntimeException ex) {
            return new AftersaleResult.Failed("AFTERSALE_FAILED", "售后处理暂时不可用");
        }
    }

    private Optional<AftersaleResult.Rejected> checkEligibility(
            AftersaleCommand command, Role actorRole, boolean exceptionRequired) {
        if (!exceptionRequired) {
            return Optional.empty();
        }
        if (actorRole == null || !actorRole.canAuthorizeException()) {
            return Optional.of(new AftersaleResult.Rejected(
                    "FORBIDDEN", "actorRole", "该售后需要店长/管理员授权例外"));
        }
        if (isBlank(command.exceptionAuthorizedBy())
                || isBlank(command.exceptionReason())
                || isBlank(command.relaxedRule())) {
            return Optional.of(new AftersaleResult.Rejected(
                    "INVALID_INPUT", "exception",
                    "资格例外必须记录授权人、原因和所放宽规则"));
        }
        return Optional.empty();
    }

    private boolean requiresException(OriginalSaleLine original, AftersaleCommandLine line, Instant now) {
        LocalDate saleDate = original.saleTime().atZone(ZONE).toLocalDate();
        boolean withinWindow = !now.atZone(ZONE).toLocalDate().isAfter(saleDate.plusDays(RETURN_WINDOW_DAYS));
        boolean openedNonQuality = line.opened() && !line.qualityProblem();
        return !(withinWindow && !openedNonQuality);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
