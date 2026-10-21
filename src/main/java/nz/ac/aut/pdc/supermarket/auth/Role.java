package nz.ac.aut.pdc.supermarket.auth;

/**
 * 系统角色，对应 Spec §5 的管理员/店长/店员三种权限身份。
 *
 * <p>角色只表示系统权限，不限定实际人数。</p>
 */
public enum Role {
    ADMIN,
    MANAGER,
    CLERK;

    /** 只有店长/管理员可以授权 CR07-09 的个案例外或作废类高风险操作。 */
    public boolean canAuthorizeException() {
        return this == MANAGER || this == ADMIN;
    }
}
