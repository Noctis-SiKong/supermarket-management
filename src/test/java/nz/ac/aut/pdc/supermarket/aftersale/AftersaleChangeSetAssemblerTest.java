package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.StockEntry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AftersaleChangeSetAssemblerTest {

    private final AftersaleChangeSetAssembler assembler = new AftersaleChangeSetAssembler();

    @Test
    public void assemblesAllAftersaleTables() {
        AftersaleResult.Prepared prepared = new AftersaleResult.Prepared(
                List.of(redemption()),
                List.of(replacement()),
                List.of(new AftersaleResult.StockEntryView(
                        "SE-1", "A", nz.ac.aut.pdc.supermarket.stock.Position.S,
                        -1, "EV-1", null)),
                List.of(new Settlement("R-1", "RL-1", SettlementDirection.REFUND,
                        new BigDecimal("3.33"))));

        ChangeSet changes = assembler.assemble(
                prepared, "AF-1",
                new OperationContext(null, "REQ-1", AftersaleChangeSetAssembler.OPERATION, "AF-1"),
                new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK),
                BusinessRequest.of(Map.of("request", "aftersale")));

        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("DOCUMENT")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("AFTERSALE")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("DOC_LINE")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("DOC_EVENT")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("REDEMPTION")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("REPLACEMENT")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("STOCK_ENTRY")));
        assertTrue(changes.inserts().stream().anyMatch(r -> r.table().equals("MONEY_ENTRY")));

        var stock = changes.inserts().stream()
                .filter(r -> r.table().equals("STOCK_ENTRY")).findFirst().orElseThrow();
        StockEntry entry = stock.as(StockEntry.class);
        assertEquals("RL-1", entry.sourceLineId());
        assertEquals(StockEntry.Position.S, entry.position());
        assertEquals(-1, entry.quantityDelta());
        assertEquals(0, entry.commitOrdinal());
    }

    private Redemption redemption() {
        return new Redemption(
                "R-1", "SL-1", "SL-1", "A", "AF-1",
                RedemptionKind.RETURN, Effect.CONSUME, 1, new BigDecimal("3.33"),
                "REQ-1", null, false, false, "其他原因");
    }

    private Replacement replacement() {
        return new Replacement(
                "RL-1", "AF-1", "R-1", "A", 1, "商品",
                new BigDecimal("3.33"), new BigDecimal("3.33"), "NORMAL", null);
    }
}
