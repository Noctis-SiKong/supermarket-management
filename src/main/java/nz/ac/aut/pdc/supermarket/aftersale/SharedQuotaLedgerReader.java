package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.shared.WorkingCopy;

/** Read-only B redemption-history adapter over A WorkingCopy.
  */
public final class SharedQuotaLedgerReader implements QuotaLedgerReader {

    private final WorkingCopy workingCopy;

    public SharedQuotaLedgerReader(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public List<Redemption> effectiveFor(String saleLineId) {
        return workingCopy.records("REDEMPTION").stream()
                .map(record -> record.as(Redemption.class))
                .filter(redemption -> redemption.originalSaleLineId().equals(saleLineId))
                .toList();
    }
}
