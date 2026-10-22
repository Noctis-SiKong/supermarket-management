package nz.ac.aut.pdc.supermarket.stock;
import java.time.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.infrastructure.AbstractReconstructingCommitValidator;
/** Exact reconstruction rejects patches, removals, unrelated facts and altered ledger metadata. */
public final class StockOperationValidator extends AbstractReconstructingCommitValidator {
    private final String operation;
    public StockOperationValidator(String operation){this.operation=operation;}
    @Override
    protected Result<ChangeSet> reconstruct(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy wc,Instant time) {
        if(!operation.equals(op.operation()))return Result.rejected("INVALID_INPUT","Wrong stock operation");
        return StockBusiness.prepare(op,actor,request,wc,new IdClock(Clock.fixed(time,ZoneOffset.UTC)));
    }
    @Override
    protected Result<Void> mismatch() { return Result.rejected("REFERENCE_INVALID","Complete stock effects differ from original request"); }
}
