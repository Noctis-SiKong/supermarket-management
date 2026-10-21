package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;

/** Opaque AuthService session with package-visible constructor prevents caller-forged identity. Historical expiry is not implemented; see shared section 3.1.
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
