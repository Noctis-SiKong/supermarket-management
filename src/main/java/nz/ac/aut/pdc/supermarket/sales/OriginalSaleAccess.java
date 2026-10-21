package nz.ac.aut.pdc.supermarket.sales;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** IF-11: one admission implementation, real IF-05 tracing, no B lifecycle/eligibility algorithms. */
public final class OriginalSaleAccess implements CommitValidator {
    /** B-owned target preparation/authorization adapter. Never saves, cancels or deletes here. */
    public interface DraftAccess {
        // For a new target return only its DOCUMENT/AFTERSALE; READ_FOR_AFTERSALE is its first event.
        // For an existing target return empty (or its missing AFTERSALE extension only).
        Result<ChangeSet> prepareTarget(OperationContext op, ActorContext actor, AftersaleReadRequest request, WorkingCopy wc);
        Result<Void> reviewTarget(OperationContext op, ActorContext actor, AftersaleReadRequest request,
                                  WorkingCopy baseline, ChangeSet completeChanges, Instant acceptedAt);
    }
    private final IdentityResolver identities;
    private final IdClock ids;
    private final DraftAccess drafts;
    public OriginalSaleAccess(IdentityResolver identities, IdClock ids, DraftAccess drafts) {
        this.identities=Objects.requireNonNull(identities); this.ids=Objects.requireNonNull(ids); this.drafts=Objects.requireNonNull(drafts);
    }
    public Result<AftersaleReadAccess> readForAftersale(SubmissionPort store, OperationContext op, AftersaleReadRequest request) {
        try {
            if (op == null || !op.operation().equals(AftersaleReadRequest.OPERATION) || !Objects.equals(op.targetId(),request.aftersaleId()))
                return Result.rejected("INVALID_INPUT","Dedicated read operation and matching target required");
            Result<ActorContext> actor=resolve(op); if (!actor.hasValue()) return actor.failure();
            BusinessRequest original=BusinessRequest.of(request);
            // An unreadable/unknown result is never treated as NOT_FOUND or retried under a new key.
            Result<CommitReceipt> saved=store.lookupResult(op,original);
            if (saved.status() != Result.Status.SUCCESS && saved.status() != Result.Status.NOT_FOUND) return saved.failure();
            if (saved.status() == Result.Status.NOT_FOUND) {
                Result<WorkingCopy> opened=store.open(op); if (!opened.hasValue()) return opened.failure();
                WorkingCopy wc=opened.value(); activeProject(wc,request); // Still private, no data released yet.
                Result<ChangeSet> prepared=drafts.prepareTarget(op,actor.value(),request,wc);
                if (!prepared.hasValue()) return prepared.failure();
                ChangeSet delta=compose(op,actor.value(),request,wc,prepared.value(),null);
                saved=store.submit(op,original,wc,delta);
                if (saved.status() != Result.Status.SUCCESS) return saved.failure();
            }
            // A trace success is not an aftersale success. Re-open after this separate commit.
            actor=resolve(op); if (!actor.hasValue()) return actor.failure();
            Result<WorkingCopy> fresh=store.open(op); if (!fresh.hasValue()) return fresh.failure();
            requireActiveTarget(fresh.value(),request);
            Result<ChangeSet> authorized=drafts.prepareTarget(op,actor.value(),request,fresh.value());
            if (!authorized.hasValue()) return authorized.failure();
            require(empty(authorized.value()),"REFERENCE_INVALID","Committed target must authorize without new changes");
            String eventId=ids.entityId(op.requestKey(),"aftersale-read");
            DocEvent event=fresh.value().find("DOC_EVENT",eventId).orElseThrow(() -> new ReadRefusal("REFERENCE_INVALID","Committed trace missing")).as(DocEvent.class);
            require(event.documentId().equals(request.aftersaleId()) && event.actorId().equals(actor.value().accountId())
                && event.action().equals(AftersaleReadRequest.OPERATION) && event.detailSnapshot().equals(detail(fresh.value(),op,request)),"REFERENCE_INVALID","Trace/request mismatch");
            return Result.value(new AftersaleReadAccess(saved.value(),eventId,fresh.value(),activeProject(fresh.value(),request)));
        } catch (ReadRefusal e) { return Result.rejected(e.code,e.getMessage()); }
        catch (IllegalArgumentException e) { return Result.rejected("INVALID_INPUT","Invalid voucher, target or candidate"); }
        catch (RuntimeException e) { return Result.failed("DEPENDENCY_FAILED","Read unavailable; retain original request/key and query before retry"); }
    }
    private Result<ActorContext> resolve(OperationContext op) {
        if (op.sessionRef()==null) return Result.rejected("AUTH_REQUIRED","Login required");
        try { return identities.resolve(op.sessionRef()); }
        catch (RuntimeException e) { return Result.failed("AUTH_TECHNICAL_FAILURE","Identity verification unavailable"); }
    }
    private ChangeSet compose(OperationContext op,ActorContext actor,AftersaleReadRequest request,WorkingCopy wc,ChangeSet draft,String time) {
        require(draft.patches().isEmpty() && draft.removals().isEmpty(),"REFERENCE_INVALID","Read target preparation cannot mutate existing business");
        for (RecordData r:draft.inserts()) require(r.id().equals(request.aftersaleId()) && Set.of("DOCUMENT","AFTERSALE").contains(r.table()),"REFERENCE_INVALID","Unexpected draft candidate");
        WorkingCopy view=wc.overlay(draft); requireActiveTarget(view,request);
        ChangeSet.Builder out=new ChangeSet.Builder().merge(draft);
        long version=1;
        Optional<RecordData> existing=wc.find("DOCUMENT",request.aftersaleId());
        if (existing.isPresent()) {
            version=wc.records("DOC_EVENT").stream().filter(r -> r.data().path("documentId").asText().equals(request.aftersaleId()))
                .mapToLong(r -> r.data().path("version").asLong()).max().orElse(0)+1;
        }
        String eventId=ids.entityId(op.requestKey(),"aftersale-read");
        out.add(RecordData.of("DOC_EVENT",eventId,new DocEvent(eventId,request.aftersaleId(),version,
            AftersaleReadRequest.OPERATION,actor.accountId(),time,detail(wc,op,request))));
        return out.build();
    }
    @Override public Result<Void> validate(OperationContext op,ActorContext actor,BusinessRequest original,WorkingCopy wc,ChangeSet changes,Instant acceptedAt) {
        try {
            AftersaleReadRequest request=original.as(AftersaleReadRequest.class);
            require(op.operation().equals(AftersaleReadRequest.OPERATION) && Objects.equals(op.targetId(),request.aftersaleId()),"INVALID_INPUT","Wrong read context");
            activeProject(wc,request);
            List<RecordData> draftRecords=changes.inserts().stream().filter(r -> !r.table().equals("DOC_EVENT")).toList();
            ChangeSet draft=new ChangeSet(draftRecords,List.of());
            ChangeSet expected=compose(op,actor,request,wc,draft,acceptedAt.toString());
            require(equivalent(expected,changes),"REFERENCE_INVALID","Read effects differ from original request");
            if (wc.find("DOCUMENT",request.aftersaleId()).isEmpty()) {
                DocumentHeader header=wc.overlay(draft).find("DOCUMENT",request.aftersaleId()).orElseThrow().as(DocumentHeader.class);
                require(header.createdBy().equals(actor.accountId()) && header.createdAt().equals(acceptedAt.toString())
                    && header.confirmedAt()==null && header.confirmedBy()==null,"REFERENCE_INVALID","Invalid draft identity/time");
            }
            return drafts.reviewTarget(op,actor,request,wc,changes,acceptedAt);
        } catch (ReadRefusal e) { return Result.rejected(e.code,e.getMessage()); }
        catch (IllegalArgumentException e) { return Result.rejected("INVALID_INPUT","Invalid read request/candidate"); }
    }
    private static void requireActiveTarget(WorkingCopy wc,AftersaleReadRequest request) {
        RecordData record=wc.find("DOCUMENT",request.aftersaleId()).orElseThrow(() -> new ReadRefusal("REFERENCE_INVALID","Target draft missing"));
        require(!record.data().hasNonNull("confirmedAt") && !record.data().hasNonNull("confirmedBy") && record.data().path("status").asText().equals("DRAFT"),"AFTERSALE_CLOSED","Target is cancelled or completed");
        RecordData extension=wc.find("AFTERSALE",request.aftersaleId()).orElseThrow(() -> new ReadRefusal("REFERENCE_INVALID","Aftersale extension missing"));
        require(!extension.data().has("documentId") || extension.data().path("documentId").asText().equals(request.aftersaleId()),"REFERENCE_INVALID","Invalid target extension");
    }
    private static String detail(WorkingCopy wc,OperationContext op,AftersaleReadRequest request) {
        return JsonCodec.write(new TreeMap<>(Map.of("originalSaleId",project(wc,request).get(0).saleId(),"saleLineIds",project(wc,request).stream().map(SaleLineSnapshot::lineId).toList(),"originalSaleLineId",project(wc,request).get(0).lineId(),"voucherType",wc.find("SALE",request.voucher()).isPresent()?"SALE_DOCUMENT_ID":"SALE_LINE_ID","requestKey",op.requestKey())));
    }
    private static List<SaleLineSnapshot> activeProject(WorkingCopy wc,AftersaleReadRequest request) {
        var lines=project(wc,request);
        require(wc.find("DOCUMENT",lines.get(0).saleId()).orElseThrow().data().path("status").asText().equals("CONFIRMED"),"REFERENCE_INVALID","Original sale is no longer effective");
        return lines;
    }
    private static List<SaleLineSnapshot> project(WorkingCopy wc,AftersaleReadRequest request) {
        String saleId=request.voucher();
        if(wc.find("SALE",saleId).isEmpty() && wc.find("SALE_LINE",saleId).isPresent())
            saleId=wc.find("DOC_LINE",saleId).orElseThrow().as(DocumentLine.class).documentId();
        RecordData sale=wc.find("SALE",saleId).orElseThrow(() -> new ReadRefusal("NOT_FOUND","Exact sale voucher not found"));
        DocumentHeader header=wc.find("DOCUMENT",sale.id()).orElseThrow(() -> new ReadRefusal("REFERENCE_INVALID","Sale header missing")).as(DocumentHeader.class);
        require(header.kind().equals("SALE") && header.confirmedAt()!=null,"REFERENCE_INVALID","Voucher is not a confirmed sale fact");
        List<RecordData> selected;
        if(request.saleLineIds().isEmpty() && wc.find("SALE_LINE",request.voucher()).isPresent()) selected=List.of(wc.find("DOC_LINE",request.voucher()).orElseThrow());
        else if (request.saleLineIds().isEmpty()) selected=wc.records("DOC_LINE").stream().filter(r -> r.as(DocumentLine.class).documentId().equals(sale.id())).sorted(Comparator.comparing(RecordData::id)).toList();
        else selected=request.saleLineIds().stream().distinct().map(id -> wc.find("DOC_LINE",id).orElseThrow(() -> new ReadRefusal("NOT_FOUND","Sale line missing"))).toList();
        require(!selected.isEmpty(),"NOT_FOUND","Sale has no requested lines");
        List<SaleLineSnapshot> out=new ArrayList<>();
        for (RecordData r:selected) {
            DocumentLine line=r.as(DocumentLine.class); require(line.documentId().equals(sale.id()),"REFERENCE_INVALID","Line does not belong to voucher");
            Pricing.SaleLineSnapshot snapshot=wc.find("SALE_LINE",r.id()).orElseThrow(() -> new ReadRefusal("REFERENCE_INVALID","Sale snapshot missing")).as(Pricing.SaleLineSnapshot.class);
            out.add(new SaleLineSnapshot(sale.id(),r.id(),line.barcode(),snapshot.productNameSnapshot(),snapshot.unitPriceSnapshot(),line.quantity(),
                JsonCodec.write(snapshot.promotionSnapshot()),snapshot.grossAmount(),snapshot.discountAmount(),snapshot.paidAmount(),header.confirmedAt()));
        }
        return List.copyOf(out);
    }
    /** Backend projection only; admission is exclusively readForAftersale. No CUI receives this reader. */
    public static Optional<SaleLineSnapshot> originalLine(WorkingCopy wc,String lineId) {
        try { return project(wc,new AftersaleReadRequest(lineId,"internal-projection",List.of(lineId))).stream().findFirst(); }
        catch(RuntimeException e) { return Optional.empty(); }
    }
    public void validateRecovered(WorkingCopy wc) throws IOException {
        try {
            for (RecordData r:wc.records("DOC_EVENT")) if (r.data().path("action").asText().equals(AftersaleReadRequest.OPERATION)) {
                DocEvent event=r.as(DocEvent.class); var data=JsonCodec.tree(event.detailSnapshot());
                List<String> lines=new ArrayList<>(); for(var n:data.path("saleLineIds")) lines.add(n.asText());
                boolean modern=data.has("requestKey");
                String voucher=data.path("originalSaleId").asText();
                if(!modern) {
                    String line=data.path("originalSaleLineId").asText();
                    lines=List.of(line); voucher=line;
                }
                AftersaleReadRequest request=new AftersaleReadRequest(voucher,event.documentId(),lines);
                require(wc.find("AFTERSALE",request.aftersaleId()).isPresent() && wc.find("DOCUMENT",request.aftersaleId()).isPresent(),"REFERENCE_INVALID","Trace target missing");
                if(modern) require(r.id().equals(ids.entityId(data.path("requestKey").asText(),"aftersale-read")),"REFERENCE_INVALID","Trace ID/key mismatch");
                project(wc,request); Instant.parse(event.occurredAt());
            }
        } catch (RuntimeException e) { throw new IOException("IF-11 trace associations invalid",e); }
    }
    private static boolean empty(ChangeSet c) { return c.inserts().isEmpty()&&c.patches().isEmpty()&&c.removals().isEmpty(); }
    private static boolean equivalent(ChangeSet a,ChangeSet b) {
        return a.inserts().size()==b.inserts().size() && a.patches().size()==b.patches().size() && a.removals().equals(b.removals())
            && new HashSet<>(a.inserts()).equals(new HashSet<>(b.inserts())) && new HashSet<>(a.patches()).equals(new HashSet<>(b.patches()));
    }
    private static void require(boolean condition,String code,String detail) { if (!condition) throw new ReadRefusal(code,detail); }
    private static final class ReadRefusal extends RuntimeException {
        final String code; ReadRefusal(String code,String detail) { super(detail); this.code=code; }
    }
}
