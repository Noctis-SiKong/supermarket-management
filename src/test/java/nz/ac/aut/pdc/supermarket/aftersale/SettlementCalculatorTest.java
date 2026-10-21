package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    public void pureReturnRefundsRedemptionAmount() {
        Redemption redemption = redemption("8.00");
        Settlement settlement = calculator.calculate(redemption, null);

        assertEquals(SettlementDirection.REFUND, settlement.direction());
        assertEquals(new BigDecimal("8.00"), settlement.amount());
    }

    @Test
    public void exchangeWithHigherReplacementChargesDifference() {
        Settlement settlement = calculator.calculate(redemption("8.00"), replacement("10.00"));

        assertEquals(SettlementDirection.CHARGE, settlement.direction());
        assertEquals(new BigDecimal("2.00"), settlement.amount());
    }

    @Test
    public void exchangeWithLowerReplacementRefundsDifference() {
        Settlement settlement = calculator.calculate(redemption("8.00"), replacement("5.00"));

        assertEquals(SettlementDirection.REFUND, settlement.direction());
        assertEquals(new BigDecimal("3.00"), settlement.amount());
    }

    @Test
    public void qualitySameItemHasNoDifference() {
        Settlement settlement = calculator.calculate(redemption("8.00"), replacement("8.00"));

        assertEquals(SettlementDirection.NONE, settlement.direction());
        assertEquals(BigDecimal.ZERO, settlement.amount());
    }

    private Redemption redemption(String amount) {
        return new Redemption(
                "R-1", "DL-1", "SL-1", "A", "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal(amount),
                "REQ-1", null, false, false);
    }

    private Replacement replacement(String amount) {
        return new Replacement(
                "RL-1", "AF-1", "R-1", "A", 1,
                "商品", new BigDecimal(amount), new BigDecimal(amount), "NORMAL", null);
    }
}
