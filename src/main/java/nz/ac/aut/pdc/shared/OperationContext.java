package nz.ac.aut.pdc.shared;

import java.util.Objects;

/** sessionRef is an opaque B-issued object, never an account/role assertion. */
public record OperationContext(Object sessionRef, String requestKey, String operation, String targetId) {
    public OperationContext { Objects.requireNonNull(operation); }
    public record ActorContext(String accountId, Role role) {
        public ActorContext { Objects.requireNonNull(accountId); Objects.requireNonNull(role); }
    }
    public enum Role { ADMIN, MANAGER, CLERK }
    /** Adapter is supplied by B; there is deliberately no production authentication here. */
    @FunctionalInterface public interface IdentityResolver {
        Result<ActorContext> resolve(Object sessionRef);
    }
}
