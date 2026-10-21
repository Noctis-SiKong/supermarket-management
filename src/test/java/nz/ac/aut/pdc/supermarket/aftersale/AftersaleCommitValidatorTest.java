package nz.ac.aut.pdc.supermarket.aftersale;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.Test;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.supermarket.sales.Pricing;
import nz.ac.aut.pdc.supermarket.stock.*;
import nz.ac.aut.pdc.infrastructure.SubmissionTime;
import static org.junit.Assert.*;

/** Full domain reconstruction against an explicitly fictional immutable sale snapshot (no runtime balances edited). */
public class AftersaleCommitValidatorTest {
    final Instant time=Instant.parse("2026-10-21T04:00:00Z");
    final String key="11111111-1111-1111-1111-111111111111";
    final OperationContext op=new OperationContext(null,key,"AFTERSALE","AF-1");
    final OperationContext.ActorContext actor=new OperationContext.ActorContext("acc-1",OperationContext.Role.CLERK);
    final AftersaleCommitValidator validator=new AftersaleCommitValidator(new SharedStockAdapter());
    WorkingCopy baseline() {
        var quote=new Pricing().quote(List.of(new Pricing.Line("SL-1","00123456","虚构商品",new BigDecimal("3.33"),3)),List.of(),time);
        return new WorkingCopy("fictional",1,List.of(
            RecordData.of("DOCUMENT","S-1",new DocumentHeader("S-1","SALE","CONFIRMED","sale-key","fp","acc-1",time.toString(),"acc-1",time.toString(),null)),
            RecordData.of("DOC_LINE","SL-1",new DocumentLine("SL-1","S-1","SALE","00123456",3)),
            RecordData.of("SALE","S-1",Map.of()),RecordData.of("SALE_LINE","SL-1",quote.lines().get(0))));
    }
    AftersaleCommand command(int lines) {
        List<AftersaleCommandLine> out=new ArrayList<>();
        for(int i=0;i<lines;i++)out.add(new AftersaleCommandLine("SL-1",RedemptionKind.RETURN,1,false,false,AftersaleReasonKind.OTHER,"虚构其他原因",null,0,false));
        return new AftersaleCommand(key,null,out);
    }
    ChangeSet candidate(AftersaleCommand command) {
        var wc=baseline();
        var prepared=(AftersaleResult.Prepared)AftersaleEngine.create(wc,new IdClock(Clock.fixed(time,ZoneOffset.UTC)),key)
            .apply(new SharedWorkingCopyView(wc),command,nz.ac.aut.pdc.supermarket.auth.Role.CLERK,"AF-1");
        return SubmissionTime.assign(wc,new AftersaleChangeSetAssembler().assemble(prepared,"AF-1",op,actor,BusinessRequest.of(command)),time);
    }
    Result<Void> validate(ChangeSet delta) { return validator.validate(op,actor,BusinessRequest.of(command(1)),baseline(),delta,time); }
    @Test public void completeValidAftersalePasses() {assertEquals(Result.Status.VALUE,validate(candidate(command(1))).status());}
    @Test public void omittedMoneyOrLineOrRedemptionIsRejected() {
        var good=candidate(command(1));
        for(String table:List.of("MONEY_ENTRY","DOC_LINE","REDEMPTION","AFTERSALE","DOC_EVENT"))
            assertEquals(table,Result.Status.REJECTED,validate(new ChangeSet(good.inserts().stream().filter(r -> !r.table().equals(table)).toList(),good.patches())).status());
    }
    @Test public void changedRefundAmountIsRejected() {
        var good=candidate(command(1));
        var bad=new ChangeSet(good.inserts().stream().map(r -> r.table().equals("MONEY_ENTRY")?RecordData.of(r.table(),r.id(),new MoneyEntry(r.id(),"AF-1",new BigDecimal("-999.00"),key,null)):r).toList(),good.patches());
        assertEquals(Result.Status.REJECTED,validate(bad).status());
    }
    @Test public void unrelatedDocumentLineAndPatchAreRejected() {
        var good=candidate(command(1));
        var extra=new ChangeSet.Builder().merge(good).add(RecordData.of("DOC_LINE","smuggled",new DocumentLine("smuggled","S-1","RETURN","00123456",1))).build();
        assertEquals(Result.Status.REJECTED,validate(extra).status());
        assertEquals(Result.Status.REJECTED,validate(new ChangeSet(good.inserts(),List.of(new ChangeSet.Patch("DOCUMENT","S-1",1,"{\"status\":\"VOIDED\"}")))).status());
    }
    @Test public void multipleReturnsShareQuotaAndProduceOneNetMoneyEntry() {
        var command=command(2);var delta=candidate(command);
        assertEquals(Result.Status.VALUE,validator.validate(op,actor,BusinessRequest.of(command),baseline(),delta,time).status());
        assertEquals(1,delta.inserts().stream().filter(r -> r.table().equals("MONEY_ENTRY")).count());
    }
    @Test public void excessiveCumulativeQuantityAndMissingOriginalAreRejected() {
        assertEquals(Result.Status.REJECTED,validator.validate(op,actor,BusinessRequest.of(command(4)),baseline(),candidate(command(1)),time).status());
        assertEquals(Result.Status.REJECTED,validator.validate(op,actor,BusinessRequest.of(command(1)),new WorkingCopy("empty",0,List.of()),candidate(command(1)),time).status());
    }
    @Test public void registryRetainsOwnedOperations() {assertTrue(BCommitValidators.create(new SharedStockAdapter()).containsKey(DraftDeletion.OPERATION));}
}
