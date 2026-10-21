package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;

/** Historical read-only D1 dependency port used with test doubles.
  */
public interface ReversalDependencyReader {

    /** Return downstream record IDs referencing this redemption; nonempty results block direct reversal.
  */
    List<String> dependentRecordIds(String redemptionId);
}
