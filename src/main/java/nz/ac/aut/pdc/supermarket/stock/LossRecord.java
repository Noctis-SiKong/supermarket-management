package nz.ac.aut.pdc.supermarket.stock;

import java.util.Objects;

/** ERD loss facts: store loss deducts source stock once; customer returns create no stock movements here. Disposition/correction facts append later.
 */
public record LossRecord(
        String lossId,
        String sourceLineId,
        String barcode,
        Position position,
        int quantity,
        LossOrigin origin,
        String disposition,
        LossState dispositionState) {

    public LossRecord {
        if (lossId == null || lossId.isBlank()) {
            throw new IllegalArgumentException("lossId must not be blank");
        }
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if(origin==LossOrigin.STORE) Objects.requireNonNull(position,"position");
        else if(position!=null) throw new IllegalArgumentException("Customer return has no stock position");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(dispositionState, "dispositionState");
    }

    /** Record disposition and switch to DISPOSED without saving/submitting.
  */
    public LossRecord dispose(String disposition) {
        if (disposition == null || disposition.isBlank()) {
            throw new IllegalArgumentException("disposition must not be blank");
        }
        return new LossRecord(
                lossId, sourceLineId, barcode, position, quantity, origin,
                disposition, LossState.DISPOSED);
    }
}


