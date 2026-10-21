package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.DraftDeletion;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;

/**
 * 售后草稿生命周期：建 DRAFT → IF-11 读取追溯 → 取消。
 *
 * <p>每次操作独立 open/submit；追溯保存成功后才返回原销售最少字段。
 * 取消只改草稿状态并写事件，不产生库存/退款/额度影响。</p>
 */
public final class AftersaleDraftService {

    private final JsonStore store;

    public AftersaleDraftService(JsonStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public Result<SubmissionPort.CommitReceipt> create(
            OperationContext operation, OperationContext.ActorContext actor) {
        Result<WorkingCopy> opened = store.open(operation);
        if (!opened.hasValue()) {
            return opened.failure();
        }
        WorkingCopy workingCopy = opened.value();
        String draftId = operation.targetId();
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.add(RecordData.of("DOCUMENT", draftId, new DocumentHeader(
                draftId, "AFTERSALE", "DRAFT", operation.requestKey(),
                JsonCodec.fingerprint(operation.operation(), actor.accountId(),
                        BusinessRequest.of(Map.of("draftId", draftId))),
                actor.accountId(), null, null, null, null)));
        out.add(RecordData.of("AFTERSALE", draftId, Map.of()));
        out.add(RecordData.of("DOC_EVENT", draftId + "-CREATE", new DocEvent(
                draftId + "-CREATE", draftId, 1, "CREATE", actor.accountId(), null, "{}")));
        return store.submit(operation,
                BusinessRequest.of(Map.of("draftId", draftId)),
                workingCopy, out.build());
    }

    public Result<SubmissionPort.CommitReceipt> recordRead(
            OperationContext operation,
            OperationContext.ActorContext actor,
            OriginalSaleLine original) {
        var actual=new OperationContext(operation.sessionRef(),operation.requestKey(),nz.ac.aut.pdc.shared.AftersaleReadRequest.OPERATION,operation.targetId());
        var access=new nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess(
            session -> store.authenticate(new OperationContext(session,actual.requestKey(),actual.operation(),actual.targetId())),
            new nz.ac.aut.pdc.shared.IdClock(java.time.Clock.systemUTC()),new AftersaleDraftAccess());
        var result=access.readForAftersale(store,actual,new nz.ac.aut.pdc.shared.AftersaleReadRequest(original.saleLineId(),actual.targetId(),java.util.List.of(original.saleLineId())));
        return result.hasValue()?new Result<>(Result.Status.SUCCESS,result.value().trace(),null,null,null,null):result.failure();
    }

    public Result<SubmissionPort.CommitReceipt> cancel(
            OperationContext operation, OperationContext.ActorContext actor) {
        String draftId = operation.targetId();
        BusinessRequest request = BusinessRequest.of(Map.of("draftId", draftId));
        Result<SubmissionPort.CommitReceipt> prior = store.lookupResult(operation, request);
        if (prior.status() != Result.Status.NOT_FOUND) {
            return prior;
        }
        Result<WorkingCopy> opened = store.open(operation);
        if (!opened.hasValue()) {
            return opened.failure();
        }
        WorkingCopy workingCopy = opened.value();
        RecordData document = workingCopy.find("DOCUMENT", draftId).orElse(null);
        if (document == null || !"DRAFT".equals(document.as(DocumentHeader.class).status())) {
            return Result.rejected("INVALID_DRAFT", "Aftersale draft not found or not DRAFT");
        }
        boolean hasReadTrace = workingCopy.records("DOC_EVENT").stream()
                .anyMatch(r -> r.as(DocEvent.class).documentId().equals(draftId)
                        && "READ_FOR_AFTERSALE".equals(r.as(DocEvent.class).action()));
        if (!hasReadTrace) {
            return Result.rejected("DRAFT_HAS_NO_READ_TRACE",
                    "Only read-traced aftersale drafts may be cancelled");
        }
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.patch(new ChangeSet.Patch("DOCUMENT", draftId, document.version(),
                JsonCodec.write(Map.of("status", "CANCELLED"))));
        out.add(RecordData.of("DOC_EVENT", draftId + "-CANCEL", new DocEvent(
                draftId + "-CANCEL", draftId, nextEventVersion(workingCopy, draftId),
                "CANCEL_AFTERSALE", actor.accountId(), null, "{}")));
        return store.submit(operation, request, workingCopy, out.build());
    }

    public Result<SubmissionPort.CommitReceipt> delete(
            OperationContext operation, OperationContext.ActorContext actor) {
        String draftId = operation.targetId();
        BusinessRequest request = BusinessRequest.of(new DraftDeletion(draftId));
        var prior = store.lookupResult(operation, request);
        if (prior.status()!=Result.Status.NOT_FOUND) return prior;
        Result<WorkingCopy> opened=store.open(operation);
        if(!opened.hasValue()) return opened.failure();
        WorkingCopy workingCopy=opened.value();
        RecordData document=workingCopy.find("DOCUMENT",draftId).orElse(null);
        if(document==null || !"DRAFT".equals(document.as(DocumentHeader.class).status()))
            return Result.rejected("INVALID_DRAFT","Aftersale draft missing or closed");
        ChangeSet.Builder out = new ChangeSet.Builder();
        out.remove(new ChangeSet.Removal("DOCUMENT", draftId, document.version()));
        addRemoval(out, workingCopy, "AFTERSALE", draftId);
        for (RecordData line : workingCopy.records("DOC_LINE")) {
            if (draftId.equals(line.as(DocumentLine.class).documentId())) {
                out.remove(new ChangeSet.Removal("DOC_LINE", line.id(), line.version()));
            }
        }
        for (RecordData event : workingCopy.records("DOC_EVENT")) {
            if (draftId.equals(event.as(DocEvent.class).documentId())) {
                out.remove(new ChangeSet.Removal("DOC_EVENT", event.id(), event.version()));
            }
        }
        for (RecordData replacement : workingCopy.records("REPLACEMENT")) {
            if (draftId.equals(replacement.as(Replacement.class).aftersaleId())) {
                out.remove(new ChangeSet.Removal("REPLACEMENT", replacement.id(), replacement.version()));
            }
        }
        return store.submit(operation, request, workingCopy, out.build());
    }

    private void addRemoval(ChangeSet.Builder out, WorkingCopy workingCopy, String table, String id) {
        workingCopy.find(table, id).ifPresent(record ->
                out.remove(new ChangeSet.Removal(table, id, record.version())));
    }

    private long nextEventVersion(WorkingCopy workingCopy, String draftId) {
        return workingCopy.records("DOC_EVENT").stream()
                .filter(r -> r.as(DocEvent.class).documentId().equals(draftId))
                .mapToLong(r -> r.as(DocEvent.class).version())
                .max().orElse(0) + 1;
    }
}



