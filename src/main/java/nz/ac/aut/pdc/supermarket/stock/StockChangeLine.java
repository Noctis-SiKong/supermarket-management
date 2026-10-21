package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/**
 * 本次业务请求中的一条有符号库存变动（IF-07 check 的输入）。
 *
 * <p>业务数量边界（1–9999）由 {@link StockValidator} 统一校验；
 * 本记录只负责结构完整性，不在这里复制业务规则。</p>
 *
 * @param barcode     商品条码
 * @param position    库存位置
 * @param delta       有符号变动，正数入库、负数出库
 * @param sourceLineId 来源业务行（可空）
 * @param transferKey 同次双腿转移关联键（补货等场景；可空）
 * @param reverseOfId 反向引用的原流水（冲销场景；可空）
 */
public record StockChangeLine(
        String barcode,
        Position position,
        int delta,
        String sourceLineId,
        String transferKey,
        String reverseOfId) {

    public StockChangeLine {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        Objects.requireNonNull(position, "position");
    }
}
