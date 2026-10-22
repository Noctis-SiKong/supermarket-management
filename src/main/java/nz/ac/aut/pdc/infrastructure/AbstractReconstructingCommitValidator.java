package nz.ac.aut.pdc.infrastructure;

import java.time.Instant;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext.ActorContext;

/** Reconstruct, assign managed time, compare the complete delta, then review.
 * Exception policy stays with each concrete validator; this class never saves.
 */
public abstract class AbstractReconstructingCommitValidator implements SubmissionPort.CommitValidator {
    @Override
    public Result<Void> validate(OperationContext op, ActorContext actor, BusinessRequest request,
                                 WorkingCopy baseline, ChangeSet actual, Instant acceptedAt) {
        Result<ChangeSet> expected = reconstruct(op, actor, request, baseline, acceptedAt);
        if (!expected.hasValue()) return expected.failure();
        ChangeSet timed = SubmissionTime.assign(baseline, expected.value(), acceptedAt);
        if (!CandidateEquality.same(timed, actual)) return mismatch();
        return review(op, actor, request, baseline, actual, acceptedAt);
    }

    protected abstract Result<ChangeSet> reconstruct(OperationContext op, ActorContext actor,
            BusinessRequest request, WorkingCopy baseline, Instant acceptedAt);

    protected abstract Result<Void> mismatch();

    /** Optional domain review runs only after complete equality, not a deletion policy. */
    protected Result<Void> review(OperationContext op, ActorContext actor, BusinessRequest request,
                                  WorkingCopy baseline, ChangeSet actual, Instant acceptedAt) {
        return Result.value(null);
    }
}
