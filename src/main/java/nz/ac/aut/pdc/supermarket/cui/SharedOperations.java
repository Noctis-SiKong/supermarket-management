package nz.ac.aut.pdc.supermarket.cui;

import java.util.List;
import nz.ac.aut.pdc.supermarket.i18n.Texts;
import static nz.ac.aut.pdc.supermarket.i18n.Texts.*;
import java.util.Map;
import java.util.Objects;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleChangeSetAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommand;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommandLine;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleDraftOperations;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleDraftService;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleService;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleSubmissionService;
import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleLine;
import nz.ac.aut.pdc.supermarket.aftersale.QualityPairValidator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaCalculator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.SharedProductSnapshotReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedQuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedSaleReader;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.auth.SharedRoles;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

/** Unified real CUI adapter: all commits through JsonStore; quotes, authorized projections, original-key retry and business operations use real services.
 *
 *
 * 完整 CUI 的统一业务适配器：所有提交只走 JsonStore。
 * 报价、受限投影、原键重试与所有业务入口使用真实服务。
 */
public final class SharedOperations implements MaintenanceOperations {

    private final JsonStore store;
    private final IdClock idClock;

    public SharedOperations(JsonStore store, IdClock idClock) {
        this.store = Objects.requireNonNull(store, "store");
        this.idClock = Objects.requireNonNull(idClock, "idClock");
    }

