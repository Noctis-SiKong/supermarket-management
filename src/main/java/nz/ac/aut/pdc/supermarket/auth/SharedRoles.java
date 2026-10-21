package nz.ac.aut.pdc.supermarket.auth;

import nz.ac.aut.pdc.shared.OperationContext;

/** B 角色与 A 共享角色枚举的唯一映射点。 */
public final class SharedRoles {

    private SharedRoles() {
    }

    public static OperationContext.Role toShared(Role role) {
        return switch (role) {
            case ADMIN -> OperationContext.Role.ADMIN;
            case MANAGER -> OperationContext.Role.MANAGER;
            case CLERK -> OperationContext.Role.CLERK;
        };
    }

    public static Role toLocal(OperationContext.Role role) {
        return switch (role) {
            case ADMIN -> Role.ADMIN;
            case MANAGER -> Role.MANAGER;
            case CLERK -> Role.CLERK;
        };
    }
}
