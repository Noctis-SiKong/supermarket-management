package nz.ac.aut.pdc.supermarket.aftersale;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.MoneyEntry;
import nz.ac.aut.pdc.shared.RecordData;
import nz.ac.aut.pdc.shared.WorkingCopy;

/** B-owned snapshot/recovery integrity validation limited to aftersale tables and associations.
 *
 * 恢复/快照期的 B 售后结构完整性检查（只检查 B 拥有的表与关联）。 */
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
            if (money.amountSigned() == null) {
                throw new IOException("Invalid MONEY_ENTRY amount: " + record.id());
            }
        }
        try {
            Set<String> released=new HashSet<>(),qualityPairs=new HashSet<>();
            for(var r:view.records("REDEMPTION")) {
                var value=r.as(Redemption.class);
                if(!r.id().equals(value.redemptionId()) || value.quantity()<1 || value.quantity()>9999) throw new IllegalArgumentException("Invalid redemption identity/quantity");
                var source=view.find("DOC_LINE",value.sourceLineId()).orElseThrow().as(nz.ac.aut.pdc.shared.DocumentLine.class);
                var original= nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess.originalLine(view,value.originalSaleLineId()).orElseThrow();
                if(!source.barcode().equals(value.originalBarcode()) || !original.barcode().equals(value.originalBarcode()) || source.quantity()!=value.quantity()) throw new IllegalArgumentException("Redemption source mismatch");
                if(value.effect()==Effect.CONSUME && !source.documentId().equals(value.aftersaleId())) throw new IllegalArgumentException("Consumed source belongs to another document");
                if(value.effect()==Effect.RELEASE) {
                    var consume=view.find("REDEMPTION",value.reverseOfId()).orElseThrow().as(Redemption.class);
                    if(!released.add(consume.redemptionId()) || consume.effect()!=Effect.CONSUME || consume.quantity()!=value.quantity() || consume.amount().compareTo(value.amount())!=0 || !consume.originalSaleLineId().equals(value.originalSaleLineId()) || !consume.aftersaleId().equals(value.aftersaleId())) throw new IllegalArgumentException("Invalid redemption release");
                }
            }
            for(var r:view.records("SALE_LINE")) {
                var line=new SharedSaleReader(view).readLine(r.id()).orElseThrow();
                new QuotaViewBuilder().build(line,new SharedQuotaLedgerReader(view).effectiveFor(r.id()));
            }
            for(var r:view.records("REPLACEMENT")) {
                var replacement=r.as(Replacement.class);
                var source=view.find("DOC_LINE",r.id()).orElseThrow().as(nz.ac.aut.pdc.shared.DocumentLine.class);
                var redemption=view.find("REDEMPTION",replacement.redemptionId()).orElseThrow().as(Redemption.class);
                if(!source.documentId().equals(replacement.aftersaleId()) || !source.barcode().equals(replacement.barcode()) || source.quantity()!=replacement.quantity()
                    || replacement.replacementAmount().compareTo(replacement.unitPriceSnapshot().multiply(java.math.BigDecimal.valueOf(replacement.quantity())))!=0)
                    throw new IllegalArgumentException("Replacement snapshot mismatch");
                if(replacement.qualityRedemptionId()!=null) {
                    if(!qualityPairs.add(replacement.qualityRedemptionId()) || new QualityPairValidator().validate(new SharedSaleReader(view).readLine(redemption.originalSaleLineId()).orElseThrow(),redemption,replacement).isPresent())
                        throw new IllegalArgumentException("Invalid quality pair");
                }
            }
            for(var document:view.records("AFTERSALE")) {
                var consumes=view.records("REDEMPTION").stream().map(r -> r.as(Redemption.class)).filter(r -> r.aftersaleId().equals(document.id()) && r.effect()==Effect.CONSUME).toList();
                if(consumes.isEmpty()) continue;
                java.math.BigDecimal expected=java.math.BigDecimal.ZERO;
                for(var consume:consumes) {
                    var replacement=view.records("REPLACEMENT").stream().map(r -> r.as(Replacement.class)).filter(r -> consume.redemptionId().equals(r.redemptionId())).findFirst().orElse(null);
                    if((consume.kind()==RedemptionKind.EXCHANGE)!=(replacement!=null))throw new IllegalArgumentException("Missing/unrelated replacement");
                    var settlement=new SettlementCalculator().calculate(consume,replacement);
                    if(settlement.direction()!=SettlementDirection.NONE)expected=expected.add(settlement.direction()==SettlementDirection.REFUND?settlement.amount().negate():settlement.amount());
                }
                var money=view.records("MONEY_ENTRY").stream().map(r -> r.as(MoneyEntry.class)).filter(r -> r.documentId().equals(document.id()) && r.reverseOfId()==null).map(MoneyEntry::amountSigned).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
                if(money.compareTo(expected)!=0)throw new IllegalArgumentException("Aftersale net money mismatch");
            }
            for(var r:view.records("LOSS_RECORD")) {
                var loss=r.as(nz.ac.aut.pdc.supermarket.stock.LossRecord.class);
                var source=view.find("DOC_LINE",loss.sourceLineId()).orElseThrow().as(nz.ac.aut.pdc.shared.DocumentLine.class);
                if(!loss.lossId().equals(r.id()) || !source.barcode().equals(loss.barcode()) || source.quantity()!=loss.quantity()) throw new IllegalArgumentException("Loss source mismatch");
            }
        } catch(RuntimeException e) {throw new IOException("Aftersale/loss recovery integrity invalid",e);}

    }
}
