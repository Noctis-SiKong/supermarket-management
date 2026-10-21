package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Assemble validated aftersale redemption/replacement candidates and IDs; AftersaleService validates eligibility, quotas and stock.
 *
 *
 * 售后单据组装器：把已校验的输入组装为核销/替换品候选。
 *
 * <p>只做组装与 ID 申请；资格、额度、库存校验由 AftersaleService 调用对应组件完成。</p>
 */
public final class AftersaleAssembler {

    private final StockPorts.IdGenerator idGenerator;

    public AftersaleAssembler(StockPorts.IdGenerator idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public Redemption assembleRedemption(
            String aftersaleId,
            String requestKey,
            OriginalSaleLine original,
            AftersaleCommandLine line,
            BigDecimal amount) {
        return assembleRedemption(aftersaleId,requestKey,original,line,amount,idGenerator.nextId("RED"));
    }

    public Redemption assembleRedemption(String aftersaleId,String requestKey,OriginalSaleLine original,
            AftersaleCommandLine line,BigDecimal amount,String redemptionId) {
        return new Redemption(
                redemptionId,
                redemptionId,
                original.saleLineId(),
                original.barcode(),
                aftersaleId,
                line.kind(),
                Effect.CONSUME,
                line.quantity(),
                amount,
                requestKey,
                null,
                line.opened(),
                line.qualityProblem(),
                line.reasonText());
    }

    public Replacement assembleReplacement(
            String aftersaleId,
            OriginalSaleLine original,
            AftersaleCommandLine line,
            Redemption redemption,
            ProductSnapshot product,
            BigDecimal replacementAmount) {
        return new Replacement(
                idGenerator.nextId("RL"),
                aftersaleId,
                redemption.redemptionId(),
                line.replacementBarcode(),
                line.replacementQty(),
                product.name(),
                product.unitPrice(),
                replacementAmount,
                line.qualityPair() ? "QUALITY_SAME_ITEM" : "NORMAL",
                line.qualityPair() ? redemption.redemptionId() : null);
    }
}
