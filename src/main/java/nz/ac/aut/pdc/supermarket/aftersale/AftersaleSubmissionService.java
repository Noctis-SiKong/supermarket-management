package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.auth.SharedRoles;
import nz.ac.aut.pdc.supermarket.stock.SharedWorkingCopyView;

/**
 * 售后提交入口：B 业务候选 → A 统一 ChangeSet → IF-05 提交。
 *
 * <p>先做 lookupResult 幂等查询再组装，符合 IF-05 单写者约定；不写文件、不嵌套 submit。</p>
 */
public final class AftersaleSubmissionService {

    private final AftersaleService aftersaleService;
    private final AftersaleChangeSetAssembler assembler;
    private final SubmissionPort store;

    public AftersaleSubmissionService(
            AftersaleService aftersaleService,
            AftersaleChangeSetAssembler assembler,
            SubmissionPort store) {
        this.aftersaleService = Objects.requireNonNull(aftersaleService, "aftersaleService");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.store = Objects.requireNonNull(store, "store");
    }

    public Result<SubmissionPort.CommitReceipt> submit(
            AftersaleCommand command,
            WorkingCopy workingCopy,
            OperationContext operation,
            OperationContext.ActorContext actor) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(workingCopy, "workingCopy");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actor, "actor");
        if (!command.requestKey().equals(operation.requestKey())) {
            return Result.rejected("INVALID_INPUT", "requestKey mismatch");
        }
        BusinessRequest request = BusinessRequest.of(command);
        Result<SubmissionPort.CommitReceipt> prior = store.lookupResult(operation, request);
        if (prior.status() != Result.Status.NOT_FOUND) {
            return prior;
        }
        if (operation.targetId() == null || operation.targetId().isBlank()) {
            return Result.rejected("INVALID_INPUT", "Aftersale target document id required");
        }

        AftersaleResult result = aftersaleService.apply(
                new SharedWorkingCopyView(workingCopy), command, SharedRoles.toLocal(actor.role()),
                operation.targetId());
        if (result instanceof AftersaleResult.Rejected rejected) {
            return Result.rejected(rejected.code(), rejected.message());
        }
        if (result instanceof AftersaleResult.Failed failed) {
            return Result.failed(failed.code(), failed.message());
        }
        AftersaleResult.Prepared prepared = (AftersaleResult.Prepared) result;
        if (prepared.redemptions().isEmpty()) {
            return Result.rejected("INVALID_INPUT", "Aftersale prepared no redemption");
        }
        ChangeSet changes = assembler.assemble(prepared, operation.targetId(), operation, actor, request);
        return store.submit(operation, request, workingCopy, changes);
    }
}
