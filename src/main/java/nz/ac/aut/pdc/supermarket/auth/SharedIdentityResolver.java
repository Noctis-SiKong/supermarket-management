package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;

import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;

/** Adapter from B authentication to A IF-01 IdentityResolver.
 *
 * B 认证 → A IF-01 IdentityResolver 适配器。 */
public final class SharedIdentityResolver implements OperationContext.IdentityResolver {

    private final AuthService authService;

    public SharedIdentityResolver(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    @Override
    public Result<OperationContext.ActorContext> resolve(Object sessionRef) {
        if (!(sessionRef instanceof SessionRef session)) {
            return Result.rejected("AUTH_REQUIRED", "B-issued session reference required");
        }
        AuthResult result = authService.resolve(session);
        if (result instanceof AuthResult.Success success) {
            return Result.value(new OperationContext.ActorContext(
                    success.accountId(), SharedRoles.toShared(success.role())));
        }
        if (result instanceof AuthResult.Rejected rejected) {
            return Result.rejected(rejected.code(), rejected.message());
        }
        AuthResult.Failed failed = (AuthResult.Failed) result;
        return Result.failed(failed.code(), failed.message());
    }
}
