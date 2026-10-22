package nz.ac.aut.pdc.infrastructure;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext.ActorContext;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.reversal.*;
import nz.ac.aut.pdc.supermarket.stock.*;

/** Real stock/reversal reconstruction through the public interface; Probe isolates template ordering. */
public class ReconstructingValidatorTest {
    private final Instant time=Instant.parse("2026-10-22T16:00:00Z");
    private final IdClock ids=new IdClock(Clock.fixed(time,ZoneOffset.UTC));
    private final ActorContext clerk=new ActorContext("fictional-clerk",OperationContext.Role.CLERK);
    private final ActorContext manager=new ActorContext("fictional-manager",OperationContext.Role.MANAGER);
    private final WorkingCopy baseline=new WorkingCopy("fixture",1,List.of(
        RecordData.of("PRODUCT","00123456",new Product("00123456","Fictional product","item",new BigDecimal("3.33"),null)),
        RecordData.of("DOCUMENT","receipt",new DocumentHeader("receipt","RECEIPT","APPROVED","old","fp","fictional-manager","2026-10-21T00:00:00Z","fictional-manager","2026-10-21T00:00:00Z",null)),
        RecordData.of("RECEIPT","receipt",Map.of("reviewState","APPROVED")),
        RecordData.of("DOC_LINE","line",new DocumentLine("line","receipt","RECEIPT","00123456",10)),
        RecordData.of("STOCK_ENTRY","entry",new nz.ac.aut.pdc.shared.StockEntry("entry","line",nz.ac.aut.pdc.shared.StockEntry.Position.W,10,"event",null,null,null,1))));
    private record Case(SubmissionPort.CommitValidator validator,OperationContext op,ActorContext actor,BusinessRequest request,ChangeSet raw) {}
    private List<Case> cases() {
        String stockKey="11111111-1111-1111-1111-111111111111",reverseKey="22222222-2222-2222-2222-222222222222";
        var stockOp=new OperationContext(null,stockKey,StockChangeSetAssembler.REPLENISHMENT,ids.entityId(stockKey,"document"));
        var stockRequest=BusinessRequest.of(Map.of("barcode","00123456","quantity",3));
        var stock=StockBusiness.prepare(stockOp,clerk,stockRequest,baseline,ids);assertTrue(stock.detail(),stock.hasValue());
        var reverseOp=new OperationContext(null,reverseKey,ReversalChangeSetAssembler.OPERATION,"receipt");
        var reverseRequest=BusinessRequest.of(new DocumentReversalCommand("receipt","Fictional reversal",reverseKey));
        var reverse=new ReversalChangeSetAssembler().assemble(baseline,"receipt",reverseOp,manager,reverseRequest,ids);
        return List.of(new Case(new StockOperationValidator(stockOp.operation()),stockOp,clerk,stockRequest,stock.value()),
            new Case(new ReversalCommitValidator(),reverseOp,manager,reverseRequest,reverse));
    }
    private Result<Void> validate(Case c,ChangeSet delta) {return c.validator().validate(c.op(),c.actor(),c.request(),baseline,delta,time);}
    @Test public void differentProductionValidatorsReconstructNormalizeAndAcceptThroughOneInterface() {
        for(Case c:cases()) {
            ChangeSet timed=SubmissionTime.assign(baseline,c.raw(),time);
            assertEquals(c.op().operation(),Result.Status.VALUE,validate(c,timed).status());
            assertEquals("REFERENCE_INVALID",validate(c,c.raw()).code());
            assertEquals("REFERENCE_INVALID",c.validator().validate(c.op(),c.actor(),c.request(),baseline,timed,time.plusSeconds(1)).code());
        }
    }
    @Test public void bothSubclassesRejectMissingExtraAlteredCrossDocumentAndDeletionEffects() {
        for(Case c:cases()) {
            ChangeSet good=SubmissionTime.assign(baseline,c.raw(),time);
            var missing=new ChangeSet(good.inserts().subList(1,good.inserts().size()),good.patches(),good.removals());
            var extra=new ChangeSet.Builder().merge(good).add(RecordData.of("PRODUCT","00123457",new Product("00123457","Unrelated","item",BigDecimal.ONE,null))).build();
            var changed=new ChangeSet(good.inserts().stream().map(r -> {
                if(!r.table().equals("DOC_LINE"))return r;
                DocumentLine line=r.as(DocumentLine.class);return RecordData.of("DOC_LINE",r.id(),new DocumentLine(line.lineId(),line.documentId(),line.lineKind(),line.barcode(),line.quantity()+1));
            }).toList(),good.patches(),good.removals());
            var cross=new ChangeSet(good.inserts().stream().map(r -> {
                if(!r.table().equals("DOC_LINE"))return r;
                DocumentLine line=r.as(DocumentLine.class);return RecordData.of("DOC_LINE",r.id(),new DocumentLine(line.lineId(),"other-document",line.lineKind(),line.barcode(),line.quantity()));
            }).toList(),good.patches(),good.removals());
            var delete=new ChangeSet(good.inserts(),good.patches(),List.of(new ChangeSet.Removal("DOC_LINE","line",1)));
            for(ChangeSet bad:List.of(missing,extra,changed,cross,delete)) {
                var result=validate(c,bad);assertEquals(c.op().operation(),Result.Status.REJECTED,result.status());assertEquals("REFERENCE_INVALID",result.code());
            }
        }
    }
    @Test public void concreteAuthorizationAndOriginalExceptionBoundariesRemainDistinct() {
        var stock=cases().get(0);var reverse=cases().get(1);
        assertEquals("FORBIDDEN",stock.validator().validate(stock.op(),new ActorContext("fictional-admin",OperationContext.Role.ADMIN),stock.request(),baseline,stock.raw(),time).code());
        assertEquals("FORBIDDEN",reverse.validator().validate(reverse.op(),clerk,reverse.request(),baseline,reverse.raw(),time).code());
        assertEquals("INVALID_INPUT",reverse.validator().validate(reverse.op(),manager,reverse.request(),baseline,reverse.raw(),null).code());
        try {stock.validator().validate(stock.op(),clerk,stock.request(),baseline,stock.raw(),null);fail("Stock must retain its exception policy");}
        catch(NullPointerException expected) { }
        var good=SubmissionTime.assign(baseline,reverse.raw(),time);
        var inserts=new ArrayList<>(good.inserts());inserts.add(good.inserts().get(0));
        var duplicate=new ChangeSet(inserts,good.patches(),good.removals());
        assertEquals("INVALID_INPUT",validate(reverse,duplicate).code());
    }
    @Test public void templatePropagatesReconstructionRejectionAndFailureBeforeTouchingTimeOrReview() {
        for(Result.Status status:List.of(Result.Status.REJECTED,Result.Status.FAILED)) {
            Probe probe=new Probe();probe.prepared=new Result<>(status,null,"OWNER_CODE","quantity","line","Owner detail");
            Result<Void> result=probe.validate(null,null,null,null,null,null);
            assertEquals(probe.prepared.failure(),result);assertEquals(1,probe.reconstructions);assertEquals(0,probe.reviews);
        }
    }
    @Test public void templatePermitsOwnerApprovedRemovalGroupsAndReviewsOnlyExactCandidates() {
        Probe probe=new Probe();var removal=new ChangeSet(List.of(),List.of(),List.of(new ChangeSet.Removal("DOC_LINE","line",1)));
        probe.prepared=Result.prepared(removal);probe.reviewResult=Result.failed("REVIEW_FAILED","Owner review failure");
        assertEquals(probe.reviewResult,probe.validate(null,null,null,baseline,removal,time));assertEquals(1,probe.reviews);
        assertEquals("MISMATCH",probe.validate(null,null,null,baseline,new ChangeSet(List.of(),List.of()),time).code());assertEquals(1,probe.reviews);
    }
    /** Test-only candidate/review probe, never registered in production. */
    private static final class Probe extends AbstractReconstructingCommitValidator {
        Result<ChangeSet> prepared;Result<Void> reviewResult;int reconstructions,reviews;
        protected Result<ChangeSet> reconstruct(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy baseline,Instant time){reconstructions++;return prepared;}
        protected Result<Void> mismatch(){return Result.rejected("MISMATCH","exact comparison failed");}
        protected Result<Void> review(OperationContext op,ActorContext actor,BusinessRequest request,WorkingCopy baseline,ChangeSet actual,Instant time){reviews++;return reviewResult;}
    }
}
