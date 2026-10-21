package nz.ac.aut.pdc.supermarket.auth;

import java.util.Objects;
import java.util.Optional;

/** Read-only credential port using A authentication storage view; historical tests use doubles, not another JSON saver. Returns hashes only.
 *
 *
 * 预置账号凭证的只读访问端口（IF-01 的存储依赖）。
 *
 * <p>正式实现由 A 的统一存储提供认证专用只读视图；本骨架用测试替身，
 * 不复制 JSON 保存器。密码只以哈希形式返回，不返回明文。</p>
 */
public interface CredentialStore {

    Optional<StoredCredential> findByUsername(String username);

    /** One immutable stored account credential.
 *
 * 存储中的一条只读账号凭证。 */
    record StoredCredential(String accountId, String username, Role role, String passwordHash) {
        public StoredCredential {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(passwordHash, "passwordHash");
        }
    }
}
