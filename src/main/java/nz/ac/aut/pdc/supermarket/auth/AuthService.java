package nz.ac.aut.pdc.supermarket.auth;

/** Minimal B-owned IF-01 authentication: issue sessions and resolve trusted identity; caller-supplied roles/account IDs never authorize operations.
 */
public interface AuthService {

    /** Login with preset username and transient password, never logged/stored in plaintext. Success returns a session; invalid credentials are REJECTED; technical faults are FAILED.
     */
    AuthResult login(String username, char[] password);

    /** Resolve trusted identity from a session; invalid sessions return REJECTED.
  */
    AuthResult resolve(SessionRef session);
}
