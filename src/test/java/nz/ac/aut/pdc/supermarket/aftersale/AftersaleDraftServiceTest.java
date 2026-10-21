package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.app.AOperations.Opening;
import nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut;
import nz.ac.aut.pdc.supermarket.app.AOperations.Sale;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.auth.SharedIdentityResolver;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.cui.BApplication;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AftersaleDraftServiceTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createTraceThenCancelDraft() throws Exception {
        Path project = temporaryFolder.newFolder("project").toPath();
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# test");
        Files.createDirectories(project.resolve("docs"));
        String hash = new Sha256PasswordHasher().hash("secret".toCharArray());
        List<JsonStore.Credential> credentials = List.of(
                new JsonStore.Credential("mgr", "manager", hash, OperationContext.Role.MANAGER),
                new JsonStore.Credential("clerk", "clerk", hash, OperationContext.Role.CLERK));
        IdClock ids = new IdClock(Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneOffset.UTC));

        try (BApplication app = new BApplication(project, credentials, ids)) {
            AuthResult.Success manager = (AuthResult.Success) app.authService()
                    .login("manager", "secret".toCharArray());
            AuthResult.Success clerk = (AuthResult.Success) app.authService()
                    .login("clerk", "secret".toCharArray());
            AOperations a = new AOperations(ids,
                    new SharedIdentityResolver(app.authService()), new SharedStockAdapter());
            submit(a, app.store(), manager.session(), "PRODUCT_PUT",
                    new ProductPut(new Product("00123456", "测试商品", "盒", new BigDecimal("3.33"), null), 0));
            submit(a, app.store(), manager.session(), "OPENING",
                    new Opening(new AOperations.Item("00123456", 10), Position.S, "期初"));
            OperationContext saleOp = new OperationContext(
                    clerk.session(), ids.newRequestKey(), "SALE", null);
            submit(a, app.store(), saleOp, new Sale(
                    List.of(new AOperations.Item("00123456", 1)), true));
            String saleLineId = ids.entityId(saleOp.requestKey(), "line:0");

            OperationContext.ActorContext actor = new OperationContext.ActorContext(
                    "clerk", OperationContext.Role.CLERK);
            AftersaleDraftService drafts = new AftersaleDraftService(app.store());
            String draftKey = ids.newRequestKey();
            String draftId = ids.entityId(draftKey, "draft");

            Result<?> created = drafts.create(new OperationContext(
                    clerk.session(), draftKey, AftersaleDraftOperations.CREATE, draftId), actor);
            assertEquals(created.code(), Result.Status.SUCCESS, created.status());

            OperationContext readOp = new OperationContext(
                    clerk.session(), ids.newRequestKey(), AftersaleDraftOperations.READ, draftId);
            Result<?> traced = drafts.recordRead(readOp, actor,
                    new OriginalSaleLine(saleLineId, "00123456", 1,
                            new BigDecimal("3.33"), Instant.parse("2026-10-01T00:00:00Z")));
            Result<?> tracedAgain = drafts.recordRead(readOp, actor,
                    new OriginalSaleLine(saleLineId, "00123456", 1,
                            new BigDecimal("3.33"), Instant.parse("2026-10-01T00:00:00Z")));
            assertEquals(traced.code(), Result.Status.SUCCESS, traced.status());
            assertEquals(tracedAgain.code(), Result.Status.SUCCESS, tracedAgain.status());

            OperationContext cancelOp = new OperationContext(
                    clerk.session(), ids.newRequestKey(), AftersaleDraftOperations.CANCEL, draftId);
            Result<?> cancelled = drafts.cancel(cancelOp, actor);
            Result<?> retried = drafts.cancel(cancelOp, actor);
            assertEquals(cancelled.code(), Result.Status.SUCCESS, cancelled.status());
            assertEquals(retried.code(), Result.Status.SUCCESS, retried.status());

            WorkingCopy state = app.store().open(new OperationContext(
                    clerk.session(), UUID.randomUUID().toString(), "READ", null)).value();
            DocumentHeader header = state.find("DOCUMENT", draftId).orElseThrow()
                    .as(DocumentHeader.class);
            assertEquals("CANCELLED", header.status());
            assertEquals(1, state.records("DOC_EVENT").stream()
                    .filter(r -> r.id().equals(ids.entityId(readOp.requestKey(),"aftersale-read"))).count());
            assertEquals(0, state.records("REDEMPTION").size());
            assertEquals(2, state.records("STOCK_ENTRY").size());
            assertEquals(1, state.records("MONEY_ENTRY").size());

            String deleteKey = ids.newRequestKey();
            String deleteId = ids.entityId(deleteKey, "draft");
            assertEquals(Result.Status.SUCCESS, drafts.create(new OperationContext(
                    clerk.session(), deleteKey, AftersaleDraftOperations.CREATE, deleteId),
                    actor).status());
            Result<?> deleted = drafts.delete(new OperationContext(
                    clerk.session(), ids.newRequestKey(),
                    nz.ac.aut.pdc.shared.DraftDeletion.OPERATION, deleteId), actor);
            assertEquals(deleted.code(), Result.Status.SUCCESS, deleted.status());
            WorkingCopy afterDelete = app.store().open(new OperationContext(
                    clerk.session(), UUID.randomUUID().toString(), "READ", null)).value();
            assertTrue(afterDelete.find("DOCUMENT", deleteId).isEmpty());
        }
    }

    private void submit(
            AOperations operations, JsonStore store, Object session, String kind, Object input) {
        OperationContext op = new OperationContext(session, UUID.randomUUID().toString(), kind, null);
        submit(operations, store, op, input);
    }

    private void submit(AOperations operations, JsonStore store, OperationContext op, Object input) {
        BusinessRequest request = BusinessRequest.of(input);
        Result<AOperations.Prepared> prepared = operations.prepare(store, op, request);
        assertTrue(prepared.code(), prepared.hasValue());
        Result<SubmissionPort.CommitReceipt> submitted = store.submit(
                op, request, prepared.value().workingCopy(), prepared.value().changes());
        assertEquals(submitted.code() + " / " + submitted.detail(),
                Result.Status.SUCCESS, submitted.status());
    }
}
