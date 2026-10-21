package nz.ac.aut.pdc.supermarket.stock;

import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.supermarket.cui.*;

/** One request-to-effects reconstruction shared by CUI and IF-05; stock rules stay in StockValidator. */
public final class StockBusiness {
    private StockBusiness() {}
    public static Result<ChangeSet> prepare(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy wc,IdClock clock) {
        try {
            boolean replenish=op.operation().equals(StockChangeSetAssembler.REPLENISHMENT);
            if(!replenish && !op.operation().equals(StockChangeSetAssembler.LOSS))return Result.rejected("INVALID_INPUT","Wrong stock operation");
            if(replenish?actor.role()!=Role.CLERK && actor.role()!=Role.MANAGER:actor.role()!=Role.MANAGER)
                return Result.rejected("FORBIDDEN","Role cannot perform this stock operation");
            Map<?,?> body=request.as(Map.class);
            Set<String> fields=replenish?Set.of("barcode","quantity"):Set.of("barcode","quantity","position","reason");
            if(!fields.equals(body.keySet()))return Result.rejected("INVALID_INPUT","Stock request fields differ from the operation");
            String barcode=Objects.toString(body.get("barcode"),null);
            int quantity=new java.math.BigDecimal(body.get("quantity").toString()).intValueExact();
            var ids=new SharedIdGenerator(clock,op.requestKey());
            var stock=new StockService(new SharedProductCatalog(wc),ids,new StockValidator());
            var assembler=new StockChangeSetAssembler();
            String document=clock.entityId(op.requestKey(),"document");
            if(!Objects.equals(op.targetId(),document))return Result.rejected("REFERENCE_INVALID","Stock target ID must be stable for original key");
            if(replenish) {
                var result=new ReplenishmentService(stock,ids).replenish(new SharedWorkingCopyView(wc),List.of(),barcode,quantity);
                if(result instanceof ReplenishmentResult.Rejected r)return Result.rejected(r.code(),r.message());
                if(result instanceof ReplenishmentResult.Failed r)return Result.failed(r.code(),r.message());
                return Result.prepared(assembler.assembleReplenishment((ReplenishmentResult.Prepared)result,document,ids.nextId("LINE"),op,actor,request));
            }
            String reason=Objects.toString(body.get("reason"),"");
            if(reason.isBlank())return Result.rejected("INVALID_INPUT","Loss reason required");
            var result=new LossService(stock,ids).recordLoss(new SharedWorkingCopyView(wc),List.of(),barcode,Position.valueOf(body.get("position").toString()),quantity);
            if(result instanceof LossResult.Rejected r)return Result.rejected(r.code(),r.message());
            if(result instanceof LossResult.Failed r)return Result.failed(r.code(),r.message());
            return Result.prepared(assembler.assembleLoss((LossResult.Prepared)result,document,ids.nextId("LINE"),op,actor,request));
        } catch(RuntimeException e) {return Result.rejected("INVALID_INPUT","Invalid stock quantity, barcode, identity or source");}
    }
}
