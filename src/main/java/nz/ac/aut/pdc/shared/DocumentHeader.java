package nz.ac.aut.pdc.shared;

public record DocumentHeader(String documentId, String kind, String status, String requestKey,
    String requestFingerprint, String createdBy, String createdAt, String confirmedBy,
    String confirmedAt, String reason) {}
