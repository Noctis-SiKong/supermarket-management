package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;

import nz.ac.aut.pdc.supermarket.stock.StockEntry;

/** Historical read-only stock-effect port for a redemption (IF-11/stock facts), backed by doubles in tests.
  */
public interface StockEffectReader {

    List<StockEntry> entriesFor(String redemptionId);
}
