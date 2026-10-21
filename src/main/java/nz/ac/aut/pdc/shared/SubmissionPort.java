package nz.ac.aut.pdc.shared;

import java.util.List;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext.ActorContext;

/** v1.0.4: provider A, consumer A/B coordinators only. */
public interface SubmissionPort {
    Result<WorkingCopy> open(OperationContext op);
    Result<CommitReceipt> submit(OperationContext op, BusinessRequest request, WorkingCopy wc, ChangeSet changes);
    Result<CommitReceipt> lookupResult(OperationContext op, BusinessRequest request);
    record CommitReceipt(String requestKey, List<String> resultIds, long commitOrdinal, boolean replayed) {
        public CommitReceipt { resultIds = List.copyOf(resultIds); }
        public CommitReceipt replay() { return new CommitReceipt(requestKey, resultIds, commitOrdinal, true); }
    }
    /** Owner validates authorization and request/effect consistency under the single writer lock. */
    @FunctionalInterface interface CommitValidator {
        Result<Void> validate(OperationContext op, ActorContext actor, BusinessRequest request,
                              WorkingCopy baseline, ChangeSet changes, java.time.Instant acceptedAt);
    }
}
