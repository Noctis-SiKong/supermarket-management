package nz.ac.aut.pdc.shared;

import java.util.List;

/** Internal B coordinator result. Only lines/trace summary are projected to CUI; never the WorkingCopy. */
public record AftersaleReadAccess(SubmissionPort.CommitReceipt trace, String eventId,
                                 WorkingCopy workingCopy, List<SaleLineSnapshot> lines) {
    public AftersaleReadAccess { lines = List.copyOf(lines); }
}
