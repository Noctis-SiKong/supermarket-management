package nz.ac.aut.pdc.supermarket.aftersale;

import java.time.*;
import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.infrastructure.SubmissionTime;
import nz.ac.aut.pdc.supermarket.auth.SharedRoles;
import nz.ac.aut.pdc.supermarket.stock.*;

/** Reconstruct the entire operation using the one eligibility/quota/settlement engine. */
public final class AftersaleCommitValidator implements SubmissionPort.CommitValidator {
    private final SharedStockAdapter stock;
    public AftersaleCommitValidator(SharedStockAdapter stock) { this.stock=Objects.requireNonNull(stock); }
    public Result<Void> validate(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy baseline,ChangeSet changes,Instant time) {
        try {
            if (!AftersaleChangeSetAssembler.OPERATION.equals(op.operation()) || actor==null || time==null)
                return Result.rejected("INVALID_INPUT","Invalid aftersale context");
            if (actor.role()!=Role.CLERK) return Result.rejected("FORBIDDEN","Daily aftersale requires clerk");
            AftersaleCommand command=request.as(AftersaleCommand.class);
            if (!command.requestKey().equals(op.requestKey())) return Result.rejected("REQUEST_KEY_CONFLICT","Request key mismatch");
            for (var line:command.lines()) {
                var original=baseline.find("DOC_LINE",line.saleLineId()).orElseThrow();
                var header=baseline.find("DOCUMENT",original.as(DocumentLine.class).documentId()).orElseThrow().as(DocumentHeader.class);
                if (!header.kind().equals("SALE") || !header.status().equals("CONFIRMED"))
                    return Result.rejected("REFERENCE_INVALID","Original sale is not effective");
            }
            var result=AftersaleEngine.create(baseline,new IdClock(Clock.fixed(time,ZoneOffset.UTC)),op.requestKey())
                .apply(new SharedWorkingCopyView(baseline),command,SharedRoles.toLocal(actor.role()),op.targetId());
            if(result instanceof AftersaleResult.Rejected r) return Result.rejected(r.code(),r.message());
            if(result instanceof AftersaleResult.Failed r) return Result.failed(r.code(),r.message());
            var prepared=(AftersaleResult.Prepared)result;
            if(prepared.redemptions().isEmpty()) return Result.rejected("INVALID_INPUT","No aftersale lines");
            var assembler=new AftersaleChangeSetAssembler();
            ChangeSet expected;
            var existing=baseline.find("DOCUMENT",op.targetId());
            if(existing.isPresent()) {
                var header=existing.get().as(DocumentHeader.class);
                if(!header.status().equals("DRAFT") || !header.createdBy().equals(actor.accountId()))
                    return Result.rejected("FORBIDDEN","Owned unfinished target required");
                Set<String> traced=new HashSet<>();
                for(var r:baseline.records("DOC_EVENT")) if(r.data().path("documentId").asText().equals(op.targetId()) && r.data().path("action").asText().equals("READ_FOR_AFTERSALE")) {
                    var detail=JsonCodec.tree(r.as(DocEvent.class).detailSnapshot());
                    if(detail.has("originalSaleLineId")) traced.add(detail.path("originalSaleLineId").asText());
                    detail.path("saleLineIds").forEach(n -> traced.add(n.asText()));
                }
                if(command.lines().stream().anyMatch(l -> !traced.contains(l.saleLineId())))
                    return Result.rejected("DRAFT_HAS_NO_READ_TRACE","Every original line needs a reliably saved IF-11 trace");
                expected=assembler.assembleForDraft(prepared,op.targetId(),baseline,op,actor,request);
            } else {
                // Direct internal callers remain supported for same-person historical submissions.
                for(var l:command.lines()) {
                    var h=baseline.find("DOCUMENT",baseline.find("DOC_LINE",l.saleLineId()).orElseThrow().as(DocumentLine.class).documentId()).orElseThrow().as(DocumentHeader.class);
                    if(!h.createdBy().equals(actor.accountId())) return Result.rejected("DRAFT_HAS_NO_READ_TRACE","Cross-person sale requires IF-11");
                }
                expected=assembler.assemble(prepared,op.targetId(),op,actor,request);
            }
            expected=SubmissionTime.assign(baseline,expected,time);
            if(!CandidateEquality.same(expected,changes)) return Result.rejected("REFERENCE_INVALID","Complete effects differ from original request");
            return stock.review(baseline,request,changes);
        } catch(RuntimeException e) { return Result.rejected("INVALID_INPUT","Invalid aftersale request or references"); }
    }
}
