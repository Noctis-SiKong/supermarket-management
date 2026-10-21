package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;

/**
 * 售后单中的一条明细（退货或换货），原因按 CR07-10 逐明细记录。
 *
 * <p>qualityProblem 是事实；QUALITY 只能用于质量事实，OTHER 必须填写原因文本。
 * 原因文本不改变 30 天、开封/质量、数量/金额/库存校验。</p>
 */
public record AftersaleCommandLine(
        String saleLineId,
        RedemptionKind kind,
        int quantity,
        boolean opened,
        boolean qualityProblem,
        AftersaleReasonKind reasonKind,
        String reasonText,
        String replacementBarcode,
        int replacementQty,
        boolean qualityPair) {

    public AftersaleCommandLine {
        if (saleLineId == null || saleLineId.isBlank()) {
            throw new IllegalArgumentException("saleLineId must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reasonKind, "reasonKind");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (reasonKind == AftersaleReasonKind.OTHER && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("OTHER reason requires reasonText");
        }
        if (reasonKind == AftersaleReasonKind.QUALITY && !qualityProblem) {
            throw new IllegalArgumentException("QUALITY reason requires qualityProblem=true");
        }
        if (kind == RedemptionKind.EXCHANGE) {
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                throw new IllegalArgumentException("exchange requires replacementBarcode");
            }
            if (replacementQty <= 0) {
                throw new IllegalArgumentException("exchange requires positive replacementQty");
            }
        }
    }
}
