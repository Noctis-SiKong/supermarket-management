package nz.ac.aut.pdc.supermarket.stock;
import java.time.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.infrastructure.SubmissionTime;
/** Exact reconstruction rejects patches, removals, unrelated facts and altered ledger metadata. */
public final class StockOperationValidator implements SubmissionPort.CommitValidator {
    private final String operation;
    public StockOperationValidator(String operation){this.operation=operation;}
    public Result<Void> validate(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy wc,ChangeSet delta,Instant time) {
        if(!operation.equals(op.operation()))return Result.rejected("INVALID_INPUT","Wrong stock operation");
        var expected=StockBusiness.prepare(op,actor,request,wc,new IdClock(Clock.fixed(time,ZoneOffset.UTC)));
        if(!expected.hasValue())return expected.failure();
        return CandidateEquality.same(SubmissionTime.assign(wc,expected.value(),time),delta)?Result.value(null):Result.rejected("REFERENCE_INVALID","Complete stock effects differ from original request");
    }
}
