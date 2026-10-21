package nz.ac.aut.pdc.supermarket.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import nz.ac.aut.pdc.shared.JsonCodec;

/** Process-local presentation state only; never passed to business requests.
  */
public final class Texts {
    public static final String UNAVAILABLE = "TEXT_UNAVAILABLE Text unavailable.";
    private final Map<String,String> english;
    private final Map<String,String> chinese;
    private String language;

    public Texts(String language) { this(language, load("en"), load("zh")); }
    /** Explicit resource injection supports observable missing-text tests.
      */
    public Texts(String language, Map<String,String> english, Map<String,String> chinese) {
        this.english = Map.copyOf(english); this.chinese = Map.copyOf(chinese); select(language);
    }
    public void select(String value) {
        if (!"en".equals(value) && !"zh".equals(value)) throw new IllegalArgumentException("INVALID_LANGUAGE");
        language = value;
    }
    public String language() { return language; }
    public Set<String> keys() { return english.keySet(); }
    public String text(String key, Object... args) {
        String value = "zh".equals(language) ? chinese.get(key) : null;
        if (value == null || value.isBlank()) value = english.get(key);
        if (value == null || value.isBlank()) return UNAVAILABLE;
        Object[] rendered = Arrays.stream(args).map(a -> a instanceof Display d ? d.render(this) : a).toArray();
        try { return String.format(Locale.ROOT, value, rendered); }
        catch (IllegalFormatException ex) { return UNAVAILABLE; }
    }
    public String error(String code) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]*")) return text("input.invalid");
        return code + ": " + text("error." + code);
    }
    public String inputError(String message) {
        // Match known system validation strings only, never business/user data.
        for (String key : english.keySet()) if (key.startsWith("validation.") &&
                (Objects.equals(message, chinese.get(key)) || Objects.equals(message, english.get(key)))) return text(key);
        if (message != null && message.matches("[A-Z][A-Z0-9_]*")) return error(message);
        return text("input.invalid");
    }
    public String label(String value) { return text("label." + value) + " (" + value + ")"; }
    @FunctionalInterface public interface Display { String render(Texts texts); }
    public static Display message(String key, Object... args) {
        Object[] copy = args.clone(); return texts -> texts.text(key, copy);
    }
    /** Freeze an authorised projection and render labelled fields, not JSON/toString.
      */
    public static Display data(Object value) {
        JsonNode frozen = JsonCodec.tree(JsonCodec.write(value));
        return texts -> texts.fields(frozen, "", "");
    }
    private String fields(JsonNode node, String indent, String name) {
        if (node.isNull()) return text("data.none");
        if (node.isObject()) {
            if (node.isEmpty()) return text("data.none");
            StringBuilder out = new StringBuilder();
            node.fields().forEachRemaining(entry -> out.append(indent).append(text("field." + entry.getKey())).append(": ")
                .append(entry.getValue().isContainerNode() ? "\n" : "")
                .append(fields(entry.getValue(), indent + "  ", entry.getKey())).append("\n"));
            return out.toString().stripTrailing();
        }
        if (node.isArray()) {
            if (node.isEmpty()) return text("data.none");
            StringBuilder out = new StringBuilder(); int index = 0;
            for (JsonNode child : node) out.append(indent).append(++index).append(") ").append(fields(child, indent + "  ", name)).append("\n");
            return out.toString().stripTrailing();
        }
        if (node.isBoolean()) return text(node.booleanValue() ? "data.true" : "data.false");
        String value = node.asText();
        if (Set.of("detailSnapshot","promotionSnapshot","beforeAfterSnapshot").contains(name)) {
            try { return fields(JsonCodec.tree(value),indent,""); }
            catch (IllegalArgumentException ex) { return value; }
        }
        if (Set.of("kind","status","position","fromPosition","toPosition","direction","reasonKind","role","eventType","documentKind","settlementDirection","lineKind","action","origin","dispositionState").contains(name)) return label(value);
        return value;
    }
    private static Map<String,String> load(String language) {
        Properties p = new Properties();
        try (InputStream stream = Texts.class.getResourceAsStream(language + ".properties")) {
            if (stream == null) return Map.of();
            p.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) { return Map.of(); }
        Map<String,String> values = new HashMap<>(); p.forEach((k,v) -> values.put((String)k, (String)v)); return values;
    }
}
