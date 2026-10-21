package nz.ac.aut.pdc.supermarket.cui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;

import static org.junit.Assert.assertEquals;

public class BApplicationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void realJsonStoreAcceptsBServiceSession() throws Exception {
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
            Result<WorkingCopy> opened = app.store().open(new OperationContext(
                    login.session(), "REQ-OPEN", "READ", null));

            assertEquals(Result.Status.VALUE, opened.status());
        }
    }
}