    private record Quoted(String actor,WorkingCopy wc,BusinessRequest request,ChangeSet delta) {}
    private final Map<String,Quoted> quotes=new java.util.HashMap<>();
    @Override public OperationFeedback quoteSale(CuiActor actor,List<nz.ac.aut.pdc.supermarket.app.AOperations.Item> items,String key) {
        var context=new OperationContext(actor.session(),key,"SALE",null);
        var identity=store.authenticate(context);if(!identity.hasValue())return rejected(identity.code());
        var a=new nz.ac.aut.pdc.supermarket.app.AOperations(idClock,s -> store.authenticate(new OperationContext(s,key,"SALE",null)),new nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter());
        var candidate=a.prepare(store,context,BusinessRequest.of(new nz.ac.aut.pdc.supermarket.app.AOperations.Sale(items,true)));
        if(!candidate.hasValue())return rejected(candidate.code());
        var prepared=candidate.value();quotes.put(key,new Quoted(identity.value().accountId(),prepared.workingCopy(),prepared.request(),prepared.changes()));
        var sale=prepared.changes().inserts().stream().filter(r -> r.table().equals("SALE")).findFirst().orElseThrow().as(nz.ac.aut.pdc.supermarket.app.AOperations.SaleRecord.class);
        return OperationFeedback.shown(true, message("feedback.quote",data(sale.quote()),key));
    }
    @Override public OperationFeedback quoteAftersale(CuiActor actor,List<AftersaleRequest> lines,String key) {return aftersaleOnce(actor,lines,key,false);}
    private record Pending(String accountId,java.util.function.Supplier<OperationFeedback> retry) {}
    private final Map<String,Pending> pending=new java.util.HashMap<>();
    private OperationFeedback remember(CuiActor actor,String key,java.util.function.Supplier<OperationFeedback> action) {
        var identity=store.authenticate(new OperationContext(actor.session(),key,"AUTH",null));
        if(!identity.hasValue()) return rejected(identity.code());
        pending.put(key,new Pending(identity.value().accountId(),action));
        var result=action.get();
        return OperationFeedback.shown(result.success(), t -> t.text("feedback.key", result.render(t), key));
    }
    @Override public OperationFeedback retry(CuiActor actor,String key) {
        var identity=store.authenticate(new OperationContext(actor.session(),key,"AUTH",null));
        var saved=pending.get(key);
        if(!identity.hasValue() || saved==null || !saved.accountId().equals(identity.value().accountId()))
            return notice(false,"feedback.retryMissing");
        var result=saved.retry().get();
        return OperationFeedback.shown(result.success(),t -> t.text("feedback.key",result.render(t),key));
    }
    @Override public OperationFeedback executeA(CuiActor actor,String kind,Object input,String key) {
        String requestKey=key==null?idClock.newRequestKey():key;
        OperationContext context=new OperationContext(actor.session(),requestKey,kind,null);
        var opened=store.open(context); if(!opened.hasValue()) return rejected(opened.code());
        WorkingCopy wc=opened.value(); Object resolved=input;
        if(input instanceof nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut p && p.expectedVersion()<0) {
            if(wc.find("PRODUCT",p.product().barcode()).isEmpty())return notice(false,"feedback.productMissing");
            resolved=new nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut(p.product(),wc.find("PRODUCT",p.product().barcode()).map(nz.ac.aut.pdc.shared.RecordData::version).orElse(0L));
        }
        if(input instanceof nz.ac.aut.pdc.supermarket.app.AOperations.PromotionPut p && p.expectedVersion()<0)
            resolved=new nz.ac.aut.pdc.supermarket.app.AOperations.PromotionPut(p.promotion(),wc.find("PROMOTION",p.promotion().promotionId()).map(nz.ac.aut.pdc.shared.RecordData::version).orElse(0L));
        if(input instanceof nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptReview r && r.expectedVersion()==0) {
            var record=wc.find("DOCUMENT",r.receiptId());
            if(record.isEmpty()) return notice(false,"feedback.receiptMissing");
            resolved=new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptReview(r.receiptId(),record.get().version(),r.approve(),r.opinion(),r.attachments());
        }
        if(input instanceof MaintenanceOperations.ReceiptEdit edit) {
            var document=wc.find("DOCUMENT",edit.receiptId()); if(document.isEmpty()) return notice(false,"feedback.receiptMissing");
            Map<String,nz.ac.aut.pdc.supermarket.app.AOperations.Item> rows=new java.util.LinkedHashMap<>();
            int sequence=0;
            for(var item:edit.items()) {
                String id=wc.records("DOC_LINE").stream().filter(r -> r.data().path("documentId").asText().equals(edit.receiptId()) && r.data().path("barcode").asText().equals(item.barcode()) && !rows.containsKey(r.id()))
                    .map(nz.ac.aut.pdc.shared.RecordData::id).sorted().findFirst().orElse(idClock.entityId(requestKey,"corrected:"+sequence++));
                rows.put(id,item);
            }
            resolved=new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptCorrect(edit.receiptId(),document.get().version(),rows,edit.reason());
        }
        BusinessRequest request=BusinessRequest.of(resolved);
        if(input instanceof nz.ac.aut.pdc.supermarket.stock.LossWorkflow.Request loss) {
            var contextLoss=new OperationContext(actor.session(),requestKey,kind,loss.lossId());
            return remember(actor,requestKey,() -> {
                var prior=store.lookupResult(contextLoss,request); if(prior.status()!=Result.Status.NOT_FOUND)return feedback(prior);
                var current=store.open(contextLoss);if(!current.hasValue())return rejected(current.code());
                var who=store.authenticate(contextLoss);if(!who.hasValue())return rejected(who.code());
                try {var delta=new nz.ac.aut.pdc.supermarket.stock.LossWorkflow().prepare(contextLoss,who.value(),loss,current.value(),idClock.now());
                    return feedback(store.submit(contextLoss,request,current.value(),delta));}
                catch(RuntimeException e){return rejected(e.getMessage());}
            });
        }
        var a=new nz.ac.aut.pdc.supermarket.app.AOperations(idClock,session -> store.authenticate(new OperationContext(session,requestKey,kind,null)),new nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter());
        return remember(actor,requestKey,() -> {
            var prior=store.lookupResult(context,request);
            if(prior.status()!=Result.Status.NOT_FOUND) return feedback(prior);
            var quoted=quotes.get(requestKey);
            if(quoted!=null) {
                var identity=store.authenticate(context);
                if(!identity.hasValue() || !quoted.actor().equals(identity.value().accountId()) || !quoted.request().equals(request))return rejected("REQUEST_KEY_CONFLICT");
                return feedback(store.submit(context,request,quoted.wc(),quoted.delta()));
            }
            var prepared=a.prepare(store,context,request);
            if(!prepared.hasValue()) return rejected(prepared.code());
            return feedback(store.submit(context,request,prepared.value().workingCopy(),prepared.value().changes()));
        });
    }
    private static OperationFeedback notice(boolean success,String key,Object... args) {
        return OperationFeedback.shown(success,message(key,args));
    }
    private static OperationFeedback rejected(String code) {
        String stable=code!=null && code.matches("[A-Z][A-Z0-9_]*")?code:"INVALID_INPUT";
        boolean technical=stable.contains("FAILED") || stable.contains("FAILURE") || stable.endsWith("_UNAVAILABLE");
        return OperationFeedback.shown(false,t -> t.text(technical?"feedback.failed":"feedback.rejected",t.error(stable)));
    }
    private OperationFeedback feedback(Result<SubmissionPort.CommitReceipt> result) {
        return result.status()==Result.Status.SUCCESS
            ? notice(true,"feedback.commit",result.value().requestKey(),data(result.value().resultIds()))
            : rejected(result.code());
    }
    @Override public Result<nz.ac.aut.pdc.infrastructure.AttachmentStore.Attachment> attach(CuiActor actor,String source,String receiptId,int additional) {
        var op=new OperationContext(actor.session(),idClock.newRequestKey(),"ATTACHMENT",receiptId);
        var identity=store.authenticate(op); if(!identity.hasValue()) return identity.failure();
        if(identity.value().role()!=OperationContext.Role.MANAGER && identity.value().role()!=OperationContext.Role.ADMIN) return Result.rejected("FORBIDDEN","Role cannot import receiving attachments");
        var wc=store.open(op); if(!wc.hasValue()) return wc.failure();
        int count=(int)wc.value().records("ATTACHMENT").stream().filter(r -> r.data().path("receiptId").asText().equals(receiptId)).count();
        return store.importAttachment(java.nio.file.Path.of(source),receiptId,count+additional);
    }
    @Override public OperationFeedback inspect(CuiActor actor,String category,String id) {
        var op=new OperationContext(actor.session(),idClock.newRequestKey(),"QUERY",id);
        var identity=store.authenticate(op); if(!identity.hasValue()) return rejected(identity.code());
        var open=store.open(op); if(!open.hasValue()) return rejected(open.code());
        var wc=open.value(); var out=new java.util.ArrayList<Object>();
        if(category.equals("QUOTA")) {
            var allowed=new nz.ac.aut.pdc.supermarket.app.AOperations(idClock,session -> store.authenticate(new OperationContext(session,op.requestKey(),"QUERY",id)),new nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter()).readSaleLine(op,wc,id);
            if(!allowed.hasValue())return rejected(allowed.code());
            var query=new nz.ac.aut.pdc.supermarket.query.QueryService(new SharedSaleReader(wc),new SharedQuotaLedgerReader(wc),new QuotaViewBuilder());
            var result=query.quotaView(id);
            if(result instanceof nz.ac.aut.pdc.supermarket.query.QueryResult.Value<?> value) return OperationFeedback.shown(true,data(value.value()));
            if(result instanceof nz.ac.aut.pdc.supermarket.query.QueryResult.Rejected<?> denied) return rejected(denied.code());
            return rejected(((nz.ac.aut.pdc.supermarket.query.QueryResult.Failed<?>)result).code());
        }
        if(category.equals("PRODUCT")) for(var r:wc.records("PRODUCT")) out.add(r.data());
        else if(category.equals("PROMOTION")) for(var r:wc.records("PROMOTION")) out.add(r.data());
        else if(category.equals("LOSS")) for(var r:wc.records("LOSS_RECORD")) {var loss=r.as(nz.ac.aut.pdc.supermarket.stock.LossRecord.class);var source=wc.find("DOC_LINE",loss.sourceLineId()).orElseThrow().as(nz.ac.aut.pdc.shared.DocumentLine.class);var owner=wc.find("DOCUMENT",source.documentId()).orElseThrow().as(nz.ac.aut.pdc.shared.DocumentHeader.class);if(identity.value().role()==OperationContext.Role.CLERK && !owner.createdBy().equals(identity.value().accountId())) continue;out.add(nz.ac.aut.pdc.supermarket.stock.LossWorkflow.effective(wc,r.id()));}
        else if(category.equals("STOCK")) out.addAll(new nz.ac.aut.pdc.supermarket.stock.SharedWorkingCopyView(wc).balances(wc.records("PRODUCT").stream().map(nz.ac.aut.pdc.shared.RecordData::id).toList()));
        else for(var r:wc.records("DOCUMENT")) {
            var h=r.as(nz.ac.aut.pdc.shared.DocumentHeader.class);
            if(identity.value().role()==OperationContext.Role.CLERK && !h.createdBy().equals(identity.value().accountId())) continue;
            if(id!=null && !id.isBlank() && !id.equals(r.id())) continue;
            out.add(h);
            for(var line:wc.records("DOC_LINE")) if(line.data().path("documentId").asText().equals(h.documentId())) {
                out.add(line.data());wc.find("SALE_LINE",line.id()).ifPresent(snapshot -> out.add(snapshot.data()));
            }
            for(var money:wc.records("MONEY_ENTRY")) if(money.data().path("documentId").asText().equals(h.documentId()))out.add(money.data());
            for(var event:wc.records("DOC_EVENT")) if(event.data().path("documentId").asText().equals(h.documentId()))out.add(event.data());
        }
        return out.isEmpty()?notice(true,"feedback.noRecords"):OperationFeedback.shown(true,data(out));
    }

