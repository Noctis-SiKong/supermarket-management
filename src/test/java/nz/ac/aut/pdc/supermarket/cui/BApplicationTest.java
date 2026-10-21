package nz.ac.aut.pdc.supermarket.cui;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleChangeSetAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommand;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommandLine;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleResult;
import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.Redemption;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BApplicationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void realJsonStoreAcceptsBServiceAndAftersaleChangeSet() throws Exception {
        Path project = temporaryFolder.newFolder("project").toPath();
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# test");
        Files.createDirectories(project.resolve("docs"));
        String hash = new Sha256PasswordHasher().hash("secret".toCharArray());
        List<JsonStore.Credential> credentials = List.of(
                new JsonStore.Credential("acc-1", "alice", hash, OperationContext.Role.CLERK));

        try (BApplication app = new BApplication(project, credentials)) {
            AuthResult.Success login = (AuthResult.Success) app.authService()
                    .login("alice", "secret".toCharArray());
            OperationContext submitOp = new OperationContext(
                    login.session(), "11111111-1111-1111-1111-111111111111",
                    AftersaleChangeSetAssembler.OPERATION, "AF-1");
            Result<WorkingCopy> opened = app.store().open(submitOp);
            assertEquals(Result.Status.VALUE, opened.status());

            AftersaleResult.Prepared prepared = new AftersaleResult.Prepared(
                    List.of(redemption()), List.of(), List.of(), List.of());
            BusinessRequest request = BusinessRequest.of(new AftersaleCommand(
                    "11111111-1111-1111-1111-111111111111", null,
                    List.of(new AftersaleCommandLine(
                            "SL-1", RedemptionKind.RETURN, 1, false, false,
                            AftersaleReasonKind.OTHER, "其他原因", null, 0, false))));
            ChangeSet changes = new AftersaleChangeSetAssembler().assemble(
                    prepared, "AF-1", submitOp,
                    new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK), request);

            ChangeSet withProduct = new ChangeSet.Builder()
                    .merge(changes)
                    .add(RecordData.of("PRODUCT", "6901234567890",
                            new Product("6901234567890", "商品", "件", new BigDecimal("8.00"), null)))
                    .build();
            Result<SubmissionPort.CommitReceipt> submitted = app.store().submit(
                    submitOp, request, opened.value(), withProduct);

            assertEquals(submitted.code() + " / " + submitted.detail(), Result.Status.SUCCESS, submitted.status());
            Result<SubmissionPort.CommitReceipt> replay = app.store().lookupResult(submitOp, request);
            assertEquals(Result.Status.SUCCESS, replay.status());
            assertTrue(replay.value().replayed());
        }
    }

    private Redemption redemption() {
        return new Redemption(
                "R-1", "SL-1", "SL-1", "6901234567890", "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "11111111-1111-1111-1111-111111111111", null, false, false, "其他原因");
    }
}


