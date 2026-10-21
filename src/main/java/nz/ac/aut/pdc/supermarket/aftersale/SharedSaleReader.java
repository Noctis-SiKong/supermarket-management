package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.sales.Pricing;

/** A WorkingCopy 上的 B 原销售行读取适配器。 */
public final class SharedSaleReader implements OriginalSaleReader {

    private final WorkingCopy workingCopy;

    public SharedSaleReader(WorkingCopy workingCopy) {
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
    }

    @Override
    public Optional<OriginalSaleLine> readLine(String saleLineId) {
        if (saleLineId == null || saleLineId.isBlank()) {
            return Optional.empty();
        }
        var documentLineRecord = workingCopy.find("DOC_LINE", saleLineId).orElse(null);
        var saleLineRecord = workingCopy.find("SALE_LINE", saleLineId).orElse(null);
        if (documentLineRecord == null || saleLineRecord == null) {
            return Optional.empty();
        }
        DocumentLine documentLine = documentLineRecord.as(DocumentLine.class);
        var documentRecord = workingCopy.find("DOCUMENT", documentLine.documentId()).orElse(null);
        if (documentRecord == null) {
            return Optional.empty();
        }
        DocumentHeader document = documentRecord.as(DocumentHeader.class);
        if (document.confirmedAt() == null) {
            return Optional.empty();
        }
        Pricing.SaleLineSnapshot snapshot = saleLineRecord.as(Pricing.SaleLineSnapshot.class);
        return Optional.of(new OriginalSaleLine(
                saleLineId,
                documentLine.barcode(),
                documentLine.quantity(),
                snapshot.paidAmount(),
                Instant.parse(document.confirmedAt())));
    }
}
