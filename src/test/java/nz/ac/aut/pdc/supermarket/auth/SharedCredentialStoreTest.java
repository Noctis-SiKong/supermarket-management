package nz.ac.aut.pdc.supermarket.auth;

import java.util.List;

import org.junit.Test;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.OperationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedCredentialStoreTest {

    @Test
    public void mapsAAuthenticationViewToBStoredCredential() {
        SharedCredentialStore store = new SharedCredentialStore(() -> List.of(
                new JsonStore.Credential("acc-1", "alice", "hash", OperationContext.Role.MANAGER)));

        CredentialStore.StoredCredential credential = store.findByUsername("alice").orElseThrow();

        assertEquals("acc-1", credential.accountId());
        assertEquals("alice", credential.username());
        assertEquals(Role.MANAGER, credential.role());
        assertEquals("hash", credential.passwordHash());
    }

    @Test
    public void unknownUsernameIsEmpty() {
        SharedCredentialStore store = new SharedCredentialStore(List::of);

        assertTrue(store.findByUsername("nobody").isEmpty());
    }
}
