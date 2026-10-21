package nz.ac.aut.pdc.supermarket.sales;

import nz.ac.aut.pdc.fixture.FictionalDependencies;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.app.AOperations.*;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

/** Real A/IF-05/files; identity, stock, B draft/cancel/delete are TEST DOUBLES, not B integration. */
public class OriginalSaleAccessTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    final FictionalDependencies deps = new FictionalDependencies();
    final IdClock ids = new IdClock(Clock.fixed(Instant.parse("2026-10-21T15:59:59Z"),ZoneOffset.UTC));
    final AOperations a = new AOperations(ids,deps,deps);
    final OriginalSaleAccess access = new OriginalSaleAccess(deps,ids,new DraftDouble());
    DataRoot root; JsonStore store; boolean failSave, denyDraft; String voucher;
    Map<String,CommitValidator> validators() {
        Map<String,CommitValidator> out = new HashMap<>(a.validators());
        out.put(AftersaleReadRequest.OPERATION,access);
        // This intentionally does not certify B's production lifecycle/permissions algorithm.
        out.put("CANCEL_AFTERSALE_TEST",(op,actor,request,wc,delta,time) -> Result.value(null));
        out.put(DraftDeletion.OPERATION,(op,actor,request,wc,delta,time) -> Result.value(null));
        return out;
    }
    void reopen() throws Exception {
        store = new JsonStore(root,deps,validators(),wc -> { a.validateRecovered(wc); access.validateRecovered(wc); },
            p -> { if (failSave) throw new IOException("Fictional disk failure"); },JsonStore.RecoveryMode.CURRENT,List.of(),ids::now);
    }
    @Before public void setup() throws Exception {
        Path project=temp.newFolder().toPath(); Files.writeString(project.resolve("pom.xml"),"fixture");
        Files.writeString(project.resolve("README.md"),"fixture"); Files.createDirectory(project.resolve("docs"));
        root=DataRoot.forProject(project); reopen();
        execute(op("PRODUCT_PUT",deps.manager,null),new ProductPut(new Product("00123456","虚构赠品","件",new BigDecimal("0.00"),"虚构零价测试"),0));
        execute(op("OPENING",deps.manager,null),new Opening(new Item("00123456",10),StockEntry.Position.S,"虚构合法期初"));
        OperationContext sale=op("SALE",deps.manager,null);
        execute(sale,new Sale(List.of(new Item("00123456",2)),true));
        voucher=ids.entityId(sale.requestKey(),"document");
    }
    @After public void stop() throws Exception { if(store!=null) store.close(); }
    OperationContext op(String kind,Object session,String target) { return new OperationContext(session,ids.newRequestKey(),kind,target); }
    OperationContext readOp(String target) { return op(AftersaleReadRequest.OPERATION,deps.clerk,target); }
    AftersaleReadRequest request(String target) { return new AftersaleReadRequest(voucher,target,List.of()); }
    WorkingCopy state() { return store.open(op("TEST",deps.manager,null)).value(); }
    void execute(OperationContext op,Object input) {
        var original=BusinessRequest.of(input); var prepared=a.prepare(store,op,original);
        assertEquals(prepared.detail(),Result.Status.PREPARED,prepared.status());
        var saved=store.submit(op,original,prepared.value().workingCopy(),prepared.value().changes());
        assertEquals(saved.detail(),Result.Status.SUCCESS,saved.status());
    }
    class DraftDouble implements OriginalSaleAccess.DraftAccess {
        @Override public Result<ChangeSet> prepareTarget(OperationContext op,ActorContext actor,AftersaleReadRequest request,WorkingCopy wc) {
            if (denyDraft) return Result.rejected("FORBIDDEN","Fictional B target denial");
            if (wc.find("DOCUMENT",request.aftersaleId()).isPresent()) return Result.prepared(ChangeSet.empty());
            var original=BusinessRequest.of(request);
            DocumentHeader header=new DocumentHeader(request.aftersaleId(),"RETURN","DRAFT",op.requestKey(),
                JsonCodec.fingerprint(op.operation(),actor.accountId(),original),actor.accountId(),null,null,null,null);
            return Result.prepared(new ChangeSet(List.of(RecordData.of("DOCUMENT",request.aftersaleId(),header),
                RecordData.of("AFTERSALE",request.aftersaleId(),Map.of("documentId",request.aftersaleId()))),List.of()));
        }
        @Override public Result<Void> reviewTarget(OperationContext op,ActorContext actor,AftersaleReadRequest request,WorkingCopy wc,ChangeSet delta,Instant acceptedAt) {
            return denyDraft ? Result.rejected("FORBIDDEN","Fictional B target denial") : Result.value(null);
        }
    }
    @Test public void crossPersonReadTracesExactReturnedLinesAndProvidesFreshCopyAndReplaysAfterRestart() throws Exception {
        String target=ids.entityId(ids.newRequestKey(),"draft"); var op=readOp(target); var request=request(target);
        WorkingCopy before=state(); String line=before.records("SALE_LINE").get(0).id();
        assertEquals("FORBIDDEN",a.readSaleLine(op,before,line).code());
        var result=access.readForAftersale(store,op,request);
        assertEquals(result.detail(),Result.Status.VALUE,result.status());
        assertEquals(before.baseLast()+1,result.value().workingCopy().baseLast());
        assertEquals(new BigDecimal("0.00"),result.value().lines().get(0).paidAmount());
        assertEquals(voucher,result.value().lines().get(0).saleId());
        DocEvent event=state().find("DOC_EVENT",result.value().eventId()).orElseThrow().as(DocEvent.class);
        assertEquals("fictional-clerk",event.actorId()); assertEquals(target,event.documentId());
        assertEquals(line,JsonCodec.tree(event.detailSnapshot()).path("saleLineIds").get(0).asText());
        assertEquals(ids.now().toString(),event.occurredAt());
        String snapshot=Files.readString(root.resolve("snapshot.json"));
        store.close(); reopen();
        var retry=access.readForAftersale(store,op,request);
        assertTrue(retry.value().trace().replayed()); assertEquals(result.value().lines(),retry.value().lines());
        assertEquals(snapshot,Files.readString(root.resolve("snapshot.json")));
        assertEquals(1,state().records("DOC_EVENT").stream().filter(r -> r.data().path("action").asText().equals(AftersaleReadRequest.OPERATION)).count());
    }
    @Test public void failedTraceSaveReleasesNoDataAndLeavesSnapshotAndDraftUnchanged() throws Exception {
        var op=readOp("draft-fail"); String before=Files.readString(root.resolve("snapshot.json")); failSave=true;
        var failed=access.readForAftersale(store,op,request("draft-fail"));
        assertEquals(Result.Status.FAILED,failed.status()); assertNull(failed.value());
        assertEquals(before,Files.readString(root.resolve("snapshot.json"))); assertTrue(state().find("DOCUMENT","draft-fail").isEmpty());
        failSave=false;
        assertEquals(Result.Status.VALUE,access.readForAftersale(store,op,request("draft-fail")).status());
        assertEquals(4,state().baseLast());
    }
    @Test public void responseLostAfterCommitQueriesSameKeyAndDoesNotDuplicateTrace() {
        var op=readOp("draft-lost"); var request=request("draft-lost");
        SubmissionPort lost=new SubmissionPort() {
            public Result<WorkingCopy> open(OperationContext o) { return store.open(o); }
            public Result<CommitReceipt> lookupResult(OperationContext o,BusinessRequest r) { return store.lookupResult(o,r); }
            public Result<CommitReceipt> submit(OperationContext o,BusinessRequest r,WorkingCopy w,ChangeSet c) {
                assertEquals(Result.Status.SUCCESS,store.submit(o,r,w,c).status());
                return Result.failed("UNKNOWN","Fictional lost response AFTER successful replacement");
            }
        };
        assertNull(access.readForAftersale(lost,op,request).value());
        assertEquals(4,state().baseLast());
        var retried=access.readForAftersale(store,op,request); assertTrue(retried.value().trace().replayed());
        assertEquals(4,state().baseLast());
        assertEquals("REQUEST_KEY_CONFLICT",access.readForAftersale(store,op,new AftersaleReadRequest(voucher,"draft-lost",List.of("other-line"))).code());
        var otherActor=new OperationContext(deps.manager,op.requestKey(),op.operation(),op.targetId());
        assertEquals("REQUEST_KEY_CONFLICT",access.readForAftersale(store,otherActor,request).code());
    }
    @Test public void failedLookupNeverSubmitsAndAuthorizationAndExactVoucherFailuresReleaseNothing() {
        var op=readOp("draft-denied");
        SubmissionPort unavailable=new SubmissionPort() {
            public Result<WorkingCopy> open(OperationContext o) { throw new AssertionError("Must query first"); }
            public Result<CommitReceipt> lookupResult(OperationContext o,BusinessRequest r) { return Result.failed("UNKNOWN","Fictional unavailable query"); }
            public Result<CommitReceipt> submit(OperationContext o,BusinessRequest r,WorkingCopy w,ChangeSet c) { throw new AssertionError("Unknown result cannot resubmit"); }
        };
        assertEquals("UNKNOWN",access.readForAftersale(unavailable,op,request("draft-denied")).code());
        denyDraft=true; assertEquals("FORBIDDEN",access.readForAftersale(store,op,request("draft-denied")).code()); denyDraft=false;
        assertEquals("NOT_FOUND",access.readForAftersale(store,op,new AftersaleReadRequest("partial-voucher","draft-denied",List.of())).code());
        assertEquals("AUTH_REQUIRED",access.readForAftersale(store,new OperationContext(new Object(),op.requestKey(),op.operation(),op.targetId()),request("draft-denied")).code());
        assertEquals(3,state().baseLast()); assertTrue(state().records("AFTERSALE").isEmpty());
    }
    @Test public void tracedDraftCannotDeleteAndBStyleCancellationRetainsTraceWithoutBusinessEffects() throws Exception {
        var read=readOp("draft-cancel"); var request=request("draft-cancel");
        assertNotNull(access.readForAftersale(store,read,request).value());
        var delete=op(DraftDeletion.OPERATION,deps.clerk,"draft-cancel"); WorkingCopy wc=store.open(delete).value();
        List<ChangeSet.Removal> group=wc.allRecords().stream().filter(r -> r.id().equals("draft-cancel") || r.data().path("documentId").asText().equals("draft-cancel"))
            .map(r -> new ChangeSet.Removal(r.table(),r.id(),r.version())).toList();
        String before=Files.readString(root.resolve("snapshot.json"));
        assertEquals("DRAFT_HAS_READ_TRACE",store.submit(delete,BusinessRequest.of(new DraftDeletion("draft-cancel",1)),wc,new ChangeSet(List.of(),List.of(),group)).code());
        assertEquals(before,Files.readString(root.resolve("snapshot.json")));
        var cancel=op("CANCEL_AFTERSALE_TEST",deps.clerk,"draft-cancel"); wc=store.open(cancel).value();
        var delta=new ChangeSet(List.of(RecordData.of("DOC_EVENT","cancel-event",new DocEvent("cancel-event","draft-cancel",2,"CANCEL_AFTERSALE","fictional-clerk",null,"{}"))),
            List.of(new ChangeSet.Patch("DOCUMENT","draft-cancel",1,"{\"status\":\"CANCELLED\"}")));
        failSave=true; assertEquals(Result.Status.FAILED,store.submit(cancel,BusinessRequest.of(Map.of("target","draft-cancel")),wc,delta).status());
        assertEquals(before,Files.readString(root.resolve("snapshot.json"))); failSave=false;
        assertEquals(Result.Status.SUCCESS,store.submit(cancel,BusinessRequest.of(Map.of("target","draft-cancel")),wc,delta).status());
        assertEquals("AFTERSALE_CLOSED",access.readForAftersale(store,read,request).code());
        assertEquals(2,state().records("STOCK_ENTRY").size()); assertEquals(1,state().records("MONEY_ENTRY").size());
        assertTrue(state().records("REDEMPTION").isEmpty()); assertEquals(1,state().records("AFTERSALE").size());
        store.close(); reopen(); assertEquals("CANCELLED",state().find("DOCUMENT","draft-cancel").orElseThrow().data().path("status").asText());
    }
}
