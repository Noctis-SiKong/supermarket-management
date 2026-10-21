package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;

/** IF-01 three-state authentication: trusted session on success, INVALID_CREDENTIALS business rejection or AUTH_TECHNICAL_FAILURE technical failure (shared v1.02 section 3.1).
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.Rejected, AuthResult.Failed {

    /** Successful login/resolution returns trusted session and resolved identity.
  */
    record Success(SessionRef session, String accountId, Role role) implements AuthResult {
        public Success {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(role, "role");
        }
    }

    /** Business rejection: invalid credentials/session or authentication required.
  */
    record Rejected(String code, String message) implements AuthResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Credential-read/verification technical failure, distinct from incorrect user credentials.
  */
    record Failed(String code, String message) implements AuthResult {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
