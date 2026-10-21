package nz.ac.aut.pdc.supermarket.aftersale;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.supermarket.cui.*;
import nz.ac.aut.pdc.supermarket.stock.*;

/** Same algorithm and stable IDs for preview and lock-held request reconstruction. */
public final class AftersaleEngine {
    private AftersaleEngine() {}
    public static AftersaleService create(WorkingCopy wc,IdClock clock,String key) {
        var ids=new SharedIdGenerator(clock,key);
        return new AftersaleService(new SharedSaleReader(wc),new SharedQuotaLedgerReader(wc),new SharedProductSnapshotReader(wc),
            new StockService(new SharedProductCatalog(wc),ids,new StockValidator()),new QuotaViewBuilder(),new QuotaCalculator(),
            new QualityPairValidator(),new AftersaleAssembler(ids),ids);
    }
}
