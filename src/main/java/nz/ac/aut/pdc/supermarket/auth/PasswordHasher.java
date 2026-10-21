package nz.ac.aut.pdc.supermarket.auth;

/** Authentication-only hashing. Historical SHA-256 may be replaced locally by salted/KDF hashing without changing AuthService semantics.
 *
 *
 * 密码哈希能力（仅认证实现使用）。
 *
 * <p>骨架使用 SHA-256 演示；正式实现如需加盐/KDF，应只影响本实现，
 * 不改变 {@link AuthService} 的对外语义。</p>
 */
public interface PasswordHasher {

    String hash(char[] rawPassword);

    boolean matches(char[] rawPassword, String passwordHash);
}
