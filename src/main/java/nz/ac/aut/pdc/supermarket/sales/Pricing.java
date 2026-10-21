package nz.ac.aut.pdc.supermarket.sales;

import java.math.*;
import java.time.Instant;
import java.util.*;
import nz.ac.aut.pdc.supermarket.promotion.Promotion;

/** Spec §7.3: integer-cent candidates; exact largest remainder; no activity stacking. */
public final class Pricing {
    public record Line(String lineId, String barcode, String productName, BigDecimal unitPrice, int quantity) {
        public Line {
            if (lineId == null || lineId.isBlank() || quantity < 1 || quantity > 9999 || unitPrice == null || unitPrice.signum() < 0)
                throw new IllegalArgumentException("Invalid sale line");
        }
        public long grossCents() { return cents(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)); }
    }
    public record SaleLineSnapshot(String lineId, String productNameSnapshot, BigDecimal unitPriceSnapshot,
        BigDecimal grossAmount, Promotion promotionSnapshot, boolean promotionApplicable, BigDecimal discountAmount, BigDecimal paidAmount) {}
    public record Quote(Promotion promotion, BigDecimal grossTotal, BigDecimal discountTotal,
                        BigDecimal paidTotal, List<SaleLineSnapshot> lines) {
        public Quote { lines = List.copyOf(lines); }
    }
    private record Candidate(Promotion promotion, long discount) {}
    public Quote quote(List<Line> lines, Collection<Promotion> promotions, Instant now) {
        if (lines.isEmpty() || lines.stream().map(Line::lineId).distinct().count() != lines.size()) throw new IllegalArgumentException("Sale requires unique stable lines");
        long gross = 0; for (Line line : lines) gross = Math.addExact(gross, line.grossCents());
        List<Candidate> candidates = new ArrayList<>();
        for (Promotion p : promotions) {
            if (!p.active(now)) continue;
            long applicable = 0;
            for (Line line : lines) if (p.barcodes().contains(line.barcode())) applicable = Math.addExact(applicable, line.grossCents());
            if (applicable == 0 || p.kind() == Promotion.Kind.REDUCTION && applicable < cents(p.threshold())) continue;
            long discount = p.kind() == Promotion.Kind.PERCENT
                ? applicable - BigDecimal.valueOf(applicable).multiply(p.payPercent()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValueExact()
                : applicable >= cents(p.threshold()) ? cents(p.reduction()) : 0;
            candidates.add(new Candidate(p, discount));
        }
        candidates.sort(Comparator.comparingLong(Candidate::discount).reversed()
            .thenComparing(c -> c.promotion().priority(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(c -> c.promotion().promotionId()));
        Candidate selected = candidates.isEmpty() ? new Candidate(null, 0) : candidates.get(0);
        Promotion promotion = selected.promotion(); long discount = selected.discount();
        long weight = 0; for (Line line : lines) if (promotion != null && promotion.barcodes().contains(line.barcode())) weight = Math.addExact(weight, line.grossCents());
        Map<String, Long> amounts = new HashMap<>(); Map<String, BigInteger> remainders = new HashMap<>();
        long allocated = 0;
        if (discount > 0) {
            if (weight <= 0) throw new IllegalArgumentException("Positive discount needs positive weight");
            for (Line line : lines) if (promotion.barcodes().contains(line.barcode())) {
                BigInteger[] qr = BigInteger.valueOf(discount).multiply(BigInteger.valueOf(line.grossCents())).divideAndRemainder(BigInteger.valueOf(weight));
                amounts.put(line.lineId(), qr[0].longValueExact()); remainders.put(line.lineId(), qr[1]); allocated += qr[0].longValueExact();
            }
            List<String> order = new ArrayList<>(remainders.keySet());
            order.sort(Comparator.<String, BigInteger>comparing(remainders::get).reversed().thenComparing(Comparator.naturalOrder()));
            for (int i = 0; i < discount - allocated; i++) amounts.merge(order.get(i), 1L, Long::sum);
        }
        List<SaleLineSnapshot> snapshots = new ArrayList<>(); long sumDiscount = 0, sumPaid = 0;
        for (Line line : lines) {
            long d = amounts.getOrDefault(line.lineId(), 0L), g = line.grossCents();
            if (d < 0 || d > g) throw new IllegalArgumentException("Allocation invariant failed");
            sumDiscount = Math.addExact(sumDiscount, d); sumPaid = Math.addExact(sumPaid, g-d);
            snapshots.add(new SaleLineSnapshot(line.lineId(), line.productName(), line.unitPrice(), money(g), promotion,
                promotion != null && promotion.barcodes().contains(line.barcode()), money(d), money(g-d)));
        }
        if (sumDiscount != discount || sumPaid != gross-discount) throw new IllegalArgumentException("Order conservation failed");
        return new Quote(promotion, money(gross), money(discount), money(gross-discount), snapshots);
    }
    public static long cents(BigDecimal amount) { return amount.movePointRight(2).longValueExact(); }
    public static BigDecimal money(long cents) { return BigDecimal.valueOf(cents, 2); }
}
