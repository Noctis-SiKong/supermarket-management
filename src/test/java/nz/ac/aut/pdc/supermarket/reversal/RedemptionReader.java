package nz.ac.aut.pdc.supermarket.reversal;

import java.util.Optional;

import nz.ac.aut.pdc.supermarket.aftersale.Redemption;

/** Read-only redemption port in the historical test prototype (IF-11/IF-04 adapter).
  */
public interface RedemptionReader {

    Optional<Redemption> find(String redemptionId);

    /** Find an effective RELEASE referencing this redemption; an existing release prevents another reversal.
  */
    Optional<Redemption> findReversalOf(String redemptionId);
}
