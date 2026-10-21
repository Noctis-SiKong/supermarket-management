package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.cui.BApplication;

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
                new JsonStore.Credential("clerk", "clerk", hash, OperationContext.Role.CLERK));
        IdClock ids = new IdClock(Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneOffset.UTC));

        try (BApplication app = new BApplication(project, credentials, ids)) {
            AuthResult.Success login = (AuthResult.Success) app.authService()
                    .login("clerk", "secret".toCharArray());
            OperationContext.ActorContext actor = new OperationContext.ActorContext(
                    "clerk", OperationContext.Role.CLERK);
            AftersaleDraftService drafts = new AftersaleDraftService(app.store());
            String draftKey = ids.newRequestKey();
            String draftId = ids.entityId(draftKey, "draft");

            Result<?> created = drafts.create(new OperationContext(
                    login.session(), draftKey, AftersaleDraftOperations.CREATE, draftId), actor);
            assertEquals(created.code(), Result.Status.SUCCESS, created.status());

            Result<?> traced = drafts.recordRead(new OperationContext(
                    login.session(), ids.newRequestKey(), AftersaleDraftOperations.READ, draftId),
                    actor, new OriginalSaleLine("SL-1", "6901234567890", 1,
                            new BigDecimal("3.33"), Instant.parse("2026-10-01T00:00:00Z")));
            assertEquals(traced.code(), Result.Status.SUCCESS, traced.status());

            Result<?> cancelled = drafts.cancel(new OperationContext(
                    login.session(), ids.newRequestKey(), AftersaleDraftOperations.CANCEL, draftId),
                    actor);
            assertEquals(cancelled.code(), Result.Status.SUCCESS, cancelled.status());

            WorkingCopy state = app.store().open(new OperationContext(
                    login.session(), ids.newRequestKey(), "READ", null)).value();
            DocumentHeader header = state.find("DOCUMENT", draftId).orElseThrow()
                    .as(DocumentHeader.class);
            assertEquals("CANCELLED", header.status());
            assertTrue(state.records("DOC_EVENT").stream()
                    .anyMatch(r -> r.id().equals(draftId + "-READ")));
        }
    }
}
