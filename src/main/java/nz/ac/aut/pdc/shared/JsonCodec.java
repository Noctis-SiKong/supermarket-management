package nz.ac.aut.pdc.shared;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class JsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private JsonCodec() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON value", e); }
    }
    public static <T> T read(String value, Class<T> type) {
        try { return MAPPER.readValue(value, type); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON structure", e); }
    }
    public static JsonNode tree(String value) { return read(value, JsonNode.class); }
    public static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            TreeSet<String> names = new TreeSet<>(); node.fieldNames().forEachRemaining(names::add);
            names.forEach(n -> out.set(n, canonical(node.get(n)))); return out;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode(); node.forEach(n -> out.add(canonical(n))); return out;
        }
        if (node.isNumber()) return new TextNode(node.decimalValue().stripTrailingZeros().toPlainString());
        return node.deepCopy();
    }
    public static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String fingerprint(String operation, String actor, BusinessRequest request) {
        return hash(write(canonical(tree(write(Map.of("protocol", "1", "operation", operation,
            "actor", actor, "request", tree(request.json())))))).getBytes(StandardCharsets.UTF_8));
    }
    public record BusinessRequest(String json) {
        public BusinessRequest {
            if (!tree(json).isObject()) throw new IllegalArgumentException("Original request must be an object");
        }
        public static BusinessRequest of(Object value) { return new BusinessRequest(write(value)); }
        public <T> T as(Class<T> type) { return read(json, type); }
    }
}
