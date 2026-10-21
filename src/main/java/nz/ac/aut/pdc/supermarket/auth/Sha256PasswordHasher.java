package nz.ac.aut.pdc.supermarket.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 现有 SHA-256 本地密码哈希实现（v3 要求保留）。
 *
 * <p>本次复用原有算法，接统一存储凭证视图；未重写认证，
 * 保持 {@link PasswordHasher} 契约。</p>
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
