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

/** CR07-10 candidate orchestration: strict eligibility, quotas, redemptions/replacements, IF-07 stock and settlement; never saves. Reject over-30-day and opened non-quality items; reasons do not relax rules.
 *
 *
 * 售后编排（CR07-10）：资格严格判定 -> 额度/金额 -> 核销/替换候选
 * -> 替换品 IF-07 库存核对 -> 结算候选。不保存、不提交。
 *
 * <p>不设店长资格例外：超 30 天一律拒绝；已开封非质量一律拒绝；
 * 质量原因仅允许 qualityProblem=true；其他原因必须填写 reasonText。原因文本
 * 不改变 30 天、开封/质量、数量/金额/库存校验。</p>
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

    /** Compatibility constructor using the default settlement calculator.
 *
 * 兼容旧调用：默认结算计算器。 */
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
        return apply(workingCopy, command, actorRole, idGenerator.nextId("AF"));
    }

    public AftersaleResult apply(
            StockPorts.WorkingCopyView workingCopy,
            AftersaleCommand command,
            Role actorRole,
            String aftersaleId) {
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actorRole, "actorRole");
        if (aftersaleId == null || aftersaleId.isBlank()) {
            return new AftersaleResult.Rejected("INVALID_INPUT", "aftersaleId", "售后单号不能为空");
        }
        try {
            Instant now = idGenerator.now();
            List<Redemption> redemptions = new ArrayList<>();
            List<Replacement> replacements = new ArrayList<>();
            List<AftersaleResult.StockEntryView> stockViews = new ArrayList<>();
            List<Settlement> settlements = new ArrayList<>();
            List<nz.ac.aut.pdc.supermarket.stock.StockEntry> stockOverlay = new ArrayList<>();
            Map<String, List<Redemption>> pendingByLine = new HashMap<>();

            for (Planned planned : orderedLines(command.lines())) {
                AftersaleCommandLine line=planned.line();
                Optional<OriginalSaleLine> originalOptional = originalSaleReader.readLine(line.saleLineId());
                if (originalOptional.isEmpty()) {
                    return new AftersaleResult.Rejected(
                            "REFERENCE_INVALID", "saleLineId", "原销售行不存在：" + line.saleLineId());
                }
                OriginalSaleLine original = originalOptional.get();
                boolean special=line.kind()==RedemptionKind.EXCHANGE && line.qualityProblem()
                    && original.barcode().equals(line.replacementBarcode()) && line.quantity()==line.replacementQty();
                if(line.qualityPair() && !special) return new AftersaleResult.Rejected("INVALID_INPUT","qualityPair","Invalid special quality pair");
                if(special && !line.qualityPair()) line=new AftersaleCommandLine(line.saleLineId(),line.kind(),line.quantity(),line.opened(),line.qualityProblem(),line.reasonKind(),line.reasonText(),line.replacementBarcode(),line.replacementQty(),true);

                Optional<AftersaleResult.Rejected> eligibility =
                        checkEligibility(original, line, now);
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
                        aftersaleId, command.requestKey(), original, line, amount,planned.id());
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
                            replacement.lineId(),
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

    private record Planned(AftersaleCommandLine line,String id) {}
    private List<Planned> orderedLines(List<AftersaleCommandLine> lines) {
        List<Planned> planned=new ArrayList<>();
        for (var line:lines.stream().sorted(java.util.Comparator.comparing(nz.ac.aut.pdc.shared.JsonCodec::write)).toList())
            planned.add(new Planned(line,idGenerator.nextId("RED")));
        return planned.stream().sorted(java.util.Comparator
            .comparingInt((Planned p) -> p.line().kind()==RedemptionKind.EXCHANGE?0:1)
            .thenComparing(Planned::id)).toList();
    }

    private Optional<AftersaleResult.Rejected> checkEligibility(
            OriginalSaleLine original, AftersaleCommandLine line, Instant now) {
        LocalDate saleDate = original.saleTime().atZone(ZONE).toLocalDate();
        boolean withinWindow = !now.atZone(ZONE).toLocalDate()
                .isAfter(saleDate.plusDays(RETURN_WINDOW_DAYS));
        if (!withinWindow) {
            return Optional.of(new AftersaleResult.Rejected(
                    "FORBIDDEN", "saleTime", "超过 30 天售后期限，一律拒绝"));
        }
        if (line.opened() && !line.qualityProblem()) {
            return Optional.of(new AftersaleResult.Rejected(
                    "FORBIDDEN", "opened", "已开封且非质量问题，一律拒绝"));
        }
        if (line.reasonKind() == AftersaleReasonKind.QUALITY && !line.qualityProblem()) {
            return Optional.of(new AftersaleResult.Rejected(
                    "INVALID_INPUT", "reasonKind", "质量原因必须对应质量问题"));
        }
        if (line.reasonKind() == AftersaleReasonKind.OTHER
                && (line.reasonText() == null || line.reasonText().isBlank())) {
            return Optional.of(new AftersaleResult.Rejected(
                    "INVALID_INPUT", "reasonText", "其他原因必须填写原因文本"));
        }
        return Optional.empty();
    }
}

