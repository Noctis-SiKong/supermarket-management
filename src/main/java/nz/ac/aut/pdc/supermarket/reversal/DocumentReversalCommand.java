package nz.ac.aut.pdc.supermarket.reversal;

import java.util.Objects;

/** Whole-document reversal request: confirmed target and authorized reason.
 *
 * 整单冲销请求：目标已确认单据 + 授权原因。 */
public record DocumentReversalCommand(
        String targetDocumentId, String reason, String requestKey) {

    public DocumentReversalCommand {
        if (targetDocumentId == null || targetDocumentId.isBlank()) {
            throw new IllegalArgumentException("targetDocumentId must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        Objects.requireNonNull(requestKey, "requestKey");
    }
}
