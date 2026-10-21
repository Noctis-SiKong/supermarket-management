package nz.ac.aut.pdc.shared;

import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Internal coordinator handle, never a CUI/query response. No credentials in this view. */
public final class WorkingCopy {
    private final String id;
    private final long baseLast;
    private final Map<String, RecordData> records;
    public WorkingCopy(String id, long baseLast, Collection<RecordData> records) {
        this.id = Objects.requireNonNull(id); this.baseLast = baseLast;
        Map<String, RecordData> copy = new LinkedHashMap<>();
        records.forEach(r -> { if (copy.put(r.key(), r) != null) throw new IllegalArgumentException("Duplicate record"); });
        this.records = Collections.unmodifiableMap(copy);
    }
    public String workingCopyId() { return id; }
    public long baseLast() { return baseLast; }
    public Optional<RecordData> find(String table, String id) { return Optional.ofNullable(records.get(table + ":" + id)); }
    public List<RecordData> records(String table) { return records.values().stream().filter(r -> r.table().equals(table)).toList(); }
    public List<RecordData> allRecords() { return List.copyOf(records.values()); }
    public WorkingCopy overlay(ChangeSet changes) {
        Map<String, RecordData> out = new LinkedHashMap<>(records);
        Set<String> seen = new HashSet<>();
        for (RecordData r : changes.inserts()) {
            if (!seen.add(r.key()) || out.putIfAbsent(r.key(), r) != null) throw new IllegalArgumentException("EVENT_CONFLICT");
        }
        for (ChangeSet.Patch p : changes.patches()) {
            RecordData old = out.get(p.key());
            if (!seen.add(p.key()) || old == null || old.version() != p.expectedVersion())
                throw new IllegalArgumentException("STALE_WORKING_COPY");
            ObjectNode data = (ObjectNode) old.data();
            pFields(p).forEachRemaining(e -> data.set(e.getKey(), e.getValue()));
            out.put(p.key(), new RecordData(p.table(), p.id(), old.version() + 1, JsonCodec.write(data)));
        }
        for (ChangeSet.Removal r : changes.removals()) {
            RecordData old = out.get(r.key());
            if (!seen.add(r.key()) || old == null || old.version() != r.expectedVersion()) throw new IllegalArgumentException("STALE_WORKING_COPY");
            out.remove(r.key());
        }
        return new WorkingCopy(id, baseLast, out.values());
    }
    private static Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> pFields(ChangeSet.Patch p) {
        return JsonCodec.tree(p.changedFields()).properties().iterator();
    }
}
