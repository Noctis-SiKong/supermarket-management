package nz.ac.aut.pdc.supermarket.aftersale;

/**
 * 本次核销的结算上下文。
 *
 * @param settlesRemaining 本次是否结清该原行全部剩余数量（结清时用全部剩余金额）
 */
public record SettlementContext(boolean settlesRemaining) {
}
