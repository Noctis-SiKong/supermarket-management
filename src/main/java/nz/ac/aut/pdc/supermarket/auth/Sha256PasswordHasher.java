package nz.ac.aut.pdc.supermarket.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 基于 SHA-256 的骨架密码哈希实现。
 *
 * <p>仅用于骨架与测试；正式实现应使用带盐的 KDF（如 PBKDF2），
 * 但不改变 {@link PasswordHasher} 契约。</p>
 */
public final class Sha256PasswordHasher implements PasswordHasher {

    @Override
    public String hash(char[] rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(new String(rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @Override
    public boolean matches(char[] rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        byte[] actual = hash(rawPassword).getBytes(StandardCharsets.UTF_8);
        byte[] expected = passwordHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
