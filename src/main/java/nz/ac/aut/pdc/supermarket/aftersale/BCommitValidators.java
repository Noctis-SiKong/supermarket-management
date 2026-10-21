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
                nz.ac.aut.pdc.supermarket.stock.LossWorkflow.DISPOSE,new nz.ac.aut.pdc.supermarket.stock.LossWorkflow(),
                nz.ac.aut.pdc.supermarket.stock.LossWorkflow.CORRECT,new nz.ac.aut.pdc.supermarket.stock.LossWorkflow(),
                AftersaleChangeSetAssembler.OPERATION,
                new AftersaleCommitValidator(stockAdapter),
                DraftDeletion.OPERATION,
                new AftersaleDraftDeletionValidator(),
                AftersaleDraftOperations.CREATE,
                new AftersaleDraftValidator(AftersaleDraftOperations.CREATE),
                AftersaleDraftOperations.CANCEL,
                new AftersaleDraftValidator(AftersaleDraftOperations.CANCEL),
                nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.REPLENISHMENT,
                new nz.ac.aut.pdc.supermarket.stock.StockOperationValidator(
                        nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.REPLENISHMENT),
                nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.LOSS,
                new nz.ac.aut.pdc.supermarket.stock.StockOperationValidator(
                        nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.LOSS),
                nz.ac.aut.pdc.supermarket.reversal.ReversalChangeSetAssembler.OPERATION,
                new nz.ac.aut.pdc.supermarket.reversal.ReversalCommitValidator());
    }
}
