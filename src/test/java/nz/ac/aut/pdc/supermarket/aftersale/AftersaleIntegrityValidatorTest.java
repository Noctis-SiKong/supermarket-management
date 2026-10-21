package nz.ac.aut.pdc.supermarket.aftersale;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.WorkingCopy;

public class AftersaleIntegrityValidatorTest {

    private final AftersaleIntegrityValidator validator = new AftersaleIntegrityValidator();

    @Test
    public void validAftersaleGraphPasses() throws Exception {
        validator.validate(new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("DOCUMENT", "AF-1", new DocumentHeader(
                        "AF-1", "AFTERSALE", "CONFIRMED", "REQ-1", "FP", "acc-1",
                        "2026-10-21T04:00:00Z", "acc-1", "2026-10-21T04:00:00Z", null)),
                RecordData.of("AFTERSALE", "AF-1", java.util.Map.of()),
                RecordData.of("REDEMPTION", "R-1", new Redemption(
                        "R-1", "R-1", "SL-1", "6901234567890", "AF-1",
                        RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                        "REQ-1", null, false, false, "其他原因")),
                RecordData.of("MONEY_ENTRY", "M-1", new MoneyEntry(
                        "M-1", "AF-1", new BigDecimal("-3.33"), "REQ-1", null)))));
    }

    @Test
    public void zeroAmountMoneyIsAllowed() throws Exception {
        validator.validate(new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("DOCUMENT", "AF-1", new DocumentHeader(
                        "AF-1", "AFTERSALE", "CONFIRMED", "REQ-1", "FP", "acc-1",
                        "2026-10-21T04:00:00Z", "acc-1", "2026-10-21T04:00:00Z", null)),
                RecordData.of("AFTERSALE", "AF-1", java.util.Map.of()),
                RecordData.of("MONEY_ENTRY", "M-1", new MoneyEntry(
                        "M-1", "AF-1", BigDecimal.ZERO, "REQ-1", null)))));
    }

    @Test(expected = IOException.class)
    public void redemptionWithoutDocumentFails() throws Exception {
        validator.validate(new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("REDEMPTION", "R-1", new Redemption(
                        "R-1", "R-1", "SL-1", "6901234567890", "AF-1",
                        RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                        "REQ-1", null, false, false, "其他原因")))));
    }
}
