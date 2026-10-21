package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.Instant;
import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;

/** Target lifecycle authorization only; OriginalSaleAccess owns the sole IF-11 admission. */
public final class AftersaleDraftAccess implements OriginalSaleAccess.DraftAccess {
    public Result<ChangeSet> prepareTarget(OperationContext op,ActorContext actor,AftersaleReadRequest request,WorkingCopy wc) {
        if(wc.find("DOCUMENT",request.aftersaleId()).isPresent()) {
            var check=new AftersaleDraftValidator(AftersaleDraftOperations.READ).requireOwnedDraft(op,actor,wc);
            return check.hasValue()?Result.prepared(ChangeSet.empty()):check.failure();
        }
        var original=JsonCodec.BusinessRequest.of(request);
        var header=new DocumentHeader(request.aftersaleId(),"AFTERSALE","DRAFT",op.requestKey(),
            JsonCodec.fingerprint(op.operation(),actor.accountId(),original),actor.accountId(),null,null,null,null);
        return Result.prepared(new ChangeSet(List.of(RecordData.of("DOCUMENT",request.aftersaleId(),header),
            RecordData.of("AFTERSALE",request.aftersaleId(),Map.of())),List.of()));
    }
    public Result<Void> reviewTarget(OperationContext op,ActorContext actor,AftersaleReadRequest request,WorkingCopy wc,ChangeSet complete,Instant time) {
        if(wc.find("DOCUMENT",request.aftersaleId()).isPresent())
            return new AftersaleDraftValidator(AftersaleDraftOperations.READ).requireOwnedDraft(op,actor,wc);
        return new AftersaleDraftValidator(AftersaleDraftOperations.CREATE).validateTargetCreation(op,actor,wc,complete);
    }
}
