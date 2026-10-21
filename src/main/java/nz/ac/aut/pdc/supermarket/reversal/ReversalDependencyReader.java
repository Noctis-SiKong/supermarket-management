package nz.ac.aut.pdc.supermarket.reversal;

import java.util.List;

/** 后续引用/依赖只读端口（D1 依赖检查，当前用替身）。 */
public interface ReversalDependencyReader {

    /** 返回引用该核销的后续记录 ID；非空即表示不能直接冲销。 */
    List<String> dependentRecordIds(String redemptionId);
}
