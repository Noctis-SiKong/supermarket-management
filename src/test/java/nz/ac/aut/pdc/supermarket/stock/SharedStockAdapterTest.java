package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.StockEntry;
import nz.ac.aut.pdc.shared.WorkingCopy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedStockAdapterTest {

    private final SharedStockAdapter adapter = new SharedStockAdapter();

    @Test
    public void preparesSharedStockEntriesFromWorkingCopy() {
        WorkingCopy workingCopy = workingCopy();

        Result<ChangeSet> result = adapter.prepare(
                workingCopy, ChangeSet.empty(),
                List.of(sharedEntry("SE-2", -2, "EV-2")));

        assertEquals(Result.Status.PREPARED, result.status());
        assertEquals(1, result.value().inserts().size());
        assertEquals("STOCK_ENTRY", result.value().inserts().get(0).table());
    }

    @Test
    public void rejectsInsufficientStock() {
        Result<ChangeSet> result = adapter.prepare(
                workingCopy(), ChangeSet.empty(),
                List.of(sharedEntry("SE-2", -6, "EV-2")));

        assertEquals(Result.Status.REJECTED, result.status());
        assertEquals("INSUFFICIENT_STOCK", result.code());
    }

    @Test
    public void workingCopyViewProjectsBalances() {
        SharedWorkingCopyView view = new SharedWorkingCopyView(workingCopy());

        assertEquals(1, view.baseLast());
        assertEquals(5, view.balances(List.of("A")).get(0).quantity());
    }

    @Test
    public void reviewAcceptsValidStockChangeAndRejectsOverdraw() {
        ChangeSet valid = new ChangeSet.Builder()
                .add(RecordData.of("STOCK_ENTRY", "SE-2", sharedEntry("SE-2", -2, "EV-2")))
                .build();
        ChangeSet invalid = new ChangeSet.Builder()
                .add(RecordData.of("STOCK_ENTRY", "SE-2", sharedEntry("SE-2", -6, "EV-2")))
                .build();

        assertEquals(Result.Status.VALUE, adapter.review(workingCopy(), null, valid).status());
        assertEquals(Result.Status.REJECTED, adapter.review(workingCopy(), null, invalid).status());
    }

    @Test
    public void recoveryRejectsNegativeAggregate() {
        WorkingCopy negative = new WorkingCopy("wc-bad", 1, List.of(
                RecordData.of("DOC_LINE", "DL-1", new DocumentLine("DL-1", "DOC-1", "SALE", "A", 5)),
                RecordData.of("STOCK_ENTRY", "SE-NEG",
                        new StockEntry("SE-NEG", "DL-1", StockEntry.Position.S, -1,
                                "EV-NEG", null, null, null, 1))));
        try {
            adapter.validateRecovered(negative);
        } catch (java.io.IOException expected) {
            return;
        }
        throw new AssertionError("expected IOException");
    }

    private WorkingCopy workingCopy() {
        return new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("DOC_LINE", "DL-1", new DocumentLine("DL-1", "DOC-1", "SALE", "A", 5)),
                RecordData.of("STOCK_ENTRY", "SE-1",
                        new StockEntry("SE-1", "DL-1", StockEntry.Position.S, 5,
                                "EV-1", null, null, null, 1))));
    }

    private StockEntry sharedEntry(String id, int delta, String eventKey) {
        return new StockEntry(id, "DL-1", StockEntry.Position.S, delta, eventKey, null, null, null, 0);
    }
}
