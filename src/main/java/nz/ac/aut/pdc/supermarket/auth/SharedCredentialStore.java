package nz.ac.aut.pdc.supermarket.auth;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.OperationContext;

/** Read-only adapter from A authentication credentials view to B CredentialStore.
  */
public final class SharedCredentialStore implements CredentialStore {

    private final Supplier<List<JsonStore.Credential>> authenticationView;

    public SharedCredentialStore(JsonStore store) {
        this(Objects.requireNonNull(store, "store")::authenticationView);
    }

    public SharedCredentialStore(Supplier<List<JsonStore.Credential>> authenticationView) {
        this.authenticationView = Objects.requireNonNull(authenticationView, "authenticationView");
    }

    @Override
    public Optional<StoredCredential> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return authenticationView.get().stream()
                .filter(credential -> username.equals(credential.username()))
                .findFirst()
                .map(credential -> new StoredCredential(
                        credential.accountId(),
                        credential.username(),
                        SharedRoles.toLocal(credential.role()),
                        credential.passwordHash()));
    }
}



