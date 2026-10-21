package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Map;

import nz.ac.aut.pdc.shared.DraftDeletion;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

/** B 的提交校验器注册表；由装配层交给 A JsonStore。 */
public final class BCommitValidators {

    private BCommitValidators() {
    }

    public static Map<String, SubmissionPort.CommitValidator> create(
            SharedStockAdapter stockAdapter) {
        return Map.of(
                AftersaleChangeSetAssembler.OPERATION,
                new AftersaleCommitValidator(stockAdapter),
                DraftDeletion.OPERATION,
                new AftersaleDraftDeletionValidator(),
                AftersaleDraftOperations.CREATE,
                new AftersaleDraftValidator(AftersaleDraftOperations.CREATE),
                AftersaleDraftOperations.READ,
                new AftersaleDraftValidator(AftersaleDraftOperations.READ),
                AftersaleDraftOperations.CANCEL,
                new AftersaleDraftValidator(AftersaleDraftOperations.CANCEL));
    }
}
