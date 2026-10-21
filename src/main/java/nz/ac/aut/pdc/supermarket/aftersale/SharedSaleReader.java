package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.sales.Pricing;

/** Read-only B original-sale adapter over A WorkingCopy.
  */
public final class SharedSaleReader implements OriginalSaleReader {

    private final WorkingCopy workingCopy;

    public SharedSaleReader(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public Optional<OriginalSaleLine> readLine(String saleLineId) {
        return nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess.originalLine(workingCopy,saleLineId)
            .map(s -> new OriginalSaleLine(s.lineId(),s.barcode(),s.originalQuantity(),s.paidAmount(),Instant.parse(s.soldAt())));
    }
}
