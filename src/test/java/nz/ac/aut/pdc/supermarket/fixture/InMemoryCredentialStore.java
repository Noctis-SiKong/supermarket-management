package nz.ac.aut.pdc.supermarket.fixture;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.auth.CredentialStore;

/** Read-only in-memory credential test double replacing the authentication storage view; returns hashes, never plaintext or real data.
 */
public final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, StoredCredential> credentials = new HashMap<>();

    public void add(StoredCredential credential) {
        credentials.put(credential.username(), credential);
    }

    @Override
    public Optional<StoredCredential> findByUsername(String username) {
        return Optional.ofNullable(credentials.get(username));
    }
}
