package nz.ac.aut.pdc.supermarket.query;

import java.util.List;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.stock.LossRecord;

/** Read-only loss-record adapter port; historical prototype can use doubles.
  */
public interface LossRecordReader {

    Optional<LossRecord> findById(String lossId);

    List<LossRecord> findByBarcode(String barcode);
}
