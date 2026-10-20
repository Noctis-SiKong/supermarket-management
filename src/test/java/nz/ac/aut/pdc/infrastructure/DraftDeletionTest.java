package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.fixture.FictionalDependencies;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real A single-snapshot saves; fictional B eligibility response, no aftersale algorithms. */
public class DraftDeletionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    final FictionalDependencies identities = new FictionalDependencies();
    final Instant time = Instant.parse("2026-10-20T15:59:59Z");
    JsonStore store; DataRoot root; boolean failSave, allowDelete = true; int reviewed;
    @Before public void setup() throws Exception {
        Path p = temp.newFolder().toPath(); Files.writeString(p.resolve("README.md"),"fixture"); Files.writeString(p.resolve("pom.xml"),"fixture"); Files.createDirectory(p.resolve("docs"));
        root = DataRoot.forProject(p); open();
    }
    void open() throws Exception {
        Map<String,CommitValidator> validators = Map.of(
            "FIXTURE_DRAFT",(op,a,r,w,c,t) -> Result.value(null),
            DraftDeletion.OPERATION,(op,a,r,w,c,t) -> { reviewed++; return allowDelete ? Result.value(null) : Result.rejected("DRAFT_NOT_DELETABLE","Fictional B rejection"); });
        store = new JsonStore(root,identities,validators,w -> {},p -> { if (failSave) throw new java.io.IOException("Injected deletion save failure"); },
            JsonStore.RecoveryMode.CURRENT,List.of(),() -> time);
    }
    @After public void close() throws Exception { store.close(); }
    OperationContext op(String operation,String target) { return new OperationContext(identities.manager,UUID.randomUUID().toString(),operation,target); }
    WorkingCopy state() { return store.open(op("READ",null)).value(); }
    String seed(String status,String action,boolean money,boolean replacement) {
        OperationContext create = op("FIXTURE_DRAFT",null); String id = UUID.randomUUID().toString(), line = UUID.randomUUID().toString();
        ChangeSet.Builder b = new ChangeSet.Builder();
        if (state().find("PRODUCT","00123456").isEmpty()) b.add(RecordData.of("PRODUCT","00123456",Map.of("barcode","00123456")));
        boolean posted = status.equals("CONFIRMED");
        b.add(RecordData.of("DOCUMENT",id,new DocumentHeader(id,"RETURN",status,create.requestKey(),"fixture","fictional-manager",null,posted ? "fictional-manager" : null,posted ? "placeholder" : null,null)));
        b.add(RecordData.of("AFTERSALE",id,Map.of("documentId",id)));
        b.add(RecordData.of("DOC_LINE",line,new DocumentLine(line,id,"REDEMPTION","00123456",1)));
        String event = UUID.randomUUID().toString();
        b.add(RecordData.of("DOC_EVENT",event,new DocEvent(event,id,1,action,"fictional-manager",null,"{}")));
        if (replacement) b.add(RecordData.of("REPLACEMENT",line,Map.of("lineId",line,"aftersaleId",id)));
        if (money) {
            String m = UUID.randomUUID().toString();
            b.add(RecordData.of("MONEY_ENTRY",m,new MoneyEntry(m,id,java.math.BigDecimal.ZERO,create.requestKey(),null)));
        }
        var result = store.submit(create,BusinessRequest.of(Map.of("fixture",id)),store.open(create).value(),b.build());
        assertEquals(result.detail(),Result.Status.SUCCESS,result.status()); return id;
    }
    ChangeSet group(String id) {
        WorkingCopy wc = state(); Set<String> lineIds = new HashSet<>();
        wc.records("DOC_LINE").forEach(r -> { if (r.data().path("documentId").asText().equals(id)) lineIds.add(r.id()); });
        ChangeSet.Builder b = new ChangeSet.Builder();
        for (RecordData r : wc.allRecords()) if (r.table().equals("DOCUMENT") && r.id().equals(id)
            || r.table().equals("AFTERSALE") && r.id().equals(id)
            || Set.of("DOC_LINE","DOC_EVENT").contains(r.table()) && r.data().path("documentId").asText().equals(id)
            || r.table().equals("REPLACEMENT") && lineIds.contains(r.id()))
            b.remove(new ChangeSet.Removal(r.table(),r.id(),r.version()));
        return b.build();
    }
    Result<CommitReceipt> delete(OperationContext op, String id,ChangeSet group) {
        return store.submit(op,BusinessRequest.of(new DraftDeletion(id,1)),store.open(op).value(),group);
    }
    @Test public void completeConditionalDraftGroupDeletesAtomicallyAndReplaySurvivesRestart() throws Exception {
        String id = seed("DRAFT","CREATE",false,true); ChangeSet group = group(id);
        assertEquals(5,group.removals().size()); OperationContext op = op(DraftDeletion.OPERATION,id);
        var success = delete(op,id,group); assertEquals(success.detail(),Result.Status.SUCCESS,success.status());
        for (ChangeSet.Removal r : group.removals()) assertTrue(state().find(r.table(),r.id()).isEmpty());
        assertTrue(state().records("DOC_EVENT").isEmpty()); assertEquals(1,state().allRecords().size());
        assertEquals(2,state().baseLast());
        store.close(); open();
        assertEquals(Result.Status.SUCCESS,store.submit(op,BusinessRequest.of(new DraftDeletion(id,1)),null,group).status());
        assertEquals(1,reviewed); assertEquals(2,state().baseLast());
    }
    @Test public void headerOnlyDraftDoesNotRequireExtensionLinesAttachmentsOrMoney() {
        OperationContext create=op("FIXTURE_DRAFT",null); String id=UUID.randomUUID().toString();
        ChangeSet changes=new ChangeSet(List.of(RecordData.of("DOCUMENT",id,new DocumentHeader(id,"RETURN","DRAFT",create.requestKey(),
            "fixture","fictional-manager",null,null,null,null))),List.of());
        assertEquals(Result.Status.SUCCESS,store.submit(create,BusinessRequest.of(Map.of("fixture","header only")),store.open(create).value(),changes).status());
        assertEquals(1,group(id).removals().size());
        assertEquals(Result.Status.SUCCESS,delete(op(DraftDeletion.OPERATION,id),id,group(id)).status());
        assertTrue(state().allRecords().isEmpty());
    }
    @Test public void tracePostedDocumentAndPostedEffectAreProtectedEvenWhenFakeBAllows() throws Exception {
        for (String mode : List.of("trace","confirmed","money")) {
            String id = seed(mode.equals("confirmed") ? "CONFIRMED" : "DRAFT",mode.equals("trace") ? "READ_FOR_AFTERSALE" : "CREATE",mode.equals("money"),false);
            byte[] bytes = Files.readAllBytes(root.resolve("snapshot.json")); long last = state().baseLast();
            assertEquals(Result.Status.REJECTED,delete(op(DraftDeletion.OPERATION,id),id,group(id)).status());
            assertArrayEquals(bytes,Files.readAllBytes(root.resolve("snapshot.json"))); assertEquals(last,state().baseLast());
            assertTrue(state().find("DOCUMENT",id).isPresent());
        }
    }
    @Test public void saveFailureDoesNotExposePartialDeletionAndSameKeyCanRetry() throws Exception {
        String id=seed("DRAFT","CREATE",false,false); ChangeSet group=group(id); OperationContext op=op(DraftDeletion.OPERATION,id);
        byte[] bytes=Files.readAllBytes(root.resolve("snapshot.json")); failSave=true;
        assertEquals(Result.Status.FAILED,delete(op,id,group).status());
        assertArrayEquals(bytes,Files.readAllBytes(root.resolve("snapshot.json")));
        for (ChangeSet.Removal r:group.removals()) assertTrue(state().find(r.table(),r.id()).isPresent());
        store.close(); open(); failSave=false;
        assertEquals(Result.Status.NOT_FOUND,store.lookupResult(op,BusinessRequest.of(new DraftDeletion(id,1))).status());
        assertEquals(Result.Status.SUCCESS,delete(op,id,group).status());
    }
    @Test public void incompleteOrUnrelatedGroupAndStaleVersionAreRejectedWithoutSaving() throws Exception {
        String id=seed("DRAFT","CREATE",false,false); ChangeSet group=group(id); byte[] bytes=Files.readAllBytes(root.resolve("snapshot.json"));
        ChangeSet incomplete=new ChangeSet(List.of(),List.of(),group.removals().subList(1,group.removals().size()));
        assertEquals("INVALID_DELETION_GROUP",delete(op(DraftDeletion.OPERATION,id),id,incomplete).code());
        var stale = new ArrayList<>(group.removals()); var first=stale.get(0); stale.set(0,new ChangeSet.Removal(first.table(),first.id(),99));
        assertEquals("STALE_WORKING_COPY",delete(op(DraftDeletion.OPERATION,id),id,new ChangeSet(List.of(),List.of(),stale)).code());
        var unrelated=new ArrayList<>(group.removals()); unrelated.add(new ChangeSet.Removal("PRODUCT","00123456",1));
        assertEquals("INVALID_DELETION_GROUP",delete(op(DraftDeletion.OPERATION,id),id,new ChangeSet(List.of(),List.of(),unrelated)).code());
        allowDelete=false; assertEquals("DRAFT_NOT_DELETABLE",delete(op(DraftDeletion.OPERATION,id),id,group).code());
        assertArrayEquals(bytes,Files.readAllBytes(root.resolve("snapshot.json")));
    }
    @Test public void remainingExternalEventReferencePreventsWholeGroupRemoval() throws Exception {
        String target=seed("DRAFT","CREATE",false,false), other=seed("DRAFT","CREATE",false,false);
        OperationContext create=op("FIXTURE_DRAFT",null); String id=UUID.randomUUID().toString();
        ChangeSet changes=new ChangeSet(List.of(RecordData.of("DOC_EVENT",id,new DocEvent(id,other,2,"DRAFT_UPDATE","fictional-manager",null,
            JsonCodec.write(Map.of("targetDocumentId",target))))),List.of());
        assertEquals(Result.Status.SUCCESS,store.submit(create,BusinessRequest.of(Map.of("fixture","external reference")),store.open(create).value(),changes).status());
        byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));
        assertEquals("REFERENCE_INVALID",delete(op(DraftDeletion.OPERATION,target),target,group(target)).code());
        assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));
    }
}
