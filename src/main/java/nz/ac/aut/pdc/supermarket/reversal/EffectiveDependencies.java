package nz.ac.aut.pdc.supermarket.reversal;

import java.util.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.supermarket.aftersale.*;

/** Spec D1: exclude entire authorized, fully paired groups, never infer depletion from net stock. */
public final class EffectiveDependencies {
    private EffectiveDependencies() {}
    public static String blocker(WorkingCopy wc,String target) {
        Set<String> lines=new HashSet<>();
        for(var r:wc.records("DOC_LINE")) if(r.as(DocumentLine.class).documentId().equals(target)) lines.add(r.id());
        Set<String> excluded=fullyReversed(wc);
        Set<String> targetLosses=new HashSet<>();
        for(var r:wc.records("LOSS_RECORD")) if(lines.contains(r.data().path("sourceLineId").asText()))targetLosses.add(r.id());
        var targetCorrection=wc.find("LOSS_CORRECTION",target);
        for(var r:wc.records("LOSS_CORRECTION")) {
            if(excluded.contains(r.id()) || wc.find("DOCUMENT",r.id()).orElseThrow().data().path("status").asText().equals("VOIDED"))continue;
            if(targetLosses.contains(r.data().path("lossId").asText()))return "Effective loss correction depends on loss";
            if(targetCorrection.isPresent() && r.data().path("lossId").equals(targetCorrection.get().data().path("lossId")) && r.data().path("correctionVersion").asLong()>targetCorrection.get().data().path("correctionVersion").asLong())return "Later loss correction depends on earlier version";
        }
        for(var r:wc.records("REDEMPTION")) {
            var redemption=r.as(Redemption.class);
            if(redemption.effect()==Effect.CONSUME && lines.contains(redemption.originalSaleLineId()) && !excluded.contains(redemption.aftersaleId()))
                return "Effective aftersale still depends on sale";
        }
        for(var r:wc.records("STOCK_ENTRY")) {
            var source=r.as(StockEntry.class);
            if(!lines.contains(source.sourceLineId()) || source.reverseOfId()!=null) continue;
            String barcode=wc.find("DOC_LINE",source.sourceLineId()).orElseThrow().as(DocumentLine.class).barcode();
            for(var other:wc.records("STOCK_ENTRY")) {
                var later=other.as(StockEntry.class);
                var line=wc.find("DOC_LINE",later.sourceLineId()).orElseThrow().as(DocumentLine.class);
                if(later.reverseOfId()==null && later.quantityDelta()<0 && later.commitOrdinal()>source.commitOrdinal()
                    && later.position()==source.position() && line.barcode().equals(barcode) && !excluded.contains(line.documentId()))
                    return "Later effective negative stock movement: "+line.documentId();
            }
        }
        return null;
    }
    public static Set<String> fullyReversed(WorkingCopy wc) {
        Set<String> excluded=new HashSet<>();
        for(var reversal:wc.records("REVERSAL")) {
            String target=reversal.data().path("target_document_id").asText();
            var targetRecord=wc.find("DOCUMENT",target);
            var reverseHeader=wc.find("DOCUMENT",reversal.id());
            if(targetRecord.isEmpty() || !targetRecord.get().data().path("status").asText().equals("VOIDED") || reverseHeader.isEmpty()
                || !reverseHeader.get().data().path("status").asText().equals("CONFIRMED")) continue;
            Set<String> targetLines=new HashSet<>(), reverseLines=new HashSet<>();
            for(var l:wc.records("DOC_LINE")) {
                var line=l.as(DocumentLine.class);
                if(line.documentId().equals(target)) targetLines.add(l.id());
                if(line.documentId().equals(reversal.id())) reverseLines.add(l.id());
            }
            boolean complete=true;
            for(var row:wc.records("STOCK_ENTRY")) {
                var original=row.as(StockEntry.class);
                if(original.reverseOfId()!=null || !targetLines.contains(original.sourceLineId())) continue;
                long pairs=wc.records("STOCK_ENTRY").stream().map(r -> r.as(StockEntry.class)).filter(r -> row.id().equals(r.reverseOfId())
                    && reverseLines.contains(r.sourceLineId()) && r.position()==original.position() && r.quantityDelta()==-original.quantityDelta()
                    && wc.find("DOC_LINE",r.sourceLineId()).orElseThrow().as(DocumentLine.class).barcode().equals(wc.find("DOC_LINE",original.sourceLineId()).orElseThrow().as(DocumentLine.class).barcode())).count();
                complete &= pairs==1;
            }
            for(var row:wc.records("MONEY_ENTRY")) {
                var original=row.as(MoneyEntry.class);
                if(original.reverseOfId()!=null || !original.documentId().equals(target)) continue;
                complete &= wc.records("MONEY_ENTRY").stream().map(r -> r.as(MoneyEntry.class)).filter(r -> row.id().equals(r.reverseOfId()) && r.documentId().equals(reversal.id()) && r.amountSigned().add(original.amountSigned()).signum()==0).count()==1;
            }
            for(var row:wc.records("REDEMPTION")) {
                var original=row.as(Redemption.class);
                if(original.effect()!=Effect.CONSUME || !original.aftersaleId().equals(target)) continue;
                complete &= wc.records("REDEMPTION").stream().map(r -> r.as(Redemption.class)).filter(r -> row.id().equals(r.reverseOfId()) && reverseLines.contains(r.sourceLineId()) && r.effect()==Effect.RELEASE && r.quantity()==original.quantity() && r.amount().compareTo(original.amount())==0).count()==1;
            }
            if(complete) { excluded.add(target); excluded.add(reversal.id()); }
        }
        return Set.copyOf(excluded);
    }
}
