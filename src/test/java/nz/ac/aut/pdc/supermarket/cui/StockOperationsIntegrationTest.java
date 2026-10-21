package nz.ac.aut.pdc.supermarket.cui;

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
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.app.AOperations.Opening;
import nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.auth.SharedIdentityResolver;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StockOperationsIntegrationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void replenishAndLossSubmitRealChangeSets() throws Exception {
        Path project = temporaryFolder.newFolder("project").toPath();
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# test");
        Files.createDirectories(project.resolve("docs"));
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        String hash = hasher.hash("secret".toCharArray());
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
                    new Opening(new AOperations.Item("00123456", 10), Position.W, "期初W"));
            submit(a, app.store(), manager.session(), "OPENING",
                    new Opening(new AOperations.Item("00123456", 10), Position.S, "期初S"));

            SharedOperations operations = new SharedOperations(app.store(), ids);
            CuiActor actor = new CuiActor(clerk.accountId(), Role.CLERK, clerk.session());
            OperationFeedback replenished = operations.replenish(actor, "00123456", 3);
            OperationFeedback lost = operations.recordLoss(
                    actor, "00123456", nz.ac.aut.pdc.supermarket.stock.Position.S, 2);

            assertTrue(replenished.message(), replenished.success());
            assertTrue(lost.message(), lost.success());
            WorkingCopy state = app.store().open(new OperationContext(
                    manager.session(), UUID.randomUUID().toString(), "READ", null)).value();
            assertEquals(1, state.records("LOSS_RECORD").size());
            assertEquals(5, state.records("STOCK_ENTRY").size());
        }
    }

    private void submit(
            AOperations operations, JsonStore store, Object session, String kind, Object input) {
        OperationContext op = new OperationContext(session, UUID.randomUUID().toString(), kind, null);
        BusinessRequest request = BusinessRequest.of(input);
        Result<AOperations.Prepared> prepared = operations.prepare(store, op, request);
        assertTrue(prepared.code(), prepared.hasValue());
        Result<SubmissionPort.CommitReceipt> submitted = store.submit(
                op, request, prepared.value().workingCopy(), prepared.value().changes());
        assertEquals(submitted.code() + " / " + submitted.detail(),
                Result.Status.SUCCESS, submitted.status());
    }
}
