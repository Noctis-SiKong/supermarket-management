package nz.ac.aut.pdc.supermarket.aftersale;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** 恢复/快照期的 B 售后结构完整性检查（只检查 B 拥有的表与关联）。 */
public final class AftersaleIntegrityValidator implements JsonStore.IntegrityValidator {

    @Override
    public void validate(WorkingCopy view) throws IOException {
        Set<String> documents = new HashSet<>();
        Set<String> redemptions = new HashSet<>();
        for (RecordData record : view.records("DOCUMENT")) {
            documents.add(record.id());
        }
        for (RecordData record : view.records("AFTERSALE")) {
            if (!documents.contains(record.id())) {
                throw new IOException("AFTERSALE has no document: " + record.id());
            }
        }
        for (RecordData record : view.records("REDEMPTION")) {
            Redemption redemption = record.as(Redemption.class);
            if (!documents.contains(redemption.aftersaleId())) {
                throw new IOException("REDEMPTION has no aftersale document: " + record.id());
            }
            if (!redemptions.add(record.id())) {
                throw new IOException("Duplicate REDEMPTION: " + record.id());
            }
            if (redemption.quantity() <= 0 || redemption.amount().signum() < 0) {
                throw new IOException("Invalid REDEMPTION quantity/amount: " + record.id());
            }
            if (redemption.sourceLineId() == null || redemption.sourceLineId().isBlank()) {
                throw new IOException("REDEMPTION without source line: " + record.id());
            }
        }
        for (RecordData record : view.records("REPLACEMENT")) {
            Replacement replacement = record.as(Replacement.class);
            if (!documents.contains(replacement.aftersaleId())) {
                throw new IOException("REPLACEMENT has no aftersale document: " + record.id());
            }
            if (replacement.redemptionId() != null
                    && !redemptions.contains(replacement.redemptionId())) {
                throw new IOException("REPLACEMENT has no redemption: " + record.id());
            }
        }
        for (RecordData record : view.records("MONEY_ENTRY")) {
            MoneyEntry money = record.as(MoneyEntry.class);
            if (!documents.contains(money.documentId())) {
                throw new IOException("MONEY_ENTRY has no document: " + record.id());
            }
            if (money.amountSigned() == null || money.amountSigned().signum() == 0) {
                throw new IOException("Invalid MONEY_ENTRY amount: " + record.id());
            }
        }
    }
}
