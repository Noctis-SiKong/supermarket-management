package nz.ac.aut.pdc.supermarket.cui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.AuthService;

/**
 * 最小 CUI 壳骨架：登录、退出与结果呈现。
 *
 * <p>业务菜单尚未接入；本类只依赖 AuthService 接口，不直接依赖具体服务实现，
 * 便于后续通过构造器注入各业务操作接口。交互错误反馈与退出路径遵循 FR-11。</p>
 */
public final class CuiApp {

    private static final String EXIT_COMMAND = "exit";

    private final AuthService authService;
    private final BufferedReader input;
    private final PrintStream output;

    public CuiApp(AuthService authService, BufferedReader input, PrintStream output) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /** 进入登录循环；输入 exit 退出。 */
    public void run() {
        output.println("== 超市管理系统（CUI 骨架）==");
        while (true) {
            String username = prompt("用户名（输入 exit 退出）：");
            if (username == null || EXIT_COMMAND.equalsIgnoreCase(username.trim())) {
                output.println("再见");
                return;
            }
            String password = prompt("密码：");
            if (password == null) {
                output.println("已退出");
                return;
            }

            AuthResult result = authService.login(username.trim(), password.toCharArray());
            if (result instanceof AuthResult.Success success) {
                output.printf("登录成功：%s（角色 %s）%n", success.accountId(), success.role());
                output.println("[业务菜单将在后续模块接入；输入 exit 退出]");
            } else if (result instanceof AuthResult.Rejected rejected) {
                output.println("登录失败：" + rejected.message());
            } else if (result instanceof AuthResult.Failed failed) {
                output.println("系统错误：" + failed.message());
            }
        }
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();
        try {
            return input.readLine();
        } catch (IOException ex) {
            return null;
        }
    }
}
