package nz.ac.aut.pdc.supermarket.stock;

/** 损耗来源：店内现货报损，或顾客交回（顾客交回后续单独记录器处理）。 */
public enum LossOrigin {
    STORE,
    CUSTOMER_RETURN
}
