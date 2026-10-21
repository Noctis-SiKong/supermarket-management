package nz.ac.aut.pdc.supermarket.query;

import nz.ac.aut.pdc.supermarket.stock.LossOrigin;
import nz.ac.aut.pdc.supermarket.stock.LossState;
import nz.ac.aut.pdc.supermarket.stock.Position;

/** 损耗记录的只读展示视图。 */
public record LossRecordView(
        String lossId,
        String barcode,
        Position position,
        int quantity,
        LossOrigin origin,
        String disposition,
        LossState dispositionState) {
}