    @Override
    public OperationFeedback replenish(CuiActor actor, String barcode, int quantity) {
        String key=idClock.newRequestKey();return remember(actor,key,() -> replenishOnce(actor,barcode,quantity,key));
    }
    private OperationFeedback replenishOnce(CuiActor actor,String barcode,int quantity,String requestKey) {
        var original=BusinessRequest.of(Map.of("barcode",barcode,"quantity",quantity));
        var prior=store.lookupResult(new OperationContext(actor.session(),requestKey,nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.REPLENISHMENT,idClock.entityId(requestKey,"document")),original);
        if(prior.status()!=Result.Status.NOT_FOUND)return feedback(prior);
        return stockOnce(actor,requestKey,nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.REPLENISHMENT,original);
    }
    @Override public OperationFeedback recordLoss(CuiActor actor,String barcode,Position position,int quantity) {return notice(false,"feedback.lossReason");}
    @Override public OperationFeedback recordLoss(CuiActor actor,String barcode,Position position,int quantity,String reason) {
        if(reason==null || reason.isBlank())return notice(false,"feedback.lossReason");
        String key=idClock.newRequestKey();return remember(actor,key,() -> {
            var request=BusinessRequest.of(Map.of("barcode",barcode,"quantity",quantity,"position",position.name(),"reason",reason));
            return stockOnce(actor,key,nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.LOSS,request);
        });
    }
    private OperationFeedback stockOnce(CuiActor actor,String key,String kind,BusinessRequest request) {
        var context=new OperationContext(actor.session(),key,kind,idClock.entityId(key,"document"));
        var prior=store.lookupResult(context,request);if(prior.status()!=Result.Status.NOT_FOUND)return feedback(prior);
        var identity=store.authenticate(context);if(!identity.hasValue())return rejected(identity.code());
        var opened=store.open(context);if(!opened.hasValue())return rejected(opened.code());
        var candidate=nz.ac.aut.pdc.supermarket.stock.StockBusiness.prepare(context,identity.value(),request,opened.value(),idClock);
        if(!candidate.hasValue())return rejected(candidate.code());
        return feedback(store.submit(context,request,opened.value(),candidate.value()));
    }

