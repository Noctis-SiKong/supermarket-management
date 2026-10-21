package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;
import java.util.Optional;

/** Read-only credential port using A authentication storage view; historical tests use doubles, not another JSON saver. Returns hashes only.
 */
public interface CredentialStore {

    Optional<StoredCredential> findByUsername(String username);

    /** One immutable stored account credential.
  */
    record StoredCredential(String accountId, String username, Role role, String passwordHash) {
        public StoredCredential {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(passwordHash, "passwordHash");
        }
    }
}
