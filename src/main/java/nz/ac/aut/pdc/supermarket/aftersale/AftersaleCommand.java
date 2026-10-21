package nz.ac.aut.pdc.supermarket.aftersale;

import java.util.List;
import java.util.Objects;

/** Complete aftersale intent under CR07-10: no manager eligibility exceptions; reasons and facts are per line.
 *
 *
 * 售后命令：一次业务意图的完整输入（骨架）。
 *
 * <p>CR07-10：单头不再保留店长资格授权/例外字段；原因与资格事实逐明细记录。</p>
 */
public record AftersaleCommand(
        String requestKey,
        String customerVoucher,
        List<AftersaleCommandLine> lines) {

    public AftersaleCommand {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("requestKey must not be blank");
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
    }
}
