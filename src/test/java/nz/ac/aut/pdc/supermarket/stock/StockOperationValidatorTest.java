package nz.ac.aut.pdc.supermarket.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.catalog.Product;

import static org.junit.Assert.assertEquals;

public class StockOperationValidatorTest {

    private final StockChangeSetAssembler assembler = new StockChangeSetAssembler();
    private final StockOperationValidator validator =
            new StockOperationValidator(StockChangeSetAssembler.REPLENISHMENT);
    private final OperationContext op = new OperationContext(
            null, "11111111-1111-1111-1111-111111111111", StockChangeSetAssembler.REPLENISHMENT, new nz.ac.aut.pdc.shared.IdClock(java.time.Clock.systemUTC()).entityId("11111111-1111-1111-1111-111111111111","document"));
    private final OperationContext.ActorContext actor =
            new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK);
    private final BusinessRequest request = BusinessRequest.of(Map.of(
            "barcode", "00123456", "quantity", 3));

    @Test
    public void validReplenishmentPasses() {
        Result<Void> result = validator.validate(
                op, actor, request, baseline(),
                changes(), Instant.parse("2026-10-21T04:00:00Z"));

        assertEquals(result.code(), Result.Status.VALUE, result.status());
    }

    @Test
    public void tamperedLineQuantityIsRejected() {
        ChangeSet tampered = new ChangeSet(
                changes().inserts().stream()
                        .map(r -> r.table().equals("DOC_LINE")
                                ? RecordData.of("DOC_LINE", "LINE-1", new DocumentLine(
                                        "LINE-1", "DOC-1", "REPLENISHMENT", "00123456", 4))
                                : r)
                        .toList(), List.of(), List.of());

        Result<Void> result = validator.validate(
                op, actor, request, baseline(),
                tampered, Instant.parse("2026-10-21T04:00:00Z"));

        assertEquals(Result.Status.REJECTED, result.status());
    }

    @Test
    public void extraProductRecordIsRejected() {
        ChangeSet withProduct = new ChangeSet.Builder()
                .merge(changes())
                .add(RecordData.of("PRODUCT", "00123456",
                        new Product("00123456", "商品", "盒", new BigDecimal("3.33"), null)))
                .build();

        Result<Void> result = validator.validate(
                op, actor, request, baseline(),
                withProduct, Instant.parse("2026-10-21T04:00:00Z"));

        assertEquals(Result.Status.REJECTED, result.status());
    }

    private WorkingCopy baseline() {
        return new WorkingCopy("wc-1",1,List.of(
            RecordData.of("PRODUCT","00123456",new Product("00123456","虚构商品","件",new BigDecimal("3.33"),null)),
            RecordData.of("DOC_LINE","opening-line",new DocumentLine("opening-line","fictional-opening","OPENING","00123456",10)),
            RecordData.of("STOCK_ENTRY","opening-entry",new nz.ac.aut.pdc.shared.StockEntry("opening-entry","opening-line",nz.ac.aut.pdc.shared.StockEntry.Position.W,10,"opening-event",null,null,null,1))));
    }
    @Test public void fractionalAndOverflowQuantitiesCannotBeTruncated() {
        for(Object quantity:List.of(new BigDecimal("3.9"),new BigDecimal("4294967299"))) {
            var raw=BusinessRequest.of(Map.of("barcode","00123456","quantity",quantity));
            assertEquals(Result.Status.REJECTED,validator.validate(op,actor,raw,baseline(),changes(),Instant.now()).status());
        }
    }
    @Test public void patchesRemovalsAndRoleEscalationAreRejected() {
        var good=changes();
        var patch=new ChangeSet(good.inserts(),List.of(new ChangeSet.Patch("DOCUMENT","other",1,"{}")));
        var removal=new ChangeSet(good.inserts(),List.of(),List.of(new ChangeSet.Removal("DOC_LINE","opening-line",1)));
        assertEquals(Result.Status.REJECTED,validator.validate(op,actor,request,baseline(),patch,Instant.now()).status());
        assertEquals(Result.Status.REJECTED,validator.validate(op,actor,request,baseline(),removal,Instant.now()).status());
        assertEquals("FORBIDDEN",validator.validate(op,new OperationContext.ActorContext("admin",OperationContext.Role.ADMIN),request,baseline(),good,Instant.now()).code());
    }
    private ChangeSet changes() {
        var time=Instant.parse("2026-10-21T04:00:00Z");
        var result=StockBusiness.prepare(op,actor,request,baseline(),new nz.ac.aut.pdc.shared.IdClock(java.time.Clock.fixed(time,java.time.ZoneOffset.UTC)));
        org.junit.Assert.assertTrue(result.detail(),result.hasValue());
        return nz.ac.aut.pdc.infrastructure.SubmissionTime.assign(baseline(),result.value(),time);
    }
}
