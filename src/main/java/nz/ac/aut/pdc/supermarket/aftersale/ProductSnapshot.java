package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.util.Objects;

/** Minimal read-only replacement-product snapshot for IF-06.
 *
 * 替换品的只读快照（IF-06/商品读取的 B 侧最小视图）。 */
public record ProductSnapshot(String name, BigDecimal unitPrice) {

    public ProductSnapshot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }
}
