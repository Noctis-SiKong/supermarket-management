package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;

import nz.ac.aut.pdc.supermarket.auth.Role;

/** CUI 会话中的可信身份快照（来自 AuthResult.Success）。 */
public record CuiActor(String accountId, Role role) {

    public CuiActor {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(role, "role");
    }
}
