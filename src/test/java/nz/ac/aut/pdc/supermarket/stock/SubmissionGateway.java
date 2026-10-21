package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/**
 * 历史捕获式测试载荷；非生产提交入口，生产仅用 shared.SubmissionPort。
 *
 * <p>正式实现由 A 维护；本骨架用捕获式替身验证"只提交一次、候选正确"，
 * 不写 JSON、不代表真实原子提交。IF-05 的三态结果用嵌套类型表达。</p>
 */
public interface SubmissionGateway {

    SubmitOutcome submit(SubmissionRequest request);

    /** 一次业务请求的提交载荷（只带本次增量，不带整快照）。 */
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

    /** 提交结果三态；只有 Success 表示已提交。 */
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
