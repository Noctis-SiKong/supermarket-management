package nz.ac.aut.pdc.supermarket.auth;

/** Authentication-only hashing. Historical SHA-256 may be replaced locally by salted/KDF hashing without changing AuthService semantics.
 */
public interface PasswordHasher {

    String hash(char[] rawPassword);

    boolean matches(char[] rawPassword, String passwordHash);
}
