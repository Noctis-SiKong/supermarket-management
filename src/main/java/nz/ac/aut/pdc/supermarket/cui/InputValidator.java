package nz.ac.aut.pdc.supermarket.cui;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * CUI 输入格式与基础边界校验。
 *
 * <p>只做"能否解析/格式/基础范围"的检查；业务规则（库存、额度、权限）
 * 由领域服务复核，二者不互相替代。</p>
 */
public final class InputValidator {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 9999;
    public static final BigDecimal MAX_MONEY = new BigDecimal("999999.99");

    public int parseQuantity(String raw) throws InvalidInputException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("数量不能为空");
        }
        final int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new InvalidInputException("数量必须是整数");
        }
        if (value < MIN_QUANTITY || value > MAX_QUANTITY) {
            throw new InvalidInputException("数量必须在 1–9999 之间");
        }
        return value;
    }

    public BigDecimal parseMoney(String raw) throws InvalidInputException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("金额不能为空");
        }
        final BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new InvalidInputException("金额格式不正确");
        }
        if (value.signum() < 0 || value.compareTo(MAX_MONEY) > 0) {
            throw new InvalidInputException("金额必须在 0–999999.99 之间");
        }
        if (value.scale() > 2) {
            throw new InvalidInputException("金额最多保留两位小数");
        }
        return value;
    }

    /** CUI 层可向用户展示的输入错误。 */
    public static final class InvalidInputException extends Exception {
        public InvalidInputException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
