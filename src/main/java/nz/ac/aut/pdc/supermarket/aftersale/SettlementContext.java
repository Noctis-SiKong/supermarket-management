package nz.ac.aut.pdc.supermarket.aftersale;

/** Current settlement context; settlesRemaining consumes all remaining original-line quantity and money.
 */
public record SettlementContext(boolean settlesRemaining) {
}
