package nz.ac.aut.pdc.supermarket.stock;

import java.util.Collection;
import java.util.List;

/**
 * 库存核对能力的公开契约（IF-07）。
 *
 * <p>提供方为 B；消费方为各业务协调者（补货、报损、销售、售后、冲销）。
 * 只读余额与候选变动分离，接口不提供任何保存/提交入口。</p>
 */
public interface StockOperations {

    /** 读取指定条码集合的商品/位置余额（不按收货单计算来源可用量）。 */
    List<StockBalance> balances(StockPorts.WorkingCopyView workingCopy, Collection<String> barcodes);

    /**
     * 核对本次完整变动组并生成候选流水。
     *
     * @param workingCopy    同一操作的只读工作副本
     * @param overlayEntries 调用前已合并的候选流水（只读叠加视图）
     * @param changes        尚未并入 overlay 的完整变动组
     */
    StockCheckResult check(
            StockPorts.WorkingCopyView workingCopy,
            List<StockEntry> overlayEntries,
            List<StockChangeLine> changes);
}