    @Override public OperationFeedback aftersale(CuiActor actor,AftersaleRequest request) {
        return aftersaleBatch(actor,List.of(request),request.requestKey());
    }
    @Override public OperationFeedback aftersaleBatch(CuiActor actor,List<AftersaleRequest> lines,String requestKey) {
        String key=requestKey==null?idClock.newRequestKey():requestKey;
        return remember(actor,key,() -> aftersaleOnce(actor,lines,key,true));
    }
    private OperationFeedback aftersaleOnce(CuiActor actor,List<AftersaleRequest> lines,String finalKey,boolean commit) {
        Objects.requireNonNull(actor,"actor");
        String aftersaleId=idClock.entityId(finalKey,"aftersale");
        var identity=store.authenticate(new OperationContext(actor.session(),finalKey,"AFTERSALE",aftersaleId));
        if(!identity.hasValue())return rejected(identity.code());
        var sharedActor=identity.value();
        OperationContext operation=new OperationContext(actor.session(),finalKey,AftersaleChangeSetAssembler.OPERATION,aftersaleId);
        AftersaleCommand command=new AftersaleCommand(finalKey,null,lines.stream().map(request -> new AftersaleCommandLine(
            request.saleLineId(),request.kind(),request.quantity(),request.opened(),request.qualityProblem(),request.reasonKind(),request.reasonText(),request.replacementBarcode(),request.replacementQuantity(),false)).toList());
        BusinessRequest finalRequest = BusinessRequest.of(command);
        Result<SubmissionPort.CommitReceipt> prior = store.lookupResult(operation, finalRequest);
        if (prior.status() != Result.Status.NOT_FOUND) {
            if (prior.hasValue()) {
                return notice(true,"feedback.aftersaleDone",prior.value().requestKey());
            }
            return rejected(prior.code());
        }

        var quoted=quotes.get(finalKey);
        if(commit && quoted!=null) {
            if(!quoted.actor().equals(sharedActor.accountId()) || !quoted.request().equals(finalRequest))return rejected("REQUEST_KEY_CONFLICT");
            return feedback(store.submit(operation,finalRequest,quoted.wc(),quoted.delta()));
        }
        var originals=new java.util.ArrayList<nz.ac.aut.pdc.shared.SaleLineSnapshot>();
        var access=new nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess(
            session -> store.authenticate(new OperationContext(session,finalKey,"AUTH",aftersaleId)),idClock,
            new nz.ac.aut.pdc.supermarket.aftersale.AftersaleDraftAccess());
        for(String lineId:lines.stream().map(AftersaleRequest::saleLineId).distinct().sorted().toList()) {
            String readKey=derivedKey(finalKey,"read:"+lineId);
            var readOp=new OperationContext(actor.session(),readKey,nz.ac.aut.pdc.shared.AftersaleReadRequest.OPERATION,aftersaleId);
            var traced=access.readForAftersale(store,readOp,new nz.ac.aut.pdc.shared.AftersaleReadRequest(lineId,aftersaleId,List.of(lineId)));
            if(!traced.hasValue())return notice(false,"feedback.readFailure",(Texts.Display)t -> t.error(traced.code()),finalKey,aftersaleId);
            originals.addAll(traced.value().lines());
        }
        Result<WorkingCopy> openResult = store.open(operation);
        if (!openResult.hasValue()) {
            return rejected(openResult.code());
        }
        WorkingCopy workingCopy = openResult.value();
        AftersaleService aftersaleService=nz.ac.aut.pdc.supermarket.aftersale.AftersaleEngine.create(workingCopy,idClock,finalKey);
        AftersaleSubmissionService submission = new AftersaleSubmissionService(
                aftersaleService, new AftersaleChangeSetAssembler(), store);
        if(!commit) {
            var candidate=submission.prepare(command,workingCopy,operation,sharedActor);
            if(!candidate.hasValue())return notice(false,"feedback.draftFailure",(Texts.Display)t -> t.error(candidate.code()),aftersaleId);
            var prepared=candidate.value();quotes.put(finalKey,new Quoted(sharedActor.accountId(),workingCopy,finalRequest,prepared.changes()));
            return notice(true,"feedback.aftersaleQuote",data(originals),data(prepared.summary().settlements()),aftersaleId,finalKey);
        }
        Result<SubmissionPort.CommitReceipt> result = submission.submit(
                command, workingCopy, operation, sharedActor);
        if (!result.hasValue()) {
            return rejected(result.code());
        }
        return notice(true,"feedback.aftersaleDone",result.value().requestKey());
    }

