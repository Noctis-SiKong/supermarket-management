package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/** Historical capturing test payload, not the production entry (shared.SubmissionPort). Verifies one submission and candidates without JSON or real atomicity; IF-05 three-state results are nested types.
 */
public interface SubmissionGateway {

    SubmitOutcome submit(SubmissionRequest request);

    /** One request's incremental submission payload, never the complete snapshot.
  */
    record SubmissionRequest(
            String requestKey,
            long baseLast,
            String operatorAccountId,
            List<StockEntry> stockEntries) {

        public SubmissionRequest {
            if (requestKey == null || requestKey.isBlank()) {
                throw new IllegalArgumentException("requestKey must not be blank");
            }
            if (operatorAccountId == null || operatorAccountId.isBlank()) {
                throw new IllegalArgumentException("operatorAccountId must not be blank");
            }
            stockEntries = List.copyOf(Objects.requireNonNull(stockEntries, "stockEntries"));
        }
    }

    /** Three-state submission result; only Success means committed.
  */
    sealed interface SubmitOutcome
            permits SubmitOutcome.Success, SubmitOutcome.Rejected, SubmitOutcome.Failed {

        record Success(String requestKey, long commitOrdinal) implements SubmitOutcome {
        }

        record Rejected(String code, String message) implements SubmitOutcome {
        }

        record Failed(String code, String message) implements SubmitOutcome {
        }
    }
}
