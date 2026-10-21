package nz.ac.aut.pdc.supermarket.auth;

/**
 * 本地认证与可信身份解析（IF-01，B 提供并唯一维护）。
 *
 * <p>接口刻意保持最小：登录签发会话；下游用会话解析可信身份。
 * 不接受调用方自行传入角色字符串或账户 ID 作为授权依据。</p>
 */
public interface AuthService {

    /**
     * 本地账号登录。
     *
     * @param username 预置账号用户名
     * @param password 明文密码（仅认证调用使用，不写日志/业务记录）
     * @return 成功返回会话；凭证错误返回 REJECTED；技术故障返回 FAILED
     */
    AuthResult login(String username, char[] password);

    /** 解析会话引用为可信身份；无效会话返回 REJECTED。 */
    AuthResult resolve(SessionRef session);
}
