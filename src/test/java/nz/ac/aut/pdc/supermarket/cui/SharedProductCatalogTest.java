package nz.ac.aut.pdc.supermarket.cui;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.catalog.Product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedProductCatalogTest {

    @Test
    public void readsProductNameFromWorkingCopy() {
        WorkingCopy workingCopy = new WorkingCopy("wc-1", 1, List.of(
                RecordData.of("PRODUCT", "6901234567890",
                        new Product("6901234567890", "商品", "件", new BigDecimal("8.00"), null))));

        SharedProductCatalog catalog = new SharedProductCatalog(workingCopy);

        assertEquals("商品", catalog.productName("6901234567890").orElseThrow());
        assertTrue(catalog.productName("00000000").isEmpty());
    }
}
