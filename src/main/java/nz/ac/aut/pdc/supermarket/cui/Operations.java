package nz.ac.aut.pdc.supermarket.cui;

import nz.ac.aut.pdc.supermarket.stock.Position;

/**
 * CUI 业务菜单的操作端口。
 *
 * <p>CUI 只依赖该接口，不直接依赖具体服务；由 SharedOperations 接真实 shared/IF-04/05；不把 WorkingCopy 暴露给 CUI。</p>
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
