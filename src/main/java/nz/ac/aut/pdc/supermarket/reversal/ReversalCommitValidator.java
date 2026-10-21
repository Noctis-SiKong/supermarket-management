package nz.ac.aut.pdc.supermarket.reversal;

import java.time.*;
import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.infrastructure.SubmissionTime;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

/** Whole-document reversal: one D1 decision and exact reconstructed inverse group. */
public final class ReversalCommitValidator implements SubmissionPort.CommitValidator {
    public Result<Void> validate(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy wc,ChangeSet delta,Instant time) {
        if(actor==null || !(actor.role()==Role.MANAGER || actor.role()==Role.ADMIN)) return Result.rejected("FORBIDDEN","Reversal requires manager/admin");
        try {
            var command=request.as(DocumentReversalCommand.class);
            if(!ReversalChangeSetAssembler.OPERATION.equals(op.operation()) || !command.targetDocumentId().equals(op.targetId()) || !command.requestKey().equals(op.requestKey()))
                return Result.rejected("REFERENCE_INVALID","Reversal request context mismatch");
            var target=wc.find("DOCUMENT",command.targetDocumentId()).orElseThrow().as(DocumentHeader.class);
            if(!Set.of("CONFIRMED","APPROVED").contains(target.status()) || target.kind().equals("REVERSAL"))
                return Result.rejected("REFERENCE_INVALID","Target is not an effective business document");
            String blocker=EffectiveDependencies.blocker(wc,command.targetDocumentId());
            if(blocker!=null) return Result.rejected("DEPENDENCY_BLOCKED",blocker);
            var expected=new ReversalChangeSetAssembler().assemble(wc,command.targetDocumentId(),op,actor,request,new IdClock(Clock.fixed(time,ZoneOffset.UTC)));
            expected=SubmissionTime.assign(wc,expected,time);
            if(!CandidateEquality.same(expected,delta)) return Result.rejected("REFERENCE_INVALID","Reversal must cover exactly the target's complete effects");
            return new SharedStockAdapter().review(wc,request,delta);
        } catch(RuntimeException e) { return Result.rejected("INVALID_INPUT","Invalid reversal request or references"); }
    }
}
