package nz.ac.aut.pdc.supermarket.stock;

/** S=shelf, W=warehouse; disjoint positions have nonnegative balances. Display totals cannot bypass source-position insufficiency.
 *
 *
 * 库存位置：S = 在售货架，W = 在仓仓库。
 *
 * <p>两个位置互斥、数量非负；合计只用于展示，不能用来绕过来源位置不足。</p>
 */
public enum Position {
    S,
    W
}
