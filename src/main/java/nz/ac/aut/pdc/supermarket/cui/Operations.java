package nz.ac.aut.pdc.supermarket.cui;

import nz.ac.aut.pdc.supermarket.stock.Position;

/**
 * CUI 业务菜单的操作端口。
 *
 * <p>CUI 只依赖该接口，不直接依赖具体服务；正式接线由 A 的 shared/IF-04/05
 * 交付后实现适配器。未接真实实现时使用 {@link UnavailableOperations}。</p>
 */
public interface Operations {

    OperationFeedback replenish(CuiActor actor, String barcode, int quantity);

    OperationFeedback recordLoss(CuiActor actor, String barcode, Position position, int quantity);

    OperationFeedback aftersale(CuiActor actor, AftersaleRequest request);
}
