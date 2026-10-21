package nz.ac.aut.pdc.supermarket.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地认证实现：内存会话表 + 只读凭证端口。
 *
 * <p>职责边界：只做认证/解析，不写业务数据、不调用业务提交入口。
 * 正式凭证来源由 A 提供只读视图，通过 {@link CredentialStore} 适配。</p>
 */
public final class LocalAuthService implements AuthService {

    private final CredentialStore credentialStore;
    private final PasswordHasher passwordHasher;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public LocalAuthService(CredentialStore credentialStore, PasswordHasher passwordHasher) {
        this.credentialStore = credentialStore;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public AuthResult login(String username, char[] password) {
        if (username == null || username.isBlank() || password == null) {
            return new AuthResult.Rejected("INVALID_CREDENTIALS", "用户名或密码不能为空");
        }
        try {
            Optional<CredentialStore.StoredCredential> credential =
                    credentialStore.findByUsername(username.trim());
            if (credential.isEmpty()) {
                return new AuthResult.Rejected("INVALID_CREDENTIALS", "用户名或密码不正确");
            }
            CredentialStore.StoredCredential stored = credential.get();
            if (!passwordHasher.matches(password, stored.passwordHash())) {
                return new AuthResult.Rejected("INVALID_CREDENTIALS", "用户名或密码不正确");
            }
            String token = UUID.randomUUID().toString();
            SessionRef session = new SessionRef(token);
            sessions.put(token, new SessionState(session, stored.accountId(), stored.role()));
            return new AuthResult.Success(session, stored.accountId(), stored.role());
        } catch (RuntimeException ex) {
            return new AuthResult.Failed("AUTH_TECHNICAL_FAILURE", "认证服务暂时不可用");
        }
    }

    @Override
    public AuthResult resolve(SessionRef session) {
        if (session == null) {
            return new AuthResult.Rejected("AUTH_REQUIRED", "需要登录");
        }
        SessionState state = sessions.get(session.token());
        if (state == null) {
            return new AuthResult.Rejected("AUTH_REQUIRED", "会话无效，请重新登录");
        }
        return new AuthResult.Success(state.session(), state.accountId(), state.role());
    }

    private record SessionState(SessionRef session, String accountId, Role role) {
    }
}
