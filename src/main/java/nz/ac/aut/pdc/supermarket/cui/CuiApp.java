package nz.ac.aut.pdc.supermarket.cui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;

import nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind;
import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;
import nz.ac.aut.pdc.supermarket.auth.AuthService;
import nz.ac.aut.pdc.supermarket.stock.Position;

/**
 * 最小 CUI：登录 + 角色菜单 + 补货/报损/售后分派。
 *
 * <p>菜单只调用 {@link Operations} 接口；真实业务适配器接入前使用
 * {@link UnavailableOperations}，不会伪造库存或提交结果。交互错误反馈
 * 与退出路径遵循 FR-11。</p>
 */
public final class CuiApp {

    private static final String EXIT_COMMAND = "exit";

    private final AuthService authService;
    private final Operations operations;
    private final InputValidator inputValidator;
    private final BufferedReader input;
    private final PrintStream output;

    public CuiApp(
            AuthService authService,
            Operations operations,
            InputValidator inputValidator,
            BufferedReader input,
            PrintStream output) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.inputValidator = Objects.requireNonNull(inputValidator, "inputValidator");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /** 登录循环；输入 exit 退出程序。 */
    public void run() {
        output.println("== 超市管理系统（CUI）==");
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
                runMainMenu(success);
            } else if (result instanceof AuthResult.Rejected rejected) {
                output.println("登录失败：" + rejected.message());
            } else if (result instanceof AuthResult.Failed failed) {
                output.println("系统错误：" + failed.message());
            }
        }
    }

    private void runMainMenu(AuthResult.Success session) {
        CuiActor actor = new CuiActor(session.accountId(), session.role(), session.session());
        while (true) {
            output.println();
            output.println("1) 补货   2) 店内报损   3) 售后   0) 退出登录");
            String choice = prompt("请选择：");
            if (choice == null || EXIT_COMMAND.equalsIgnoreCase(choice.trim())) {
                return;
            }
            switch (choice.trim()) {
                case "0" -> {
                    output.println("已退出登录");
                    return;
                }
                case "1" -> replenish(actor);
                case "2" -> recordLoss(actor);
                case "3" -> aftersale(actor);
                default -> output.println("无效选项，请重新选择");
            }
        }
    }

    private void replenish(CuiActor actor) {
        String barcode = prompt("商品条码：");
        if (barcode == null) {
            return;
        }
        Integer quantity = readQuantity("补货数量（1-9999）：");
        if (quantity == null) {
            return;
        }
        print(operations.replenish(actor, barcode.trim(), quantity));
    }

    private void recordLoss(CuiActor actor) {
        String barcode = prompt("商品条码：");
        if (barcode == null) {
            return;
        }
        String positionCode = prompt("来源位置（S=货架 / W=仓库）：");
        if (positionCode == null) {
            return;
        }
        Position position = parsePosition(positionCode);
        if (position == null) {
            return;
        }
        Integer quantity = readQuantity("报损数量（1-9999）：");
        if (quantity == null) {
            return;
        }
        print(operations.recordLoss(actor, barcode.trim(), position, quantity));
    }

    private void aftersale(CuiActor actor) {
        String saleLineId = prompt("原销售行 ID：");
        if (saleLineId == null) {
            return;
        }
        String kindCode = prompt("售后类型（RETURN / EXCHANGE）：");
        if (kindCode == null) {
            return;
        }
        RedemptionKind kind = parseKind(kindCode);
        if (kind == null) {
            return;
        }
        Integer quantity = readQuantity("售后数量（1-9999）：");
        if (quantity == null) {
            return;
        }
        Boolean opened = readYesNo("原商品是否已开封（y/n）：");
        if (opened == null) {
            return;
        }
        Boolean qualityProblem = readYesNo("是否质量问题（y/n）：");
        if (qualityProblem == null) {
            return;
        }
        AftersaleReasonKind reasonKind;
        String reasonText = null;
        if (qualityProblem) {
            reasonKind = AftersaleReasonKind.QUALITY;
        } else {
            reasonKind = AftersaleReasonKind.OTHER;
            reasonText = prompt("其他原因（必填）：");
            if (reasonText == null || reasonText.isBlank()) {
                output.println("输入错误：其他原因必须填写原因文本");
                return;
            }
            reasonText = reasonText.trim();
        }
        String replacementBarcode = null;
        int replacementQuantity = 0;
        if (kind == RedemptionKind.EXCHANGE) {
            replacementBarcode = prompt("替换品条码：");
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                output.println("输入错误：换货必须填写替换品条码");
                return;
            }
            replacementBarcode = replacementBarcode.trim();
            Integer parsedReplacementQuantity = readQuantity("替换数量（1-9999）：");
            if (parsedReplacementQuantity == null) {
                return;
            }
            replacementQuantity = parsedReplacementQuantity;
        }
        AftersaleRequest request = new AftersaleRequest(
                saleLineId.trim(), kind, quantity, opened, qualityProblem,
                reasonKind, reasonText, replacementBarcode, replacementQuantity);
        try {
            print(operations.aftersale(actor, request));
        } catch (RuntimeException ex) {
            output.println("操作失败：" + ex.getMessage());
        }
    }

    private Integer readQuantity(String message) {
        String raw = prompt(message);
        if (raw == null) {
            return null;
        }
        try {
            return inputValidator.parseQuantity(raw);
        } catch (InputValidator.InvalidInputException ex) {
            output.println("输入错误：" + ex.getMessage());
            return null;
        }
    }

    private Boolean readYesNo(String message) {
        String raw = prompt(message);
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("y".equals(value) || "yes".equals(value)) {
            return Boolean.TRUE;
        }
        if ("n".equals(value) || "no".equals(value)) {
            return Boolean.FALSE;
        }
        output.println("输入错误：请输入 y 或 n");
        return null;
    }

    private Position parsePosition(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("S".equals(value)) {
            return Position.S;
        }
        if ("W".equals(value)) {
            return Position.W;
        }
        output.println("输入错误：位置只能是 S 或 W");
        return null;
    }

    private RedemptionKind parseKind(String raw) {
        try {
            return RedemptionKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            output.println("输入错误：售后类型只能是 RETURN 或 EXCHANGE");
            return null;
        }
    }

    private void print(OperationFeedback feedback) {
        output.println(feedback.message());
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
