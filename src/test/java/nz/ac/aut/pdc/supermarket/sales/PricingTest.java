package nz.ac.aut.pdc.supermarket.sales;

import nz.ac.aut.pdc.supermarket.promotion.Promotion;
import org.junit.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class PricingTest {
    final Instant now = Instant.parse("2026-10-20T12:00:00Z");
    Promotion reduction(String id, String threshold, String amount, Integer priority, String... barcodes) {
        return new Promotion(id,Promotion.Kind.REDUCTION,null,new BigDecimal(threshold),new BigDecimal(amount),priority,"2026-10-20","2026-10-20",Set.of(barcodes));
    }
    Pricing.Line line(String id, String barcode, String price) { return new Pricing.Line(id,barcode,"虚构商品",new BigDecimal(price),1); }
    @Test public void stableIdBreaksRemainderTiesRegardlessOfArrayOrderAndExcludesInapplicableLine() {
        List<Pricing.Line> lines = List.of(line("d","A","0.01"),line("b","A","0.01"),line("a","A","0.01"),line("c","A","0.01"),line("e","B","1.00"));
        Pricing.Quote result = new Pricing().quote(lines,List.of(reduction("p","0.04","0.02",null,"A")),now);
        Map<String,BigDecimal> discounts = new HashMap<>(); result.lines().forEach(l -> discounts.put(l.lineId(),l.discountAmount()));
        assertEquals(new BigDecimal("0.01"), discounts.get("a")); assertEquals(new BigDecimal("0.01"), discounts.get("b"));
        assertEquals(new BigDecimal("0.00"), discounts.get("c")); assertEquals(new BigDecimal("0.00"), discounts.get("d"));
        assertEquals(new BigDecimal("0.00"), discounts.get("e")); assertEquals(new BigDecimal("1.02"),result.paidTotal());
    }
    @Test public void singleActivityBestThenExplicitPriorityThenSmallerIdAndInclusiveDates() {
        var lines = List.of(line("a","A","10.00"));
        Promotion low = reduction("a","5.00","1.00",99,"A"), best = reduction("z","5.00","2.00",null,"A");
        assertEquals("z",new Pricing().quote(lines,List.of(low,best),now).promotion().promotionId());
        Promotion tie = reduction("b","5.00","2.00",1,"A"), smaller = reduction("a","5.00","2.00",1,"A");
        assertEquals("a",new Pricing().quote(lines,List.of(best,tie,smaller),now).promotion().promotionId());
        assertTrue(best.active(Instant.parse("2026-10-19T16:00:00Z")));
        assertTrue(best.active(Instant.parse("2026-10-20T15:59:59Z")));
        assertTrue(best.active(Instant.parse("2026-10-20T15:59:59.999999999Z")));
        assertFalse(best.active(Instant.parse("2026-10-20T16:00:00Z")));
    }
    @Test public void percentageRoundsOrderToCentsAndZeroGrossNeverDividesByZero() {
        Promotion percent = new Promotion("p",Promotion.Kind.PERCENT,new BigDecimal("50"),null,null,null,"2026-10-20","2026-10-20",Set.of("A"));
        Pricing.Quote quote = new Pricing().quote(List.of(line("a","A","0.01")),List.of(percent),now);
        assertEquals(new BigDecimal("0.01"),quote.paidTotal());
        assertEquals(new BigDecimal("0.00"),new Pricing().quote(List.of(line("a","A","0.00")),List.of(percent),now).paidTotal());
    }
}
