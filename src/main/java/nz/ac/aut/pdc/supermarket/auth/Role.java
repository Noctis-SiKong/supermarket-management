package nz.ac.aut.pdc.supermarket.auth;

/** Spec section 5 ADMIN/MANAGER/CLERK permission roles do not prescribe headcount.
 */
public enum Role {
    ADMIN,
    MANAGER,
    CLERK;

    /** Manager/admin authorize reversal; CR07-10 withdraws aftersale eligibility exceptions.
  */
    public boolean canPerformHighRiskOperation() {
        return this == MANAGER || this == ADMIN;
    }
}
