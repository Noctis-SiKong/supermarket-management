package nz.ac.aut.pdc.supermarket.fixture;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import nz.ac.aut.pdc.supermarket.auth.CredentialStore;

/**
 * 内存凭证只读替身（测试用途，非生产实现）。
 *
 * <p>替代 A 的统一存储认证只读视图；不写真实 data/，不提供明文密码。</p>
 */
public final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, StoredCredential> credentials = new HashMap<>();

    public void add(StoredCredential credential) {
        credentials.put(credential.username(), credential);
    }

    @Override
    public Optional<StoredCredential> findByUsername(String username) {
        return Optional.ofNullable(credentials.get(username));
    }
}
