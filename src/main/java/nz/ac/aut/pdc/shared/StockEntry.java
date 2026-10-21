package nz.ac.aut.pdc.shared;

/** Candidate ordinal is zero; IF-05 assigns the committed ordinal to the entire group. */
public record StockEntry(String stockEntryId, String sourceLineId, Position position, int quantityDelta,
    String eventKey, String receiptReviewId, String reverseOfId, String transferKey,
    @com.fasterxml.jackson.annotation.JsonProperty("commit_ordinal") long commitOrdinal) {
    public enum Position { S, W }
}
