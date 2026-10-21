package nz.ac.aut.pdc.supermarket.stock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LossRecordTest {

    @Test
    public void disposeChangesStateAndKeepsOriginalFields() {
        LossRecord pending = new LossRecord(
                "LOSS-1", null, "A", Position.S, 2, LossOrigin.STORE,
                null, LossState.PENDING);

        LossRecord disposed = pending.dispose("已报废");

        assertEquals(LossState.DISPOSED, disposed.dispositionState());
        assertEquals("已报废", disposed.disposition());
        assertEquals(pending.lossId(), disposed.lossId());
        assertEquals(pending.quantity(), disposed.quantity());
        assertEquals(pending.origin(), disposed.origin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankDispositionIsRejected() {
        new LossRecord("LOSS-1", null, "A", Position.S, 2, LossOrigin.STORE,
                null, LossState.PENDING).dispose(" ");
    }
}
