package nz.ac.aut.pdc.supermarket.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nz.ac.aut.pdc.infrastructure.DataRoot;
import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.fixture.InMemoryCredentialStore;

import static org.junit.Assert.assertEquals;

public class SharedAuthJsonStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aJsonStoreAcceptsBServiceSession() throws Exception {
        Path project = temporaryFolder.newFolder("project").toPath();
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# test");
        Files.createDirectories(project.resolve("docs"));

        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        String hash = hasher.hash("secret".toCharArray());
        InMemoryCredentialStore credentials = new InMemoryCredentialStore();
        credentials.add(new CredentialStore.StoredCredential("acc-1", "alice", Role.CLERK, hash));
        LocalAuthService auth = new LocalAuthService(credentials, hasher);

        JsonStore store = new JsonStore(
                DataRoot.forProject(project),
                new SharedIdentityResolver(auth),
                Map.of(),
                state -> { });
        try {
            AuthResult.Success login = (AuthResult.Success) auth.login(
                    "alice", "secret".toCharArray());
            OperationContext op = new OperationContext(
                    login.session(), "REQ-1", "READ", null);

            Result<WorkingCopy> opened = store.open(op);

            assertEquals(Result.Status.VALUE, opened.status());
            assertEquals(0, opened.value().baseLast());
        } finally {
            store.close();
        }
    }
}

