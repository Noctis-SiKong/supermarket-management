package nz.ac.aut.pdc.supermarket.fixture;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/**
 * 固定 ID/时间替身（测试用途，非生产实现）。
 *
 * <p>替代 A 的 IF-03：输出可预测的 ID 与固定业务时间，便于断言。</p>
 */
public final class FakeIdGenerator implements StockPorts.IdGenerator {

    private final AtomicInteger sequence = new AtomicInteger();
    private final Instant fixedTime;

    public FakeIdGenerator(Instant fixedTime) {
        this.fixedTime = fixedTime;
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "-" + sequence.incrementAndGet();
    }

    @Override
    public Instant now() {
        return fixedTime;
    }
}
