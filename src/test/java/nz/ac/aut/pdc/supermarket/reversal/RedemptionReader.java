package nz.ac.aut.pdc.supermarket.reversal;

import java.util.Optional;

import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** Read-only redemption port in the historical test prototype (IF-11/IF-04 adapter).
 *
 * 核销只读端口（IF-11/IF-04 的 B 侧适配，当前用替身）。 */
public interface RedemptionReader {

    Optional<Redemption> find(String redemptionId);

    /** Find an effective RELEASE referencing this redemption; an existing release prevents another reversal.
 *
 * 查找指向该核销的有效 RELEASE 冲销事实；存在即不可再次冲销。 */
    Optional<Redemption> findReversalOf(String redemptionId);
}
