package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;

/**
 * 认证/身份解析结果，区分成功、业务拒绝与技术失败三态。
 *
 * <p>对应共享规范 v1.02 §3.1 的 IF-01：成功返回可信会话；失败区分
 * {@code INVALID_CREDENTIALS} 与 {@code AUTH_TECHNICAL_FAILURE}。</p>
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.Rejected, AuthResult.Failed {

    /** 登录/解析成功：可信会话与解析后的身份。 */
    record Success(SessionRef session, String accountId, Role role) implements AuthResult {
        public Success {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(role, "role");
        }
    }

    /** 业务拒绝：凭证错误、会话无效、未登录等。 */
    record Rejected(String code, String message) implements AuthResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** 技术失败：凭证读取/校验故障，不代表用户凭证错误。 */
    record Failed(String code, String message) implements AuthResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
