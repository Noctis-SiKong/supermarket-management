package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;

import nz.ac.aut.pdc.supermarket.stock.StockEntry;

/** 原核销关联库存流水只读端口（IF-11/stock 事实读取，当前用替身）。 */
public interface StockEffectReader {

    List<StockEntry> entriesFor(String redemptionId);
}
