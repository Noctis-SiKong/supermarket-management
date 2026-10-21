package nz.ac.aut.pdc.supermarket.i18n;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.stock.Position;
import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.Effect;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.aftersale.SettlementDirection;
import nz.ac.aut.pdc.supermarket.promotion.Promotion;
import org.junit.Test;

/**
 * Guards the display vocabulary: every value the CUI can print as a label and every
 * projected field name must resolve in both languages.
 * 守护展示词汇：CUI 可能打印的每个标签值与投影字段名，在两种语言下都必须可解析。
 */
public class TextCoverageTest {
    private static final List<String> STATUSES = List.of(
        "DRAFT", "PENDING_REVIEW", "NEEDS_CORRECTION", "CONFIRMED", "APPROVED", "CANCELLED", "VOIDED");
    private static final List<String> ACTIONS = List.of(
        "CREATE", "CORRECT", "REVIEW", "SALE", "CONFIRM", "CANCEL_AFTERSALE", "READ_FOR_AFTERSALE");

    @Test public void everyDisplayedEnumValueHasALabel() {
        List<Enum<?>> values = new ArrayList<>();
        values.addAll(List.of(Role.values()));
        values.addAll(List.of(Position.values()));
        values.addAll(List.of(AftersaleReasonKind.values()));
        values.addAll(List.of(Effect.values()));
        values.addAll(List.of(RedemptionKind.values()));
        values.addAll(List.of(SettlementDirection.values()));
        values.addAll(List.of(Promotion.Kind.values()));
        for (String lang : List.of("en", "zh")) {
            Texts texts = new Texts(lang);
            for (Enum<?> value : values) assertLabel(texts, value.name(), lang);
            for (String status : STATUSES) assertLabel(texts, status, lang);
            for (String action : ACTIONS) assertLabel(texts, action, lang);
        }
    }

    @Test public void projectedFieldsHaveLabels() {
        Map<String, String> projection = new LinkedHashMap<>();
        projection.put("productId", "P-1");
        projection.put("eventKey", "RECEIPT:1");
        projection.put("commitOrdinal", "1");
        projection.put("replacementLineId", "L-1");
        for (String lang : List.of("en", "zh")) {
            String rendered = Texts.data(projection).render(new Texts(lang));
            assertFalse(rendered, rendered.contains(Texts.UNAVAILABLE));
            for (String value : projection.values()) assertTrue(rendered, rendered.contains(value));
        }
    }

    private void assertLabel(Texts texts, String value, String language) {
        assertFalse(language + " label." + value, texts.label(value).contains(Texts.UNAVAILABLE));
    }
}
