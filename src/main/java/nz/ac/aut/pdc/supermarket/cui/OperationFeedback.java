package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;

/** 菜单操作给用户的统一反馈（成功/失败 + 可读消息）。 */
public record OperationFeedback(boolean success, String message) {

    public OperationFeedback {
        Objects.requireNonNull(message, "message");
    }

    public static OperationFeedback ok(String message) {
        return new OperationFeedback(true, message);
    }

    public static OperationFeedback fail(String message) {
        return new OperationFeedback(false, message);
    }
}
