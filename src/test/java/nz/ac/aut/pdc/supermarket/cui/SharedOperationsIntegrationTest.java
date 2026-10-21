package nz.ac.aut.pdc.supermarket.cui;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.app.AOperations.Opening;
import nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut;
import nz.ac.aut.pdc.supermarket.app.AOperations.Sale;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.auth.SharedIdentityResolver;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedOperationsIntegrationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ciflowCanSeedSaleReadItBackAndSubmitAftersale() throws Exception {
        Path project = temporaryFolder.newFolder("project").toPath();
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# test");
        Files.createDirectories(project.resolve("docs"));
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        String hash = hasher.hash("secret".toCharArray());
        List<JsonStore.Credential> credentials = List.of(
                new JsonStore.Credential("mgr", "manager", hash, OperationContext.Role.MANAGER),
                new JsonStore.Credential("clerk", "clerk", hash, OperationContext.Role.CLERK));

        try (BApplication app = new BApplication(project, credentials)) {
            AuthResult.Success manager = (AuthResult.Success) app.authService()
                    .login("manager", "secret".toCharArray());
            AuthResult.Success clerk = (AuthResult.Success) app.authService()
                    .login("clerk", "secret".toCharArray());
            IdClock ids = new IdClock(Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), java.time.ZoneOffset.UTC));
            AOperations a = new AOperations(ids,
                    new SharedIdentityResolver(app.authService()), new SharedStockAdapter());

            submit(a, app.store(), manager.session(), "PRODUCT_PUT",
                    new ProductPut(new Product("00123456", "测试商品", "盒", new BigDecimal("3.33"), null), 0));
            submit(a, app.store(), manager.session(), "OPENING",
                    new Opening(new AOperations.Item("00123456", 10), Position.S, "期初"));
            OperationContext saleOp = new OperationContext(
                    clerk.session(), ids.newRequestKey(), "SALE", null);
            submit(a, app.store(), saleOp, new Sale(List.of(new AOperations.Item("00123456", 1)), true));
            String saleLineId = ids.entityId(saleOp.requestKey(), "line:0");

            SharedOperations operations = new SharedOperations(app.store(), ids);
            CuiActor actor = new CuiActor(clerk.accountId(), Role.CLERK, clerk.session());
            AftersaleRequest request = new AftersaleRequest(
                    saleLineId, RedemptionKind.RETURN, 1, false, false,
                    AftersaleReasonKind.OTHER, "顾客原因", null, 0, ids.newRequestKey());
            OperationFeedback feedback = operations.aftersale(actor, request);
            OperationFeedback retry = operations.aftersale(actor, request);

            assertTrue(feedback.message(), feedback.success());
            assertTrue(retry.message(), retry.success());
            assertEquals(1, app.store().open(new OperationContext(
                    manager.session(), UUID.randomUUID().toString(), "READ", null))
                    .value().records("REDEMPTION").size());
        }
    }

    private void submit(
            AOperations operations,
            JsonStore store,
            Object session,
            String kind,
            Object input) {
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