    @Override
    public OperationFeedback cancelAftersaleDraft(CuiActor actor, String draftId) {
        Objects.requireNonNull(actor, "actor");
        if (draftId == null || draftId.isBlank()) {
            return notice(false,"feedback.draftRequired");
        }
        OperationContext operation = new OperationContext(
                actor.session(), derivedKey(draftId.trim(), "cancel"),
                AftersaleDraftOperations.CANCEL, draftId.trim());
        Result<SubmissionPort.CommitReceipt> result = new AftersaleDraftService(store).cancel(
                operation,
                new OperationContext.ActorContext(
                        actor.accountId(), SharedRoles.toShared(actor.role())));
        if (!result.hasValue()) {
            return rejected(result.code());
        }
        return notice(true,"feedback.cancelled",draftId.trim());
    }

    @Override
    public OperationFeedback deleteAftersaleDraft(CuiActor actor, String draftId) {
        Objects.requireNonNull(actor, "actor");
        if (draftId == null || draftId.isBlank()) {
            return notice(false,"feedback.draftRequired");
        }
        OperationContext operation = new OperationContext(
                actor.session(), derivedKey(draftId.trim(), "delete"),
                nz.ac.aut.pdc.shared.DraftDeletion.OPERATION, draftId.trim());
        Result<SubmissionPort.CommitReceipt> result = new AftersaleDraftService(store).delete(
                operation,
                new OperationContext.ActorContext(
                        actor.accountId(), SharedRoles.toShared(actor.role())));
        if (!result.hasValue()) {
            return rejected(result.code());
        }
        return notice(true,"feedback.deleted",draftId.trim());
    }

