package nz.ac.aut.pdc.supermarket.cui;

import java.util.List;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.infrastructure.AttachmentStore;
import nz.ac.aut.pdc.supermarket.app.AOperations.Item;

/** Complete CUI facade: business inputs and authorized projections, never WorkingCopy or hashes. */
public interface MaintenanceOperations extends Operations {
    record ReceiptEdit(String receiptId,List<Item> items,String reason) { public ReceiptEdit {items=List.copyOf(items);} }
    OperationFeedback quoteSale(CuiActor actor,List<Item> items,String requestKey);
    OperationFeedback quoteAftersale(CuiActor actor,List<AftersaleRequest> lines,String requestKey);
    OperationFeedback aftersaleBatch(CuiActor actor,List<AftersaleRequest> lines,String requestKey);
    OperationFeedback executeA(CuiActor actor,String operation,Object input,String requestKey);
    OperationFeedback inspect(CuiActor actor,String category,String id);
    OperationFeedback retry(CuiActor actor,String requestKey);
    Result<AttachmentStore.Attachment> attach(CuiActor actor,String source,String receiptId,int additional);
}
