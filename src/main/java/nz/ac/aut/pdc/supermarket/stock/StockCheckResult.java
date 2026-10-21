package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Objects;

/**
 * IF-07 check 的结果：PREPARED / REJECTED / FAILED 三态。
 *
 * <p>PREPARED 只表示候选已准备好，不是业务提交成功，也不分配 commitOrdinal。</p>
 */
public sealed interface StockCheckResult
        permits StockCheckResult.Prepared, StockCheckResult.Rejected, StockCheckResult.Failed {

    /** 候选流水与变动后余额；由业务协调者合并进 overlay 后再提交。 */
    record Prepared(List<StockEntry> entries, List<StockBalance> balancesAfter) implements StockCheckResult {
        public Prepared {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            balancesAfter = List.copyOf(Objects.requireNonNull(balancesAfter, "balancesAfter"));
        }
    }

    /** 业务拒绝：库存不足、数量非法、转移不守恒、引用无效等。 */
    record Rejected(String code, String field, String message) implements StockCheckResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** 技术失败：内部/依赖读取异常，不代表业务拒绝。 */
    record Failed(String code, String message) implements StockCheckResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
