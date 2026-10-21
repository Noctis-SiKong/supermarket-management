package nz.ac.aut.pdc.infrastructure;

import com.fasterxml.jackson.databind.node.ObjectNode;
import nz.ac.aut.pdc.shared.*;
import java.time.Instant;
import java.util.*;

/** Explicit server-managed field assignment. Historical and user-input dates are never stripped. */
public final class SubmissionTime {
    private SubmissionTime() {}
    public static ChangeSet assign(WorkingCopy baseline, ChangeSet changes, Instant acceptedAt) {
        String time = acceptedAt.toString();
        List<RecordData> inserts = new ArrayList<>();
        for (RecordData r : changes.inserts()) {
            ObjectNode data = (ObjectNode) r.data();
            switch (r.table()) {
                case "DOCUMENT" -> {
                    data.put("createdAt", time);
                    if (Set.of("CONFIRMED", "APPROVED").contains(data.path("status").asText())
                        || data.hasNonNull("confirmedAt")) data.put("confirmedAt", time);
                }
                case "DOC_EVENT" -> data.put("occurredAt", time);
                case "RECEIPT_REVIEW" -> data.put("reviewedAt", time);
                default -> { }
            }
            // Keep untouched payload bytes stable, including BigDecimal representation.
            boolean managed = Set.of("DOCUMENT", "DOC_EVENT", "RECEIPT_REVIEW").contains(r.table());
            inserts.add(managed ? new RecordData(r.table(),r.id(),r.version(),JsonCodec.write(data)) : r);
        }
        List<ChangeSet.Patch> patches = new ArrayList<>();
        for (ChangeSet.Patch p : changes.patches()) {
            if (!p.table().equals("DOCUMENT")) { patches.add(p); continue; }
            RecordData old = baseline.find(p.table(),p.id()).orElseThrow(() -> new IllegalArgumentException("REFERENCE_INVALID"));
            ObjectNode fields = (ObjectNode) JsonCodec.tree(p.changedFields());
            if (fields.has("createdAt") && !fields.get("createdAt").equals(old.data().path("createdAt")))
                throw new IllegalArgumentException("INVALID_INPUT");
            if (old.data().hasNonNull("confirmedAt")) {
                if (fields.has("confirmedAt") && !fields.get("confirmedAt").equals(old.data().get("confirmedAt")))
                    throw new IllegalArgumentException("INVALID_INPUT");
            } else if (fields.has("confirmedAt") || Set.of("CONFIRMED","APPROVED").contains(fields.path("status").asText())) {
                fields.put("confirmedAt", time);
            }
            patches.add(new ChangeSet.Patch(p.table(),p.id(),p.expectedVersion(),JsonCodec.write(fields)));
        }
        return new ChangeSet(inserts,patches,changes.removals());
    }
}