    @Override
    public OperationFeedback reverseDocument(CuiActor actor, String targetDocumentId, String reason) {
        Objects.requireNonNull(actor, "actor");
        if (targetDocumentId == null || targetDocumentId.isBlank()
                || reason == null || reason.isBlank()) {
            return notice(false,"feedback.reverseRequired");
        }
        return reverseDocument(actor,targetDocumentId,reason,idClock.newRequestKey());
    }

    public OperationFeedback reverseDocument(CuiActor actor,String targetDocumentId,String reason,String requestKey) {
        return remember(actor,requestKey,() -> reverseOnce(actor,targetDocumentId,reason,requestKey));
    }
    private OperationFeedback reverseOnce(CuiActor actor,String targetDocumentId,String reason,String requestKey) {
        String target = targetDocumentId.trim();
        var command = new nz.ac.aut.pdc.supermarket.reversal.DocumentReversalCommand(
                target, reason.trim(), requestKey);
        BusinessRequest request = BusinessRequest.of(command);
        OperationContext operation = new OperationContext(
                actor.session(), requestKey,
                nz.ac.aut.pdc.supermarket.reversal.ReversalChangeSetAssembler.OPERATION, target);
        var prior=store.lookupResult(operation,request);
        if(prior.status()!=Result.Status.NOT_FOUND) return feedback(prior);
        Result<WorkingCopy> opened = store.open(operation);
        if (!opened.hasValue()) {
            return rejected(opened.code());
        }
        OperationContext.ActorContext sharedActor =
                new OperationContext.ActorContext(actor.accountId(), SharedRoles.toShared(actor.role()));
        ChangeSet changes;
        try {
            changes = new nz.ac.aut.pdc.supermarket.reversal.ReversalChangeSetAssembler()
                    .assemble(opened.value(), target, operation, sharedActor, request, idClock);
        } catch (RuntimeException ex) {
            return rejected(ex.getMessage());
        }
        Result<SubmissionPort.CommitReceipt> submitted = store.submit(
                operation, request, opened.value(), changes);
        if (!submitted.hasValue()) {
            return rejected(submitted.code());
        }
        return notice(true,"feedback.reversalDone",submitted.value().requestKey());
    }

    private String derivedKey(String baseKey, String purpose) {
        return java.util.UUID.nameUUIDFromBytes(
                (baseKey + ":" + purpose).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
