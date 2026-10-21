package nz.ac.aut.pdc.shared;

import java.util.List;

/** Voucher is an exact persisted sale document ID or printed stable sale line ID. Empty line selection means that receipt's lines. */
public record AftersaleReadRequest(String voucher, String aftersaleId, List<String> saleLineIds) {
    public static final String OPERATION = "READ_FOR_AFTERSALE";
    public AftersaleReadRequest {
        if (voucher == null || voucher.isBlank() || aftersaleId == null || aftersaleId.isBlank())
            throw new IllegalArgumentException("Exact voucher and target aftersale required");
        saleLineIds = List.copyOf(saleLineIds);
    }
}
