package nz.ac.aut.pdc.supermarket.reversal;

import java.util.Objects;

/** Historical reversal command: target redemption, reason and original request key.
 *
 * 冲销命令（骨架）：目标核销 + 原因 + 请求键。 */
public record ReversalCommand(String requestKey, String redemptionId, String reason) {

    public ReversalCommand {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("requestKey must not be blank");
        }
        if (redemptionId == null || redemptionId.isBlank()) {
            throw new IllegalArgumentException("redemptionId must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        Objects.requireNonNull(reason, "reason");
    }
}
