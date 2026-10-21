package nz.ac.aut.pdc.shared;

public record DocEvent(String eventId, String documentId, long version, String action, String actorId,
                       String occurredAt, String detailSnapshot) {}
