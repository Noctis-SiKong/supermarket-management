package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.fixture.FictionalDependencies;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class StoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    FictionalDependencies dependencies = new FictionalDependencies();
    BusinessRequest original = BusinessRequest.of(Map.of("name", "虚构商品"));
    private DataRoot root() throws Exception {
        Path project = temp.newFolder().toPath(); Files.writeString(project.resolve("pom.xml"), "test");
        Files.writeString(project.resolve("README.md"), "test"); Files.createDirectory(project.resolve("docs"));
        return DataRoot.forProject(project);
    }
    private JsonStore store(DataRoot root) throws Exception { return store(root, p -> {}); }
    private JsonStore store(DataRoot root, JsonStore.BeforeReplace fault) throws Exception {
        // Generic fixture validator permits TEST records only; no production business validation is claimed.
        return new JsonStore(root, dependencies, Map.of("TEST", (op,a,r,w,c) ->
            c.patches().isEmpty() && c.inserts().stream().allMatch(x -> x.table().equals("TEST"))
            ? Result.value(null) : Result.rejected("INVALID_INPUT", "test only")), w -> {}, fault);
    }
    private OperationContext op() { return new OperationContext(dependencies.manager, UUID.randomUUID().toString(), "TEST", null); }
    private ChangeSet delta(String id) { return new ChangeSet(List.of(RecordData.of("TEST",id,Map.of("value","虚构"))),List.of()); }
    @Test public void replayBeforeStaleAndLookupRequiresAllFourMatchesAfterRestart() throws Exception {
        DataRoot root = root(); OperationContext op = op();
        try (JsonStore store = store(root)) {
            WorkingCopy wc = store.open(op).value();
            assertEquals(Result.Status.SUCCESS, store.submit(op,original,wc,delta("first")).status());
            assertTrue(store.submit(op,original,wc,delta("ignored-on-replay")).value().replayed());
            assertEquals(1, store.open(op).value().baseLast());
            assertEquals("REQUEST_KEY_CONFLICT",store.lookupResult(op,BusinessRequest.of(Map.of("name","different"))).code());
            assertEquals("REQUEST_KEY_CONFLICT",store.lookupResult(new OperationContext(dependencies.clerk,op.requestKey(),"TEST",null),original).code());
            assertEquals("REQUEST_KEY_CONFLICT",store.lookupResult(new OperationContext(dependencies.manager,op.requestKey(),"OTHER",null),original).code());
            assertEquals("AUTH_REQUIRED",store.lookupResult(new OperationContext(new Object(),op.requestKey(),"TEST",null),original).code());
        }
        try (JsonStore store = store(root)) {
            Result<CommitReceipt> receipt = store.lookupResult(op, original);
            assertEquals(Result.Status.SUCCESS,receipt.status()); assertEquals(1,receipt.value().commitOrdinal());
            assertTrue(receipt.value().replayed());
            assertEquals(Result.Status.NOT_FOUND,store.lookupResult(op(),original).status());
        }
    }
    @Test public void staleAndForgedHandlesCannotOverwriteAndSecondWriterCannotStart() throws Exception {
        DataRoot root = root();
        try (JsonStore store = store(root)) {
            OperationContext a = op(), b = op(); WorkingCopy wa = store.open(a).value(), wb = store.open(b).value();
            assertEquals(Result.Status.SUCCESS,store.submit(a,original,wa,delta("one")).status());
            assertEquals("STALE_WORKING_COPY",store.submit(b,original,wb,delta("two")).code());
            WorkingCopy real = store.open(b).value();
            WorkingCopy forged = new WorkingCopy(real.workingCopyId(),real.baseLast(),real.allRecords());
            assertEquals("STALE_WORKING_COPY",store.submit(b,original,forged,delta("two")).code());
            try { store(root).close(); fail("Second writer acquired lock"); } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("WRITE_BLOCKED")); }
        }
    }
    @Test public void atomicReplaceFailureKeepsPreviousSnapshotAndOrdinal() throws Exception {
        DataRoot root = root();
        try (JsonStore store = store(root)) { OperationContext op = op(); assertEquals(Result.Status.SUCCESS,store.submit(op,original,store.open(op).value(),delta("one")).status()); }
        byte[] old = Files.readAllBytes(root.resolve("snapshot.json"));
        OperationContext failed = op();
        try (JsonStore store = store(root, p -> { throw new java.io.IOException("Injected before atomic replace"); })) {
            assertEquals(Result.Status.FAILED, store.submit(failed,original,store.open(failed).value(),delta("two")).status());
            assertArrayEquals(old,Files.readAllBytes(root.resolve("snapshot.json")));
            assertEquals(1, store.open(failed).value().baseLast());
        }
        try (JsonStore store = store(root)) {
            assertEquals(Result.Status.NOT_FOUND,store.lookupResult(failed,original).status());
            assertEquals(Result.Status.SUCCESS,store.submit(failed,original,store.open(failed).value(),delta("two")).status());
            assertEquals(2,store.open(failed).value().baseLast());
        }
    }
    @Test public void malformedRecoveryBlocksAndDoesNotLoadTemporarySnapshot() throws Exception {
        DataRoot root = root();
        Files.writeString(root.resolve("snapshot.next.json"), "{broken");
        try (JsonStore store = store(root)) { assertEquals(0,store.open(op()).value().baseLast()); }
        Files.writeString(root.resolve("snapshot.json"), "{broken");
        try { store(root).close(); fail(); } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("RECOVERY_WRITE_BLOCKED")); }
    }
    @Test public void explicitPreviousRecoveryRestoresWholeSnapshotAndContinuesAtLastPlusOne() throws Exception {
        DataRoot root = root(); OperationContext first = op(), second = op();
        try (JsonStore store = store(root)) {
            store.submit(first,original,store.open(first).value(),delta("one"));
            store.submit(second,original,store.open(second).value(),delta("two"));
        }
        Files.writeString(root.resolve("snapshot.json"),"{corrupt");
        var fixtureValidator = Map.<String,CommitValidator>of("TEST",(op,a,r,w,c) -> Result.value(null));
        try (JsonStore recovered = new JsonStore(root,dependencies,fixtureValidator,w -> {},p -> {},JsonStore.RecoveryMode.PREVIOUS,List.of())) {
            assertEquals(1,recovered.open(first).value().baseLast());
            assertEquals(Result.Status.SUCCESS,recovered.lookupResult(first,original).status());
            assertEquals(Result.Status.NOT_FOUND,recovered.lookupResult(second,original).status());
            assertEquals(1,recovered.open(first).value().records("TEST").size());
            assertEquals(2,recovered.submit(second,original,recovered.open(second).value(),delta("two")).value().commitOrdinal());
        }
    }
    @Test public void overlayDeduplicatesIdenticalDeltaRejectsConflictingDeltaAndIsImmutable() {
        RecordData one = RecordData.of("TEST","one",Map.of("number",1));
        ChangeSet delta = new ChangeSet.Builder().add(one).add(one).build(); assertEquals(1,delta.inserts().size());
        WorkingCopy wc = new WorkingCopy("wc",0,List.of()); assertEquals(1,wc.overlay(delta).records("TEST").size());
        assertTrue(wc.records("TEST").isEmpty()); one.data().deepCopy(); assertEquals(1,one.data().path("number").asInt());
        try { new ChangeSet.Builder().add(one).add(RecordData.of("TEST","one",Map.of("number",2))); fail(); } catch (IllegalArgumentException expected) { assertEquals("EVENT_CONFLICT",expected.getMessage()); }
    }
    @Test public void canonicalFingerprintSortsPropertiesPreservesArrayOrderAndDecimalValue() {
        BusinessRequest a = new BusinessRequest("{\"b\":[1,2],\"amount\":1.00}");
        BusinessRequest b = new BusinessRequest("{\"amount\":1.0,\"b\":[1,2]}");
        assertEquals(JsonCodec.fingerprint("T","A",a),JsonCodec.fingerprint("T","A",b));
        assertNotEquals(JsonCodec.fingerprint("T","A",a),JsonCodec.fingerprint("T","A",new BusinessRequest("{\"amount\":1,\"b\":[2,1]}")));
    }
}
