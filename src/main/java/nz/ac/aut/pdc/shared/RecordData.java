package nz.ac.aut.pdc.shared;

import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;

/** Immutable JSON object payload. Text storage prevents leaking mutable Jackson nodes. */
public record RecordData(String table, String id, long version, String json) {
    public RecordData {
        if (table == null || table.isBlank() || id == null || id.isBlank() || version < 1)
            throw new IllegalArgumentException("Record key/version required");
        Objects.requireNonNull(json);
    }
    public String key() { return table + ":" + id; }
    public JsonNode data() { return JsonCodec.tree(json); }
    public <T> T as(Class<T> type) { return JsonCodec.read(json, type); }
    public static RecordData of(String table, String id, Object value) {
        return new RecordData(table, id, 1, JsonCodec.write(value));
    }
}
