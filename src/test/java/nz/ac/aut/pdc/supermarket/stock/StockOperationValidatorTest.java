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
            null, "REQ-1", StockChangeSetAssembler.REPLENISHMENT, "DOC-1");
    private final OperationContext.ActorContext actor =
            new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK);
    private final BusinessRequest request = BusinessRequest.of(Map.of(
            "barcode", "00123456", "quantity", 3));

    @Test
    public void validReplenishmentPasses() {
        Result<Void> result = validator.validate(
                op, actor, request, new WorkingCopy("wc-1", 1, List.of()),
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
                op, actor, request, new WorkingCopy("wc-1", 1, List.of()),
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
                op, actor, request, new WorkingCopy("wc-1", 1, List.of()),
                withProduct, Instant.parse("2026-10-21T04:00:00Z"));

        assertEquals(Result.Status.REJECTED, result.status());
    }

    private ChangeSet changes() {
        ReplenishmentResult.Prepared prepared = new ReplenishmentResult.Prepared(
                "TR-1",
                List.of(new StockEntry("SE-W", "00123456", Position.W, -3, "EV-W", "TR-1", null, null),
                        new StockEntry("SE-S", "00123456", Position.S, 3, "EV-S", "TR-1", null, null)),
                List.of());
        return assembler.assembleReplenishment(
                prepared, "DOC-1", "LINE-1", op, actor, request);
    }
}
