package nz.ac.aut.pdc.shared;

/** Original intent for a B-validated aftersale draft group deletion, not an arbitrary table delete. */
public record DraftDeletion(String documentId, long expectedVersion) {
    public static final String OPERATION = "AFTERSALE_DRAFT_DELETE";
    public DraftDeletion(String documentId) { this(documentId,0); }
    public DraftDeletion {
        if (documentId == null || documentId.isBlank() || expectedVersion < 0)
            throw new IllegalArgumentException("Target draft/version required");
    }
}
