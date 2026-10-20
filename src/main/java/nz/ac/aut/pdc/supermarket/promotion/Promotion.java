package nz.ac.aut.pdc.supermarket.promotion;

import java.math.*;
import java.time.*;
import java.util.*;
import nz.ac.aut.pdc.shared.IdClock;

public record Promotion(String promotionId, Kind kind, BigDecimal payPercent, BigDecimal threshold,
                        BigDecimal reduction, Integer priority, String startsOn, String endsOn, Set<String> barcodes) {
    public enum Kind { PERCENT, REDUCTION }
    public Promotion {
        if (promotionId == null || promotionId.isBlank() || kind == null) throw new IllegalArgumentException("Activity ID/kind required");
        if (LocalDate.parse(startsOn).isAfter(LocalDate.parse(endsOn))) throw new IllegalArgumentException("Invalid activity dates");
        barcodes = Set.copyOf(barcodes);
        if (barcodes.isEmpty()) throw new IllegalArgumentException("Explicit applicable products required");
        if (kind == Kind.PERCENT) {
            if (payPercent == null || payPercent.compareTo(BigDecimal.ONE) < 0 || payPercent.compareTo(new BigDecimal("99")) > 0)
                throw new IllegalArgumentException("Pay percentage must be 1–99");
            payPercent = payPercent.stripTrailingZeros();
            if (threshold != null || reduction != null) throw new IllegalArgumentException("Unexpected reduction fields");
        } else {
            if (threshold == null || reduction == null || threshold.signum() <= 0 || reduction.signum() <= 0 || reduction.compareTo(threshold) > 0)
                throw new IllegalArgumentException("Invalid threshold/reduction");
            threshold = threshold.setScale(2, RoundingMode.UNNECESSARY); reduction = reduction.setScale(2, RoundingMode.UNNECESSARY);
            if (payPercent != null) throw new IllegalArgumentException("Unexpected pay percentage");
        }
    }
    public boolean active(Instant now) {
        Instant start = LocalDate.parse(startsOn).atStartOfDay(IdClock.BUSINESS_ZONE).toInstant();
        Instant followingMidnight = LocalDate.parse(endsOn).plusDays(1).atStartOfDay(IdClock.BUSINESS_ZONE).toInstant();
        return !now.isBefore(start) && now.isBefore(followingMidnight);
    }
}
