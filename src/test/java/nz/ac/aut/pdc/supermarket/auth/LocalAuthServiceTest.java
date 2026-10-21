package nz.ac.aut.pdc.supermarket.auth;

import org.junit.Before;
import org.junit.Test;

import nz.ac.aut.pdc.supermarket.fixture.InMemoryCredentialStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LocalAuthServiceTest {

    private InMemoryCredentialStore credentialStore;
    private LocalAuthService authService;

    @Before
    public void setUp() {
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        credentialStore = new InMemoryCredentialStore();
        credentialStore.add(new CredentialStore.StoredCredential(
                "acc-1", "alice", Role.CLERK, hasher.hash("secret".toCharArray())));
        authService = new LocalAuthService(credentialStore, hasher);
    }

    @Test
    public void loginWithValidCredentialsReturnsSessionAndRole() {
        AuthResult result = authService.login("alice", "secret".toCharArray());

        assertTrue(result instanceof AuthResult.Success);
        AuthResult.Success success = (AuthResult.Success) result;
        assertEquals("acc-1", success.accountId());
        assertEquals(Role.CLERK, success.role());
        assertEquals("SessionRef[redacted]", success.session().toString());
    }

    @Test
    public void loginWithWrongPasswordIsRejected() {
        AuthResult result = authService.login("alice", "wrong".toCharArray());

        assertTrue(result instanceof AuthResult.Rejected);
        assertEquals("INVALID_CREDENTIALS", ((AuthResult.Rejected) result).code());
    }

    @Test
    public void loginWithUnknownUserIsRejectedWithoutLeakingDetails() {
        AuthResult result = authService.login("nobody", "secret".toCharArray());

        assertTrue(result instanceof AuthResult.Rejected);
        assertEquals("用户名或密码不正确", ((AuthResult.Rejected) result).message());
    }

    @Test
    public void resolveReturnsSameAccountForIssuedSession() {
        AuthResult.Success login = (AuthResult.Success) authService.login("alice", "secret".toCharArray());

        AuthResult resolved = authService.resolve(login.session());

        assertTrue(resolved instanceof AuthResult.Success);
        AuthResult.Success success = (AuthResult.Success) resolved;
        assertEquals("acc-1", success.accountId());
        assertEquals(Role.CLERK, success.role());
        assertEquals(login.session(), success.session());
    }

    @Test
    public void resolveRejectsUnknownSession() {
        AuthResult resolved = authService.resolve(new SessionRef("not-issued"));

        assertTrue(resolved instanceof AuthResult.Rejected);
        assertEquals("AUTH_REQUIRED", ((AuthResult.Rejected) resolved).code());
    }

    @Test
    public void resolveRejectsNullSession() {
        AuthResult resolved = authService.resolve(null);

        assertTrue(resolved instanceof AuthResult.Rejected);
        assertEquals("AUTH_REQUIRED", ((AuthResult.Rejected) resolved).code());
    }

    @Test
    public void managerAndAdminCanAuthorizeExceptionsButClerkCannot() {
        assertTrue(Role.MANAGER.canAuthorizeException());
        assertTrue(Role.ADMIN.canAuthorizeException());
        assertTrue(!Role.CLERK.canAuthorizeException());
        assertNotEquals(Role.CLERK, Role.MANAGER);
        assertSame(Role.ADMIN, Role.valueOf("ADMIN"));
    }
}
