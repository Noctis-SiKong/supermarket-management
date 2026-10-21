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

    /** 店长/管理员用于冲销等高风险操作；CR07-10 已撤销售后资格例外。 */
    public boolean canPerformHighRiskOperation() {
        return this == MANAGER || this == ADMIN;
    }
}
