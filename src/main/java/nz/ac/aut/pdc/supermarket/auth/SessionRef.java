package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;

/** Opaque AuthService session with package-visible constructor prevents caller-forged identity. Historical expiry is not implemented; see shared section 3.1.
 *
 *
 * 认证服务签发的会话引用（逻辑句柄）。
 *
 * <p>构造器包内可见，业务代码只能从 {@link AuthService} 获得实例，避免自行伪造可信身份。
 * 本骨架不实现会话过期；正式边界见《共享边界与接口规范》§3.1。</p>
 */
public final class SessionRef {

    private final String token;

    SessionRef(String token) {
        this.token = Objects.requireNonNull(token, "token");
    }

    public String token() {
        return token;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SessionRef ref && token.equals(ref.token);
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        return "SessionRef[redacted]";
    }
}
