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
                RecordData.of("DOCUMENT","S-1",new DocumentHeader("S-1","SALE","CONFIRMED","sale-key","fp","acc-1","2026-10-21T04:00:00Z","acc-1","2026-10-21T04:00:00Z",null)),
                RecordData.of("SALE","S-1",java.util.Map.of()),
                RecordData.of("DOC_LINE","SL-1",new nz.ac.aut.pdc.shared.DocumentLine("SL-1","S-1","SALE","6901234567890",3)),
                RecordData.of("SALE_LINE","SL-1",new nz.ac.aut.pdc.supermarket.sales.Pricing.SaleLineSnapshot("SL-1","虚构商品",new BigDecimal("3.33"),new BigDecimal("9.99"),null,false,BigDecimal.ZERO,new BigDecimal("9.99"))),
                RecordData.of("DOC_LINE","R-1",new nz.ac.aut.pdc.shared.DocumentLine("R-1","AF-1","RETURN","6901234567890",1)),
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
