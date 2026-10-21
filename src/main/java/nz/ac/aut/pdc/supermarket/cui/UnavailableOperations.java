package nz.ac.aut.pdc.supermarket.cui;

import nz.ac.aut.pdc.supermarket.stock.Position;

/**
 * 未接入真实 shared/IF-04/05 时的明确占位实现。
 *
 * <p>不伪造库存/提交结果，只返回"依赖未交付"的失败反馈；正式适配器
 * 接入后删除或替换。</p>
 */
public final class UnavailableOperations implements Operations {

    private static final String MESSAGE =
            "该操作依赖 A 的 shared/IF-04/05 或真实凭证/快照实现，当前尚未接入；未产生任何提交。";

    @Override
    public OperationFeedback replenish(CuiActor actor, String barcode, int quantity) {
        return OperationFeedback.fail(MESSAGE);
    }

    @Override
    public OperationFeedback recordLoss(CuiActor actor, String barcode, Position position, int quantity) {
        return OperationFeedback.fail(MESSAGE);
    }

    @Override
    public OperationFeedback aftersale(CuiActor actor, AftersaleRequest request) {
        return OperationFeedback.fail(MESSAGE);
    }

    @Override
    public OperationFeedback cancelAftersaleDraft(CuiActor actor, String draftId) {
        return OperationFeedback.fail(MESSAGE);
    }

    @Override
    public OperationFeedback deleteAftersaleDraft(CuiActor actor, String draftId) {
        return OperationFeedback.fail(MESSAGE);
    }
}
