package nz.ac.aut.pdc.shared;

import java.util.*;

/** Only new records and changed fields. No baseline snapshot in submission objects. */
public record ChangeSet(List<RecordData> inserts, List<Patch> patches, List<Removal> removals) {
    public ChangeSet { inserts = List.copyOf(inserts); patches = List.copyOf(patches); removals = List.copyOf(removals); }
    public ChangeSet(List<RecordData> inserts, List<Patch> patches) { this(inserts, patches, List.of()); }
    /** Operation-scoped: receipt correction lines or a complete B-validated aftersale draft group. */
    public record Removal(String table, String id, long expectedVersion) {
        public String key() { return table + ":" + id; }
    }
    public record Patch(String table, String id, long expectedVersion, String changedFields) {
        public Patch {
            if (expectedVersion < 1 || !JsonCodec.tree(changedFields).isObject())
                throw new IllegalArgumentException("Invalid patch");
        }
        public String key() { return table + ":" + id; }
    }
    public static ChangeSet empty() { return new ChangeSet(List.of(), List.of()); }
    public static final class Builder {
        private final Map<String, RecordData> inserts = new LinkedHashMap<>();
        private final Map<String, Patch> patches = new LinkedHashMap<>();
        private final Map<String, Removal> removals = new LinkedHashMap<>();
        public Builder add(RecordData record) {
            if (patches.containsKey(record.key()) || removals.containsKey(record.key())) throw new IllegalArgumentException("EVENT_CONFLICT");
            RecordData old = inserts.putIfAbsent(record.key(), record);
            if (old != null && !old.equals(record)) throw new IllegalArgumentException("EVENT_CONFLICT");
            return this;
        }
        /** Caller forms one final patch per existing record, never cumulative overwrite patches. */
        public Builder patch(Patch patch) {
            if (inserts.containsKey(patch.key()) || removals.containsKey(patch.key())) throw new IllegalArgumentException("EVENT_CONFLICT");
            Patch old = patches.putIfAbsent(patch.key(), patch);
            if (old != null && !old.equals(patch)) throw new IllegalArgumentException("EVENT_CONFLICT");
            return this;
        }
        public Builder remove(Removal removal) {
            if (inserts.containsKey(removal.key()) || patches.containsKey(removal.key())) throw new IllegalArgumentException("EVENT_CONFLICT");
            Removal old = removals.putIfAbsent(removal.key(), removal);
            if (old != null && !old.equals(removal)) throw new IllegalArgumentException("EVENT_CONFLICT");
            return this;
        }
        public Builder merge(ChangeSet delta) { delta.inserts.forEach(this::add); delta.patches.forEach(this::patch); delta.removals.forEach(this::remove); return this; }
        public ChangeSet build() { return new ChangeSet(new ArrayList<>(inserts.values()), new ArrayList<>(patches.values()), new ArrayList<>(removals.values())); }
    }
}
