package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuotaViewBuilderTest {

    private final QuotaViewBuilder builder = new QuotaViewBuilder();
    private final OriginalSaleLine line = new OriginalSaleLine(
            "SL-1", "A", 3, new BigDecimal("10.00"), Instant.parse("2026-10-01T00:00:00Z"));

    @Test
    public void aggregatesReturnAndExchangeConsumptions() {
        List<Redemption> history = List.of(
                redemption("R-1", RedemptionKind.RETURN, Effect.CONSUME, 1, "3.33", null),
                redemption("R-2", RedemptionKind.EXCHANGE, Effect.CONSUME, 1, "3.33", null));

        QuotaView view = builder.build(line, history);

        assertEquals(1, view.returnedQty());
        assertEquals(new BigDecimal("3.33"), view.returnedAmount());
        assertEquals(1, view.exchangedQty());
        assertEquals(new BigDecimal("3.33"), view.exchangedAmount());
        assertEquals(1, view.remainingQty());
        assertEquals(new BigDecimal("3.34"), view.remainingAmount());
    }

    @Test
    public void releaseSubtractsFromConsumption() {
        List<Redemption> history = List.of(
                redemption("R-1", RedemptionKind.RETURN, Effect.CONSUME, 1, "3.33", null),
                redemption("R-2", RedemptionKind.RETURN, Effect.RELEASE, 1, "3.33", "R-1"));

        QuotaView view = builder.build(line, history);

        assertEquals(0, view.returnedQty());
        assertEquals(new BigDecimal("0.00"), view.returnedAmount());
        assertEquals(3, view.remainingQty());
        assertEquals(new BigDecimal("10.00"), view.remainingAmount());
    }

    @Test
    public void historyThatOverdrawsQuotaIsRejected() {
        List<Redemption> history = List.of(
                redemption("R-1", RedemptionKind.RETURN, Effect.CONSUME, 4, "10.00", null));

        try {
            builder.build(line, history);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private Redemption redemption(
            String id,
            RedemptionKind kind,
            Effect effect,
            int quantity,
            String amount,
            String reverseOf) {
        return new Redemption(
                id,
                "DL-1",
                line.saleLineId(),
                line.barcode(),
                "AF-1",
                kind,
                effect,
                quantity,
                new BigDecimal(amount),
                "REQ-1",
                reverseOf,
                false,
                false);
    }
}
