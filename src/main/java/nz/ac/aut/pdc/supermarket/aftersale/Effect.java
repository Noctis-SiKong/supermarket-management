package nz.ac.aut.pdc.supermarket.aftersale;

/** Redemption effect: CONSUME for normal aftersale or RELEASE for authorized reversal.
 *
 * 核销账务方向：占用（正常售后）或释放（授权冲销）。 */
public enum Effect {
    CONSUME,
    RELEASE
}
