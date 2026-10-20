package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.fixture.FictionalDependencies;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.app.AOperations.*;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.promotion.Promotion;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.Assert.*;

/** Real A acceptance/pricing/persistence; fictional identities and capturing stock only. */
public class SubmissionTimeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-20T15:59:58Z");
        public ZoneId getZone(){ return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone){ return this; }
        public Instant instant(){ return now; }
    }
    final MutableClock clock=new MutableClock(); final FictionalDependencies deps=new FictionalDependencies();
    IdClock ids; AOperations a; JsonStore store; DataRoot root; int samples; boolean advanceBeforeReplace;
    @Before public void start() throws Exception {
        Path p=temp.newFolder().toPath(); Files.writeString(p.resolve("README.md"),"fixture"); Files.writeString(p.resolve("pom.xml"),"fixture"); Files.createDirectory(p.resolve("docs"));
        root=DataRoot.forProject(p); ids=new IdClock(clock); a=new AOperations(ids,deps,deps); open();
        success("PRODUCT_PUT",new ProductPut(new Product("00123456","虚构牛奶","盒",new BigDecimal("10.00"),null),0));
        success("OPENING",new Opening(new Item("00123456",10),Position.S,"合法虚构期初"));
    }
    void open() throws Exception {
        store=new JsonStore(root,deps,a.validators(),a::validateRecovered,p -> {
            if (advanceBeforeReplace) clock.now=Instant.parse("2026-10-20T16:00:00Z");
        },JsonStore.RecoveryMode.CURRENT,List.of(),() -> { samples++; return clock.instant(); });
    }
    @After public void close() throws Exception { store.close(); }
    OperationContext op(String kind){ return new OperationContext(deps.manager,ids.newRequestKey(),kind,null); }
    Prepared prepare(OperationContext op,Object input){ var result=a.prepare(store,op,BusinessRequest.of(input)); assertEquals(result.detail(),Result.Status.PREPARED,result.status()); return result.value(); }
    void success(String kind,Object input){ OperationContext op=op(kind); var p=prepare(op,input); var r=store.submit(op,p.request(),p.workingCopy(),p.changes()); assertEquals(r.detail(),Result.Status.SUCCESS,r.status()); }
    WorkingCopy state(){ return store.open(op("READ")).value(); }
    RecordData document(String id){ return state().find("DOCUMENT",id).orElseThrow(); }
    @Test public void receiptCreationAndReviewUseSeparateLockTimesAndPreserveExistingCreation() throws Exception {
        OperationContext create=op("RECEIPT_CREATE");
        Prepared pending=prepare(create,new ReceiptCreate(List.of(new Item("00123456",2)),List.of()));
        String id=ids.entityId(create.requestKey(),"document"), creation="2026-10-20T16:00:01Z";
        clock.now=Instant.parse(creation); int before=samples;
        assertEquals(Result.Status.SUCCESS,store.submit(create,pending.request(),pending.workingCopy(),pending.changes()).status());
        assertEquals(before+1,samples); assertEquals(creation,document(id).as(DocumentHeader.class).createdAt());
        assertEquals(creation,state().find("DOC_EVENT",ids.entityId(create.requestKey(),"event")).orElseThrow().as(DocEvent.class).occurredAt());
        OperationContext review=op("RECEIPT_REVIEW");
        Prepared approval=prepare(review,new ReceiptReview(id,1,true,"虚构审核",List.of()));
        String accepted="2026-10-21T16:00:01Z"; clock.now=Instant.parse(accepted); before=samples;
        assertEquals(Result.Status.SUCCESS,store.submit(review,approval.request(),approval.workingCopy(),approval.changes()).status());
        assertEquals(before+1,samples);
        DocumentHeader header=document(id).as(DocumentHeader.class); assertEquals(creation,header.createdAt()); assertEquals(accepted,header.confirmedAt());
        assertEquals(accepted,state().find("RECEIPT_REVIEW",ids.entityId(review.requestKey(),"review")).orElseThrow().as(Review.class).reviewedAt());
        assertEquals(accepted,state().find("DOC_EVENT",ids.entityId(review.requestKey(),"event")).orElseThrow().as(DocEvent.class).occurredAt());
        String original=JsonCodec.write(document(id)); byte[] bytes=Files.readAllBytes(root.resolve("snapshot.json")); before=samples;
        clock.now=clock.now.plusSeconds(86_400);
        assertTrue(store.submit(review,approval.request(),approval.workingCopy(),approval.changes()).value().replayed());
        assertEquals(before,samples); assertArrayEquals(bytes,Files.readAllBytes(root.resolve("snapshot.json"))); assertEquals(original,JsonCodec.write(document(id)));
        store.close(); open(); assertEquals(original,JsonCodec.write(document(id)));
    }
    void activity(){ success("PROMOTION_PUT",new PromotionPut(new Promotion("p",Promotion.Kind.PERCENT,new BigDecimal("50"),null,null,null,"2026-10-20","2026-10-20",Set.of("00123456")),0)); }
    @Test public void crossingMidnightRejectsOldPriceThenRebuildStampsNewAcceptanceNotPreview() throws Exception {
        activity(); OperationContext sale=op("SALE"); Sale input=new Sale(List.of(new Item("00123456",1)),true);
        Prepared before=prepare(sale,input); byte[] snapshot=Files.readAllBytes(root.resolve("snapshot.json"));
        clock.now=Instant.parse("2026-10-20T16:00:00Z");
        assertEquals("STALE_PRICE",store.submit(sale,before.request(),before.workingCopy(),before.changes()).code());
        assertArrayEquals(snapshot,Files.readAllBytes(root.resolve("snapshot.json"))); assertTrue(state().records("SALE").isEmpty());
        Prepared rebuilt=prepare(sale,input); clock.now=clock.now.plusSeconds(2);
        assertEquals(Result.Status.SUCCESS,store.submit(sale,rebuilt.request(),rebuilt.workingCopy(),rebuilt.changes()).status());
        String id=ids.entityId(sale.requestKey(),"document"); DocumentHeader header=document(id).as(DocumentHeader.class);
        assertEquals(clock.now.toString(),header.createdAt()); assertEquals(header.createdAt(),header.confirmedAt());
        assertEquals(header.confirmedAt(),state().find("DOC_EVENT",ids.entityId(sale.requestKey(),"event")).orElseThrow().as(DocEvent.class).occurredAt());
        assertEquals(new BigDecimal("10.00"),state().find("SALE",id).orElseThrow().as(SaleRecord.class).quote().paidTotal());
        String saved=JsonCodec.write(document(id)); store.close(); open(); assertEquals(saved,JsonCodec.write(document(id)));
    }
    @Test public void pricingUsesAcceptanceEvenIfActualReplacementOccursAfterMidnightAndRecoveryRejectsWrongTime() throws Exception {
        activity(); OperationContext sale=op("SALE"); Prepared p=prepare(sale,new Sale(List.of(new Item("00123456",1)),true));
        clock.now=Instant.parse("2026-10-20T15:59:59Z"); advanceBeforeReplace=true; int before=samples;
        assertEquals(Result.Status.SUCCESS,store.submit(sale,p.request(),p.workingCopy(),p.changes()).status()); assertEquals(before+1,samples);
        String id=ids.entityId(sale.requestKey(),"document"); DocumentHeader header=document(id).as(DocumentHeader.class);
        assertEquals("2026-10-20T15:59:59Z",header.createdAt()); assertEquals(header.createdAt(),header.confirmedAt());
        assertEquals(new BigDecimal("5.00"),state().find("SALE",id).orElseThrow().as(SaleRecord.class).quote().paidTotal());
        store.close(); open(); assertEquals(header,document(id).as(DocumentHeader.class));
        // Corrupt only the saved confirmation time. The former activity-date fallback must not mask it.
        store.close(); JsonStore.Snapshot saved=JsonCodec.read(Files.readString(root.resolve("snapshot.json")),JsonStore.Snapshot.class);
        var records=new ArrayList<>(saved.records());
        for(int i=0;i<records.size();i++) if(records.get(i).key().equals("DOCUMENT:"+id)) {
            var data=(com.fasterxml.jackson.databind.node.ObjectNode) records.get(i).data(); data.put("confirmedAt","2026-10-20T16:00:00Z");
            records.set(i,new RecordData("DOCUMENT",id,records.get(i).version(),JsonCodec.write(data)));
        }
        Files.writeString(root.resolve("snapshot.json"),JsonCodec.write(new JsonStore.Snapshot(saved.schemaVersion(),saved.lastCommitOrdinal(),records,saved.results(),saved.credentials())));
        try { open(); fail("Invalid activity/time snapshot accepted"); } catch(java.io.IOException expected){ assertTrue(expected.getMessage().contains("RECOVERY_WRITE_BLOCKED")); }
    }
    @Test public void historicalCreationAndNonManagedTimeInputsCannotBeSilentlyIgnored() {
        OperationContext create=op("RECEIPT_CREATE"); Prepared p=prepare(create,new ReceiptCreate(List.of(new Item("00123456",1)),List.of()));
        assertEquals(Result.Status.SUCCESS,store.submit(create,p.request(),p.workingCopy(),p.changes()).status());
        String id=ids.entityId(create.requestKey(),"document");
        OperationContext review=op("RECEIPT_REVIEW"); Prepared approval=prepare(review,new ReceiptReview(id,1,true,"虚构审核",List.of()));
        List<ChangeSet.Patch> patches=new ArrayList<>(approval.changes().patches());
        for(int i=0;i<patches.size();i++) if(patches.get(i).table().equals("DOCUMENT")) {
            var patch=patches.get(i); var fields=(com.fasterxml.jackson.databind.node.ObjectNode)JsonCodec.tree(patch.changedFields()); fields.put("createdAt","1999-01-01T00:00:00Z");
            patches.set(i,new ChangeSet.Patch(patch.table(),patch.id(),patch.expectedVersion(),JsonCodec.write(fields)));
        }
        long last=state().baseLast();
        assertEquals(Result.Status.REJECTED,store.submit(review,approval.request(),approval.workingCopy(),new ChangeSet(approval.changes().inserts(),patches)).status());
        assertEquals(last,state().baseLast());
        List<RecordData> inserts=new ArrayList<>(approval.changes().inserts());
        for(int i=0;i<inserts.size();i++) if(inserts.get(i).table().equals("DOC_EVENT")) {
            RecordData event=inserts.get(i); var data=(com.fasterxml.jackson.databind.node.ObjectNode)event.data();
            data.put("detailSnapshot",JsonCodec.write(Map.of("originalSaleTime","1999-01-01T00:00:00Z")));
            inserts.set(i,new RecordData(event.table(),event.id(),event.version(),JsonCodec.write(data)));
        }
        // Only managed timestamps are assigned; a changed historical/input-time payload is still compared.
        assertEquals("REFERENCE_INVALID",store.submit(review,approval.request(),approval.workingCopy(),new ChangeSet(inserts,approval.changes().patches())).code());
        assertEquals(last,state().baseLast());
    }
}
