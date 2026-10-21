package nz.ac.aut.pdc.supermarket.cui;

import nz.ac.aut.pdc.supermarket.stock.Position;

/** CUI operation port implemented by SharedOperations with real shared IF-04/05; never exposes WorkingCopy to the CUI.
 */
public interface Operations {

    OperationFeedback replenish(CuiActor actor, String barcode, int quantity);

    OperationFeedback recordLoss(CuiActor actor, String barcode, Position position, int quantity);

    default OperationFeedback recordLoss(CuiActor actor,String barcode,Position position,int quantity,String reason) { return recordLoss(actor,barcode,position,quantity); }

    OperationFeedback aftersale(CuiActor actor, AftersaleRequest request);

    OperationFeedback cancelAftersaleDraft(CuiActor actor, String draftId);

    OperationFeedback deleteAftersaleDraft(CuiActor actor, String draftId);

    OperationFeedback reverseDocument(CuiActor actor, String targetDocumentId, String reason);
}
