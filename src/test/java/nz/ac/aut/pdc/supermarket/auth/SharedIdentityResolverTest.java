package nz.ac.aut.pdc.supermarket.auth;

import org.junit.Test;

import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.supermarket.fixture.InMemoryCredentialStore;

import static org.junit.Assert.assertEquals;

public class SharedIdentityResolverTest {

    @Test
    public void resolvesBServiceSessionToSharedActor() {
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        store.add(new CredentialStore.StoredCredential(
                "acc-1", "alice", Role.MANAGER, hasher.hash("secret".toCharArray())));
        LocalAuthService auth = new LocalAuthService(store, hasher);
        AuthResult.Success success = (AuthResult.Success) auth.login("alice", "secret".toCharArray());

        Result<OperationContext.ActorContext> result =
                new SharedIdentityResolver(auth).resolve(success.session());

        assertEquals(Result.Status.VALUE, result.status());
        assertEquals("acc-1", result.value().accountId());
        assertEquals(OperationContext.Role.MANAGER, result.value().role());
    }

    @Test
    public void rejectsForeignSessionObject() {
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        LocalAuthService auth = new LocalAuthService(store, hasher);

        Result<OperationContext.ActorContext> result =
                new SharedIdentityResolver(auth).resolve("string-session");

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("AUTH_REQUIRED", result.code());
    }
}
