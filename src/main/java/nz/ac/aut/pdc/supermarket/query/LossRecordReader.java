package nz.ac.aut.pdc.supermarket.query;

import java.util.List;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.stock.LossRecord;

/** 损耗记录只读端口（B 侧适配，当前用替身）。 */
public interface LossRecordReader {

    Optional<LossRecord> findById(String lossId);

    List<LossRecord> findByBarcode(String barcode);
}
