package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuotaCalculatorTest {

    private final QuotaCalculator calculator = new QuotaCalculator();

    @Test
    public void proportionalAmountUsesHalfUpTwoDecimals() {
        QuotaView view = view(3, "10.00", 3, "10.00");

        assertEquals(new BigDecimal("3.33"), calculator.allocate(1, view, new SettlementContext(false)));
    }

    @Test
    public void settlingAllocationUsesAllRemainingAmount() {
        QuotaView view = view(3, "10.00", 1, "3.34");

        assertEquals(new BigDecimal("3.34"), calculator.allocate(1, view, new SettlementContext(true)));
    }

    @Test
    public void proportionalAmountIsCappedByRemainingAmount() {
        QuotaView view = view(3, "10.00", 3, "1.00");

        assertEquals(new BigDecimal("1.00"), calculator.allocate(1, view, new SettlementContext(false)));
    }

    @Test
    public void settleFlagRequiresFullRemainingQuantity() {
        QuotaView view = view(3, "10.00", 2, "6.67");

        try {
            calculator.allocate(1, view, new SettlementContext(true));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void quantityAboveRemainingIsRejected() {
        QuotaView view = view(3, "10.00", 1, "3.34");

        try {
            calculator.allocate(2, view, new SettlementContext(false));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private QuotaView view(int originalQty, String originalPaid, int remainingQty, String remainingAmount) {
        return new QuotaView(
                "SL-1",
                originalQty,
                new BigDecimal(originalPaid),
                originalQty - remainingQty,
                new BigDecimal(originalPaid).subtract(new BigDecimal(remainingAmount)),
                0,
                BigDecimal.ZERO,
                remainingQty,
                new BigDecimal(remainingAmount));
    }
}
