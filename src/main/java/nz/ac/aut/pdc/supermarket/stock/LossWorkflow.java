package nz.ac.aut.pdc.supermarket.stock;

import java.time.*;
import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.infrastructure.SubmissionTime;

/** Disposition never deducts twice. Corrections retain the original and append a versioned difference. */
public final class LossWorkflow implements SubmissionPort.CommitValidator {
    public static final String DISPOSE="LOSS_DISPOSE", CORRECT="LOSS_CORRECT";
    public record Request(String lossId,int quantity,String disposition,String reason) {}
    public record Correction(String documentId,String lossId,long correctionVersion,int quantityDifference,String beforeAfterSnapshot) {}
    public static LossRecord effective(WorkingCopy wc,String id) {
        LossRecord original=wc.find("LOSS_RECORD",id).orElseThrow().as(LossRecord.class);
        return wc.records("LOSS_CORRECTION").stream().map(r -> r.as(Correction.class)).filter(c -> c.lossId().equals(id)
            && !wc.find("DOCUMENT",c.documentId()).orElseThrow().data().path("status").asText().equals("VOIDED"))
            .max(Comparator.comparingLong(Correction::correctionVersion)).map(c -> {var after=JsonCodec.read(JsonCodec.tree(c.beforeAfterSnapshot()).path("after").toString(),LossRecord.class);return new LossRecord(after.lossId(),after.sourceLineId(),after.barcode(),after.position(),after.quantity(),after.origin(),original.disposition(),original.dispositionState());}).orElse(original);
    }
    public ChangeSet prepare(OperationContext op,ActorContext actor,Request request,WorkingCopy wc,Instant time) {
        boolean correction=op.operation().equals(CORRECT);
        if(!correction && !op.operation().equals(DISPOSE)) throw new IllegalArgumentException("Wrong loss operation");
        if(correction?actor.role()!=Role.ADMIN:actor.role()!=Role.MANAGER && actor.role()!=Role.ADMIN)
            throw new IllegalArgumentException("FORBIDDEN");
        if(!Objects.equals(op.targetId(),request.lossId()) || request.reason()==null || request.reason().isBlank() || request.disposition()==null || request.disposition().isBlank())
            throw new IllegalArgumentException("INVALID_INPUT");
        var original=wc.find("LOSS_RECORD",request.lossId()).orElseThrow();
        var before=effective(wc,request.lossId());
        String parent=wc.find("DOC_LINE",before.sourceLineId()).orElseThrow().as(DocumentLine.class).documentId();
        if(wc.find("DOCUMENT",parent).orElseThrow().data().path("status").asText().equals("VOIDED")) throw new IllegalArgumentException("REFERENCE_INVALID");
        int quantity=correction?request.quantity():before.quantity();
        if(quantity<1 || quantity>9999 || before.origin()==LossOrigin.CUSTOMER_RETURN && quantity!=before.quantity()) throw new IllegalArgumentException("INVALID_INPUT");
        var after=new LossRecord(before.lossId(),before.sourceLineId(),before.barcode(),before.position(),quantity,before.origin(),request.disposition(),LossState.DISPOSED);
        var clock=new IdClock(Clock.fixed(time,ZoneOffset.UTC)); var out=new ChangeSet.Builder();
        String documentId=clock.entityId(op.requestKey(),"document"),eventId=clock.entityId(op.requestKey(),"event");
        if(!correction) {
            var header=wc.find("DOCUMENT",parent).orElseThrow();
            long version=wc.records("DOC_EVENT").stream().filter(r -> r.data().path("documentId").asText().equals(parent)).mapToLong(r -> r.data().path("version").asLong()).max().orElse(0)+1;
            out.patch(new ChangeSet.Patch("DOCUMENT",parent,header.version(),"{}"));
            out.patch(new ChangeSet.Patch("LOSS_RECORD",original.id(),original.version(),JsonCodec.write(Map.of("disposition",request.disposition(),"dispositionState",LossState.DISPOSED))));
            out.add(RecordData.of("DOC_EVENT",eventId,new DocEvent(eventId,parent,version,DISPOSE,actor.accountId(),null,JsonCodec.write(Map.of("lossId",request.lossId(),"reason",request.reason(),"before",before,"after",after)))));
        } else {
            out.patch(new ChangeSet.Patch("LOSS_RECORD",original.id(),original.version(),JsonCodec.write(Map.of("disposition",request.disposition(),"dispositionState",LossState.DISPOSED))));
            int diff=quantity-before.quantity();
            long version=wc.records("LOSS_CORRECTION").stream().map(r -> r.as(Correction.class)).filter(c -> c.lossId().equals(request.lossId())).mapToLong(Correction::correctionVersion).max().orElse(0)+1;
            out.add(RecordData.of("DOCUMENT",documentId,new DocumentHeader(documentId,"LOSS_CORRECTION","CONFIRMED",op.requestKey(),JsonCodec.fingerprint(op.operation(),actor.accountId(),BusinessRequest.of(request)),actor.accountId(),null,actor.accountId(),null,request.reason())));
            out.add(RecordData.of("LOSS_CORRECTION",documentId,new Correction(documentId,request.lossId(),version,diff,JsonCodec.write(Map.of("before",before,"after",after)))));
            out.add(RecordData.of("DOC_EVENT",eventId,new DocEvent(eventId,documentId,1,CORRECT,actor.accountId(),null,JsonCodec.write(Map.of("lossId",request.lossId())))));
            String line=clock.entityId(op.requestKey(),"line");
            out.add(RecordData.of("DOC_LINE",line,new DocumentLine(line,documentId,"LOSS_CORRECTION",before.barcode(),Math.max(1,Math.abs(diff)))));
            if(diff!=0) {
                String stock=clock.entityId(op.requestKey(),"stock");
                out.add(RecordData.of("STOCK_ENTRY",stock,new nz.ac.aut.pdc.shared.StockEntry(stock,line,nz.ac.aut.pdc.shared.StockEntry.Position.valueOf(before.position().name()),-diff,op.requestKey()+":loss-difference",null,null,null,0)));
            }
        }
        return SubmissionTime.assign(wc,out.build(),time);
    }
    public Result<Void> validate(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy wc,ChangeSet delta,Instant time) {
        try {
            var expected=prepare(op,actor,request.as(Request.class),wc,time);
            if(!CandidateEquality.same(expected,delta)) return Result.rejected("REFERENCE_INVALID","Unexpected loss effects");
            return new SharedStockAdapter().review(wc,request,delta);
        } catch(RuntimeException e) {return Result.rejected("FORBIDDEN".equals(e.getMessage())?"FORBIDDEN":"INVALID_INPUT","Invalid loss disposition/correction");}
    }
}
