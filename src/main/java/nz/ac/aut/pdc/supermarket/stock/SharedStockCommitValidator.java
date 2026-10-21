package nz.ac.aut.pdc.supermarket.stock;

import java.time.Instant;
import java.util.Objects;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B six-argument CommitValidator rechecks complete stock candidates under A's lock.
  */
public final class SharedStockCommitValidator implements SubmissionPort.CommitValidator {

    private final SharedStockAdapter stockAdapter;

    public SharedStockCommitValidator(SharedStockAdapter stockAdapter) {
        this.stockAdapter = Objects.requireNonNull(stockAdapter, "stockAdapter");
    }

    @Override
    public Result<Void> validate(
            OperationContext op,
            OperationContext.ActorContext actor,
            BusinessRequest request,
            WorkingCopy baseline,
            ChangeSet changes,
            Instant acceptedAt) {
        return stockAdapter.review(baseline, request, changes);
    }
}
