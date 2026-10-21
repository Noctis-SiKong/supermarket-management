package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.Objects;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.DocEvent;
import nz.ac.aut.pdc.shared.DocumentHeader;
import nz.ac.aut.pdc.shared.DocumentLine;
import nz.ac.aut.pdc.shared.JsonCodec;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.StockEntry;

/**
 * 把已校验的售后候选组装为 A 的统一 ChangeSet。
 *
 * <p>只组装，不保存/不提交；ID 由 B 侧稳定生成，commitOrdinal=0 由 IF-05 赋。
 * 表名/字段按 A handoff 与共享规范：DOCUMENT/AFTERSALE/DOC_LINE/DOC_EVENT/
 * REDEMPTION/REPLACEMENT/STOCK_ENTRY/MONEY_ENTRY。</p>
 */
public final class AftersaleChangeSetAssembler {

    public static final String OPERATION = "AFTERSALE";

    public ChangeSet assemble(
            AftersaleResult.Prepared prepared,
            String aftersaleId,
            OperationContext operation,
            OperationContext.ActorContext actor,
            BusinessRequest request) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(aftersaleId, "aftersaleId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");

        ChangeSet.Builder out = new ChangeSet.Builder();
        String fingerprint = JsonCodec.fingerprint(operation.operation(), actor.accountId(), request);
        out.add(RecordData.of("DOCUMENT", aftersaleId, new DocumentHeader(
                aftersaleId, "AFTERSALE", "CONFIRMED", operation.requestKey(), fingerprint,
                actor.accountId(), null, actor.accountId(), null, null)));
        out.add(RecordData.of("AFTERSALE", aftersaleId,
                java.util.Map.of("status", "CONFIRMED", "requestKey", operation.requestKey())));
        out.add(RecordData.of("DOC_EVENT", aftersaleId + "-EVENT", new DocEvent(
                aftersaleId + "-EVENT", aftersaleId, 1, "AFTERSALE_CONFIRM",
                actor.accountId(), null, "{}")));

        for (Redemption redemption : prepared.redemptions()) {
            out.add(RecordData.of("DOC_LINE", redemption.redemptionId(), new DocumentLine(
                    redemption.redemptionId(), aftersaleId,
                    redemption.kind().name(), redemption.originalBarcode(), redemption.quantity())));
            out.add(RecordData.of("REDEMPTION", redemption.redemptionId(), redemption));
        }
        for (Replacement replacement : prepared.replacements()) {
            out.add(RecordData.of("DOC_LINE", replacement.lineId(), new DocumentLine(
                    replacement.lineId(), aftersaleId, "REPLACEMENT",
                    replacement.barcode(), replacement.quantity())));
            out.add(RecordData.of("REPLACEMENT", replacement.lineId(), replacement));
        }
        for (AftersaleResult.StockEntryView entry : prepared.stockEntries()) {
            String sourceLineId = sourceLineId(entry, prepared);
            out.add(RecordData.of("STOCK_ENTRY", entry.id(), new StockEntry(
                    entry.id(), sourceLineId,
                    StockEntry.Position.valueOf(entry.position().name()),
                    entry.delta(), entry.eventKey(), null, null, entry.transferKey(), 0)));
        }
        for (Settlement settlement : prepared.settlements()) {
            if (settlement.direction() == SettlementDirection.NONE) {
                continue;
            }
            java.math.BigDecimal signed = settlement.direction() == SettlementDirection.REFUND
                    ? settlement.amount().negate() : settlement.amount();
            String moneyId = settlement.redemptionId() + "-MONEY";
            out.add(RecordData.of("MONEY_ENTRY", moneyId, new MoneyEntry(
                    moneyId, aftersaleId, signed, operation.requestKey(), null)));
        }
        return out.build();
    }

    private String sourceLineId(AftersaleResult.StockEntryView entry, AftersaleResult.Prepared prepared) {
        if (entry.transferKey() != null && !entry.transferKey().isBlank()) {
            return entry.transferKey();
        }
        for (Replacement replacement : prepared.replacements()) {
            if (replacement.barcode().equals(entry.barcode())) {
                return replacement.lineId();
            }
        }
        for (Redemption redemption : prepared.redemptions()) {
            if (redemption.originalBarcode().equals(entry.barcode())) {
                return redemption.redemptionId();
            }
        }
        throw new IllegalArgumentException("STOCK_ENTRY has no source line: " + entry.id());
    }
}
