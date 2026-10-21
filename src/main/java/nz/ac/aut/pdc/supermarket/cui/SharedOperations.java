package nz.ac.aut.pdc.supermarket.cui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleChangeSetAssembler;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommand;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleCommandLine;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleDraftOperations;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleDraftService;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleService;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleSubmissionService;
import nz.ac.aut.pdc.supermarket.aftersale.OriginalSaleLine;
import nz.ac.aut.pdc.supermarket.aftersale.QualityPairValidator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaCalculator;
import nz.ac.aut.pdc.supermarket.aftersale.QuotaViewBuilder;
import nz.ac.aut.pdc.supermarket.aftersale.SharedProductSnapshotReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedQuotaLedgerReader;
import nz.ac.aut.pdc.supermarket.aftersale.SharedSaleReader;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.auth.SharedRoles;
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

    public SharedOperations(JsonStore store, IdClock idClock) {
        this.store = Objects.requireNonNull(store, "store");
        this.idClock = Objects.requireNonNull(idClock, "idClock");
    }

    @Override
    public OperationFeedback replenish(CuiActor actor, String barcode, int quantity) {
        String requestKey = idClock.newRequestKey();
        String documentId = idClock.entityId(requestKey, "document");
        OperationContext operation = new OperationContext(
                actor.session(), requestKey,
                nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.REPLENISHMENT, documentId);
        Result<WorkingCopy> opened = store.open(operation);
        if (!opened.hasValue()) {
            return OperationFeedback.fail("打开工作副本失败：" + opened.code() + " " + opened.detail());
        }
        WorkingCopy workingCopy = opened.value();
        SharedIdGenerator ids = new SharedIdGenerator(idClock, requestKey);
        StockService stock = new StockService(new SharedProductCatalog(workingCopy), ids, new StockValidator());
        var result = new nz.ac.aut.pdc.supermarket.stock.ReplenishmentService(stock, ids)
                .replenish(new nz.ac.aut.pdc.supermarket.stock.SharedWorkingCopyView(workingCopy),
                        List.of(), barcode, quantity);
        if (!(result instanceof nz.ac.aut.pdc.supermarket.stock.ReplenishmentResult.Prepared prepared)) {
            if (result instanceof nz.ac.aut.pdc.supermarket.stock.ReplenishmentResult.Rejected rejected) {
                return OperationFeedback.fail("补货被拒绝：" + rejected.code() + " " + rejected.message());
            }
            var failed = (nz.ac.aut.pdc.supermarket.stock.ReplenishmentResult.Failed) result;
            return OperationFeedback.fail("补货失败：" + failed.code() + " " + failed.message());
        }
        String lineId = ids.nextId("LINE");
        OperationContext.ActorContext sharedActor =
                new OperationContext.ActorContext(actor.accountId(), SharedRoles.toShared(actor.role()));
        BusinessRequest request = BusinessRequest.of(Map.of("barcode", barcode, "quantity", quantity));
        var changes = new nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler()
                .assembleReplenishment(prepared, documentId, lineId, operation, sharedActor, request);
        Result<SubmissionPort.CommitReceipt> submitted = store.submit(
                operation, request, workingCopy, changes);
        if (!submitted.hasValue()) {
            return OperationFeedback.fail("补货未提交：" + submitted.code() + " " + submitted.detail());
        }
        return OperationFeedback.ok("补货已提交：" + submitted.value().requestKey());
    }

    @Override
    public OperationFeedback recordLoss(CuiActor actor, String barcode, Position position, int quantity) {
        String requestKey = idClock.newRequestKey();
        String documentId = idClock.entityId(requestKey, "document");
        OperationContext operation = new OperationContext(
                actor.session(), requestKey,
                nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler.LOSS, documentId);
        Result<WorkingCopy> opened = store.open(operation);
        if (!opened.hasValue()) {
            return OperationFeedback.fail("打开工作副本失败：" + opened.code() + " " + opened.detail());
        }
        WorkingCopy workingCopy = opened.value();
        SharedIdGenerator ids = new SharedIdGenerator(idClock, requestKey);
        StockService stock = new StockService(new SharedProductCatalog(workingCopy), ids, new StockValidator());
        var result = new nz.ac.aut.pdc.supermarket.stock.LossService(stock, ids)
                .recordLoss(new nz.ac.aut.pdc.supermarket.stock.SharedWorkingCopyView(workingCopy),
                        List.of(), barcode, position, quantity);
        if (!(result instanceof nz.ac.aut.pdc.supermarket.stock.LossResult.Prepared prepared)) {
            if (result instanceof nz.ac.aut.pdc.supermarket.stock.LossResult.Rejected rejected) {
                return OperationFeedback.fail("报损被拒绝：" + rejected.code() + " " + rejected.message());
            }
            var failed = (nz.ac.aut.pdc.supermarket.stock.LossResult.Failed) result;
            return OperationFeedback.fail("报损失败：" + failed.code() + " " + failed.message());
        }
        String lineId = ids.nextId("LINE");
        OperationContext.ActorContext sharedActor =
                new OperationContext.ActorContext(actor.accountId(), SharedRoles.toShared(actor.role()));
        BusinessRequest request = BusinessRequest.of(Map.of(
                "barcode", barcode, "position", position.name(), "quantity", quantity));
        var changes = new nz.ac.aut.pdc.supermarket.stock.StockChangeSetAssembler()
                .assembleLoss(prepared, documentId, lineId, operation, sharedActor, request);
        Result<SubmissionPort.CommitReceipt> submitted = store.submit(
                operation, request, workingCopy, changes);
        if (!submitted.hasValue()) {
            return OperationFeedback.fail("报损未提交：" + submitted.code() + " " + submitted.detail());
        }
        return OperationFeedback.ok("报损已提交：" + submitted.value().requestKey());
    }

    @Override
    public OperationFeedback aftersale(CuiActor actor, AftersaleRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        String finalKey = request.requestKey() == null || request.requestKey().isBlank()
                ? idClock.newRequestKey() : request.requestKey();
        String draftKey = derivedKey(finalKey, "draft");
        String readKey = derivedKey(finalKey, "read");
        String aftersaleId = idClock.entityId(finalKey, "aftersale");
        OperationContext.ActorContext sharedActor =
                new OperationContext.ActorContext(actor.accountId(), SharedRoles.toShared(actor.role()));
        AftersaleDraftService drafts = new AftersaleDraftService(store);
        OperationContext operation = new OperationContext(
                actor.session(), finalKey, AftersaleChangeSetAssembler.OPERATION, aftersaleId);
        AftersaleCommand command = new AftersaleCommand(
                finalKey,
                null,
                List.of(new AftersaleCommandLine(
                        request.saleLineId(), request.kind(), request.quantity(),
                        request.opened(), request.qualityProblem(), request.reasonKind(),
                        request.reasonText(), request.replacementBarcode(),
                        request.replacementQuantity(), false)));
        BusinessRequest finalRequest = BusinessRequest.of(command);
        Result<SubmissionPort.CommitReceipt> prior = store.lookupResult(operation, finalRequest);
        if (prior.status() != Result.Status.NOT_FOUND) {
            if (prior.hasValue()) {
                return OperationFeedback.ok("售后已提交：" + prior.value().requestKey());
            }
            return OperationFeedback.fail("售后未提交：" + prior.code() + " " + prior.detail());
        }

        OperationContext createOp = new OperationContext(
                actor.session(), draftKey, AftersaleDraftOperations.CREATE, aftersaleId);
        Result<SubmissionPort.CommitReceipt> created = drafts.create(createOp, sharedActor);
        if (!created.hasValue()) {
            return OperationFeedback.fail("售后草稿创建失败：" + created.code() + " " + created.detail());
        }

        OperationContext readOp = new OperationContext(
                actor.session(), readKey, AftersaleDraftOperations.READ, aftersaleId);
        Result<WorkingCopy> draftCopy = store.open(readOp);
        if (!draftCopy.hasValue()) {
            return OperationFeedback.fail("读取售后草稿失败：" + draftCopy.code() + " " + draftCopy.detail());
        }
        OriginalSaleLine original = new SharedSaleReader(draftCopy.value())
                .readLine(request.saleLineId()).orElse(null);
        if (original == null) {
            return OperationFeedback.fail("原销售行不存在：" + request.saleLineId());
        }
        Result<SubmissionPort.CommitReceipt> traced = drafts.recordRead(readOp, sharedActor, original);
        if (!traced.hasValue()) {
            return OperationFeedback.fail("IF-11 读取追溯保存失败：" + traced.code() + " " + traced.detail());
        }

        Result<WorkingCopy> openResult = store.open(operation);
        if (!openResult.hasValue()) {
            return OperationFeedback.fail("打开工作副本失败：" + openResult.code() + " " + openResult.detail());
        }
        WorkingCopy workingCopy = openResult.value();
        SharedIdGenerator ids = new SharedIdGenerator(idClock, finalKey);
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
        AftersaleSubmissionService submission = new AftersaleSubmissionService(
                aftersaleService, new AftersaleChangeSetAssembler(), store);
        Result<SubmissionPort.CommitReceipt> result = submission.submit(
                command, workingCopy, operation, sharedActor);
        if (!result.hasValue()) {
            return OperationFeedback.fail("售后未提交：" + result.code() + " " + result.detail());
        }
        return OperationFeedback.ok("售后已提交：" + result.value().requestKey());
    }

    @Override
    public OperationFeedback cancelAftersaleDraft(CuiActor actor, String draftId) {
        Objects.requireNonNull(actor, "actor");
        if (draftId == null || draftId.isBlank()) {
            return OperationFeedback.fail("售后草稿 ID 不能为空");
        }
        OperationContext operation = new OperationContext(
                actor.session(), derivedKey(draftId.trim(), "cancel"),
                AftersaleDraftOperations.CANCEL, draftId.trim());
        Result<SubmissionPort.CommitReceipt> result = new AftersaleDraftService(store).cancel(
                operation,
                new OperationContext.ActorContext(
                        actor.accountId(), SharedRoles.toShared(actor.role())));
        if (!result.hasValue()) {
            return OperationFeedback.fail("取消失败：" + result.code() + " " + result.detail());
        }
        return OperationFeedback.ok("售后草稿已取消：" + draftId.trim());
    }

    @Override
    public OperationFeedback deleteAftersaleDraft(CuiActor actor, String draftId) {
        Objects.requireNonNull(actor, "actor");
        if (draftId == null || draftId.isBlank()) {
            return OperationFeedback.fail("售后草稿 ID 不能为空");
        }
        OperationContext operation = new OperationContext(
                actor.session(), derivedKey(draftId.trim(), "delete"),
                nz.ac.aut.pdc.shared.DraftDeletion.OPERATION, draftId.trim());
        Result<SubmissionPort.CommitReceipt> result = new AftersaleDraftService(store).delete(
                operation,
                new OperationContext.ActorContext(
                        actor.accountId(), SharedRoles.toShared(actor.role())));
        if (!result.hasValue()) {
            return OperationFeedback.fail("删除失败：" + result.code() + " " + result.detail());
        }
        return OperationFeedback.ok("售后草稿已删除：" + draftId.trim());
    }

    private String derivedKey(String baseKey, String purpose) {
        return java.util.UUID.nameUUIDFromBytes(
                (baseKey + ":" + purpose).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
