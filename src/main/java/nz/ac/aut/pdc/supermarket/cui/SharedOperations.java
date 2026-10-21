package nz.ac.aut.pdc.supermarket.cui;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleChangeSetAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommand;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommandLine;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleService;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleSubmissionService;
import nz.ac.aut.pdc.supermarket.aftersale.QualityPairValidator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaCalculator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.aftersale.SharedProductSnapshotReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedQuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedSaleReader;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

/**
 * CUI 真实操作适配器：售后走 A JsonStore 的真实 open/submit；
 * 补货/报损尚无真实 ChangeSet 组装，明确返回未接线，不伪造成功。
 */
public final class SharedOperations implements Operations {

    private final JsonStore store;
    private final IdClock idClock;

    public SharedOperations(JsonStore store) {
        this(store, new IdClock(Clock.system(IdClock.BUSINESS_ZONE)));
    }

    public SharedOperations(JsonStore store, IdClock idClock) {
        this.store = Objects.requireNonNull(store, "store");
        this.idClock = Objects.requireNonNull(idClock, "idClock");
    }

    @Override
    public OperationFeedback replenish(CuiActor actor, String barcode, int quantity) {
        return OperationFeedback.fail("补货真实 ChangeSet 尚未接线；本次未提交。");
    }

    @Override
    public OperationFeedback recordLoss(CuiActor actor, String barcode, Position position, int quantity) {
        return OperationFeedback.fail("报损真实 ChangeSet 尚未接线；本次未提交。");
    }

    @Override
    public OperationFeedback aftersale(
            CuiActor actor,
            String saleLineId,
            RedemptionKind kind,
            int quantity,
            boolean opened,
            boolean qualityProblem,
            AftersaleReasonKind reasonKind,
            String reasonText) {
        String requestKey = idClock.newRequestKey();
        String aftersaleId = idClock.entityId(requestKey, "aftersale");
        OperationContext operation = new OperationContext(
                actor.session(), requestKey, AftersaleChangeSetAssembler.OPERATION, aftersaleId);
        Result<WorkingCopy> openResult = store.open(operation);
        if (!openResult.hasValue()) {
            return OperationFeedback.fail("打开工作副本失败：" + openResult.code() + " " + openResult.detail());
        }
        WorkingCopy workingCopy = openResult.value();
        SharedIdGenerator ids = new SharedIdGenerator(idClock, requestKey);
        AftersaleService aftersaleService = new AftersaleService(
                new SharedSaleReader(workingCopy),
                new SharedQuotaLedgerReader(workingCopy),
                new SharedProductSnapshotReader(workingCopy),
                new StockService(new SharedProductCatalog(workingCopy), ids, new StockValidator()),
                new QuotaViewBuilder(),
                new QuotaCalculator(),
                new QualityPairValidator(),
                new AftersaleAssembler(ids),
                ids);
        AftersaleCommand command = new AftersaleCommand(
                requestKey,
                null,
                List.of(new AftersaleCommandLine(
                        saleLineId, kind, quantity, opened, qualityProblem,
                        reasonKind, reasonText, null, 0, false)));
        AftersaleSubmissionService submission = new AftersaleSubmissionService(
                aftersaleService, new AftersaleChangeSetAssembler(), store);
        Result<SubmissionPort.CommitReceipt> result = submission.submit(
                command, workingCopy, operation,
                new OperationContext.ActorContext(actor.accountId(), toSharedRole(actor.role())));
        if (!result.hasValue()) {
            return OperationFeedback.fail("售后未提交：" + result.code() + " " + result.detail());
        }
        return OperationFeedback.ok("售后已提交：" + result.value().requestKey());
    }

    private OperationContext.Role toSharedRole(Role role) {
        return switch (role) {
            case ADMIN -> OperationContext.Role.ADMIN;
            case MANAGER -> OperationContext.Role.MANAGER;
            case CLERK -> OperationContext.Role.CLERK;
        };
    }
}

