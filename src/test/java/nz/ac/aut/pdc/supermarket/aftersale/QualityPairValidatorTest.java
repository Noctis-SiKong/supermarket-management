package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class QualityPairValidatorTest {

    private final QualityPairValidator validator = new QualityPairValidator();
    private final OriginalSaleLine line = new OriginalSaleLine(
            "SL-1", "A", 1, new BigDecimal("8.00"), Instant.parse("2026-10-01T00:00:00Z"));

    @Test
    public void validQualityPairIsAccepted() {
        Redemption redemption = redemption(true, RedemptionKind.EXCHANGE, Effect.CONSUME, "8.00");
        Replacement replacement = replacement("A", 1, "8.00", redemption.redemptionId());

        assertTrue(validator.validate(line, redemption, replacement).isEmpty());
    }

    @Test
    public void differentBarcodeIsRejected() {
        Redemption redemption = redemption(true, RedemptionKind.EXCHANGE, Effect.CONSUME, "8.00");
        Replacement replacement = replacement("B", 1, "8.00", redemption.redemptionId());

        assertTrue(validator.validate(line, redemption, replacement).isPresent());
    }

    @Test
    public void differentQuantityIsRejected() {
        Redemption redemption = redemption(true, RedemptionKind.EXCHANGE, Effect.CONSUME, "8.00");
        Replacement replacement = replacement("A", 2, "16.00", redemption.redemptionId());

        assertTrue(validator.validate(line, redemption, replacement).isPresent());
    }

    @Test
    public void nonZeroDifferenceIsRejected() {
        Redemption redemption = redemption(true, RedemptionKind.EXCHANGE, Effect.CONSUME, "8.00");
        Replacement replacement = replacement("A", 1, "9.00", redemption.redemptionId());

        assertTrue(validator.validate(line, redemption, replacement).isPresent());
    }

    @Test
    public void nonQualityProblemIsRejected() {
        Redemption redemption = redemption(false, RedemptionKind.EXCHANGE, Effect.CONSUME, "8.00");
        Replacement replacement = replacement("A", 1, "8.00", redemption.redemptionId());

        assertTrue(validator.validate(line, redemption, replacement).isPresent());
    }

    private Redemption redemption(boolean qualityProblem, RedemptionKind kind, Effect effect, String amount) {
        return new Redemption(
                "R-1", "DL-1", line.saleLineId(), line.barcode(), "AF-1",
                kind, effect, 1, new BigDecimal(amount), "REQ-1", null, false, qualityProblem);
    }

    private Replacement replacement(String barcode, int quantity, String amount, String qualityRedemptionId) {
        return new Replacement(
                "RL-1", "AF-1", "R-1", barcode, quantity,
                "虚构商品", new BigDecimal(amount), new BigDecimal(amount), "QUALITY", qualityRedemptionId);
    }
}
