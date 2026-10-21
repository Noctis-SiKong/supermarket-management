package nz.ac.aut.pdc.supermarket.fixture;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Fixed ID/time test double for IF-03; deterministic values support assertions, never production.
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
