package nz.ac.aut.pdc.supermarket.cui;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.supermarket.stock.StockPorts;

/** Adapt A IdClock stable entity IDs within one operation.
 *
 * 在一次 A 操作内由 A IdClock 生成稳定实体 ID 的适配器。 */
public final class SharedIdGenerator implements StockPorts.IdGenerator {

    private final IdClock idClock;
    private final String requestKey;
    private final AtomicInteger sequence = new AtomicInteger();

    public SharedIdGenerator(IdClock idClock, String requestKey) {
        this.idClock = Objects.requireNonNull(idClock, "idClock");
        this.requestKey = Objects.requireNonNull(requestKey, "requestKey");
    }

    @Override
    public String nextId(String prefix) {
        return idClock.entityId(requestKey, prefix + ":" + sequence.incrementAndGet());
    }

    @Override
    public Instant now() {
        return idClock.now();
    }
}
