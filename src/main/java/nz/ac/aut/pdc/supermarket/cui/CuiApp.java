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
 * 完整 P1 CUI：角色业务、报价确认、历史和原键重试。
 *
 * <p>菜单只调用 {@link Operations} 接口；统一运行适配器只返回授权投影和提交反馈，不暴露全量工作副本。交互错误反馈
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
            output.println("1) 补货   2) 店内报损   3) 售后   4) 取消售后草稿   5) 删除未读取草稿   6) 整单冲销   0) 退出登录");
            if(operations instanceof MaintenanceOperations) output.println("7) 商品维护  8) 期初登记  9) 收货登记  10) 收货审核  11) 收货补正  12) 促销  13) 销售  14) 查询  15) 按原请求键重试  16) 损耗处置  17) 管理员报损修正  18) 多行／混合售后");
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
                case "4" -> cancelAftersaleDraft(actor);
                case "5" -> deleteAftersaleDraft(actor);
                case "6" -> reverseDocument(actor);
                default -> maintenance(actor,choice.trim());
            }
        }
    }

    private static final class CancelInput extends RuntimeException {}
    private String required(String message) {
        String value=prompt(message);
        if(value==null || value.equalsIgnoreCase("exit")) throw new CancelInput();
        return value.trim();
    }
    private int quantity(String message) {
        try{return inputValidator.parseQuantity(required(message));}
        catch(InputValidator.InvalidInputException e){throw new IllegalArgumentException(e.getMessage());}
    }
    private java.util.List<nz.ac.aut.pdc.supermarket.app.AOperations.Item> items() {
        var items=new java.util.ArrayList<nz.ac.aut.pdc.supermarket.app.AOperations.Item>();
        while(true) {
            String barcode=required("商品条码（空行结束明细，exit 放弃）：");
            if(barcode.isEmpty()) break;
            items.add(new nz.ac.aut.pdc.supermarket.app.AOperations.Item(barcode,quantity("数量（1-9999）：")));
        }
        return items;
    }
    private java.util.List<nz.ac.aut.pdc.infrastructure.AttachmentStore.Attachment> attachments(MaintenanceOperations full,CuiActor actor,String receiptId) {
        var files=new java.util.ArrayList<nz.ac.aut.pdc.infrastructure.AttachmentStore.Attachment>();
        while(true) {
            String path=required("JPG/PNG 本地路径（空行跳过／结束）：");
            if(path.isEmpty()) return files;
            var imported=full.attach(actor,path,receiptId,files.size());
            if(!imported.hasValue()) {output.println("附件未导入："+imported.code());continue;}
            files.add(imported.value()); output.println("附件已导入待随收货提交");
        }
    }
    private void maintenance(CuiActor actor,String choice) {
        if(!(operations instanceof MaintenanceOperations full)) {output.println("无效选项，请重新选择");return;}
        try {
            var clock=new nz.ac.aut.pdc.shared.IdClock(java.time.Clock.systemUTC());
            String key=clock.newRequestKey();
            switch(choice) {
                case "7" -> {
                    String mode=required("new 新增 / edit 修改：");if(!mode.equals("new") && !mode.equals("edit"))throw new IllegalArgumentException("请选择 new 或 edit");
                    String barcode=required("条码："),name=required("品牌及商品名："),unit=required("单位：");
                    var price=new java.math.BigDecimal(required("售价（两位小数）："));
                    String note=price.signum()==0?required("零价说明（必填）："):null;
                    print(full.executeA(actor,"PRODUCT_PUT",new nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut(new nz.ac.aut.pdc.supermarket.catalog.Product(barcode,name,unit,price,note),mode.equals("new")?0:-1),key));
                }
                case "8" -> {
                    String barcode=required("条码："); int qty=quantity("期初数量：");
                    var position=nz.ac.aut.pdc.shared.StockEntry.Position.valueOf(required("位置 S/W：").toUpperCase(Locale.ROOT));
                    print(full.executeA(actor,"OPENING",new nz.ac.aut.pdc.supermarket.app.AOperations.Opening(new nz.ac.aut.pdc.supermarket.app.AOperations.Item(barcode,qty),position,required("期初来源说明：")),key));
                }
                case "9" -> {
                    var rows=items(); String receiptId=clock.entityId(key,"document");
                    print(full.executeA(actor,"RECEIPT_CREATE",new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptCreate(rows,attachments(full,actor,receiptId)),key));
                }
                case "10" -> {
                    String id=required("收货单号："); print(full.inspect(actor,"DOCUMENT",id));
                    Boolean approve=readYesNo("审核通过？y/n（n=退回补正）："); if(approve==null)return;
                    String opinion=required("审核意见：");
                    print(full.executeA(actor,"RECEIPT_REVIEW",new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptReview(id,0,approve,opinion,attachments(full,actor,id)),key));
                }
                case "11" -> {
                    String id=required("收货单号："); print(full.inspect(actor,"DOCUMENT",id));
                    output.println("输入补正后的全部明细；遗漏的未入账明细将删除，原商品保留稳定行号。");
                    var rows=items(); print(full.executeA(actor,"RECEIPT_CORRECT",new MaintenanceOperations.ReceiptEdit(id,rows,required("补正原因：")),key));
                }
                case "12" -> {
                    String id=required("活动 ID（空行新建）："); if(id.isEmpty())id=clock.entityId(key,"promotion");
                    var kind=nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.valueOf(required("PERCENT 折扣 / REDUCTION 满减：").toUpperCase(Locale.ROOT));
                    java.math.BigDecimal percent=null,threshold=null,reduction=null;
                    if(kind==nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.PERCENT) percent=new java.math.BigDecimal(required("实付百分比（1-99）："));
                    else {threshold=new java.math.BigDecimal(required("满减门槛："));reduction=new java.math.BigDecimal(required("减免金额："));}
                    String priority=required("优先级（空行未设置）："),start=required("开始日 yyyy-MM-dd："),end=required("结束日 yyyy-MM-dd：");
                    var barcodes=new java.util.HashSet<>(java.util.Arrays.asList(required("适用条码（逗号分隔）：").split("\\s*,\\s*")));
                    var promotion=new nz.ac.aut.pdc.supermarket.promotion.Promotion(id,kind,percent,threshold,reduction,priority.isEmpty()?null:Integer.valueOf(priority),start,end,barcodes);
                    print(full.executeA(actor,"PROMOTION_PUT",new nz.ac.aut.pdc.supermarket.app.AOperations.PromotionPut(promotion,-1),key));
                }
                case "13" -> {
                    var rows=items();var quote=full.quoteSale(actor,rows,key);print(quote);if(!quote.success())return; Boolean paid=readYesNo("模拟付款成功并确认成交？y/n：");if(paid==null || !paid)return;
                    print(full.executeA(actor,"SALE",new nz.ac.aut.pdc.supermarket.app.AOperations.Sale(rows,true),key));
                }
                case "14" -> {
                    String category=required("查询 PRODUCT 商品 / STOCK 库存 / PROMOTION 活动 / DOCUMENT 单据 / QUOTA 额度：").toUpperCase(Locale.ROOT);
                    print(full.inspect(actor,category,category.equals("DOCUMENT")?required("单据号（空行列出当前身份可见单据）："):category.equals("QUOTA")?required("原销售行凭证："):null));
                }
                case "18" -> {
                    var rows=new java.util.ArrayList<AftersaleRequest>();
                    while(true) {var row=readAftersaleLine();if(row==null)return;rows.add(row);Boolean more=readYesNo("继续添加售后明细？y/n：");if(more==null)return;if(!more)break;}
                    String supplied=retryKey();String finalKey=supplied==null?java.util.UUID.randomUUID().toString():supplied;
                    var quote=full.quoteAftersale(actor,rows,finalKey);print(quote);if(!quote.success())return;Boolean confirm=readYesNo("确认本次售后结算？y/n（n 保留可取消草稿）：");if(confirm==null || !confirm)return;
                    print(full.aftersaleBatch(actor,rows,finalKey));
                }
                case "16", "17" -> {
                    print(full.inspect(actor,"LOSS",null));
                    String id=required("损耗记录 ID：");
                    int qty=choice.equals("17")?quantity("修正后的数量："):0;
                    var request=new nz.ac.aut.pdc.supermarket.stock.LossWorkflow.Request(id,qty,required("实际去向／处置结果："),required("原因："));
                    print(full.executeA(actor,choice.equals("17")?nz.ac.aut.pdc.supermarket.stock.LossWorkflow.CORRECT:nz.ac.aut.pdc.supermarket.stock.LossWorkflow.DISPOSE,request,key));
                }
                case "15" -> print(full.retry(actor,required("原请求键（保留原始输入，禁止换键盲重做）：")));
                default -> output.println("无效选项，请重新选择");
            }
        } catch(CancelInput ignored) { output.println("已放弃本次输入"); }
        catch(RuntimeException e) { output.println("输入或操作被拒绝："+e.getMessage()); }
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
        if(operations instanceof MaintenanceOperations) {String reason=prompt("报损原因（必填）：");if(reason==null)return;print(operations.recordLoss(actor,barcode.trim(),position,quantity,reason));}
            else print(operations.recordLoss(actor, barcode.trim(), position, quantity));
    }

    private AftersaleRequest readAftersaleLine() {
        String saleLineId = prompt("原销售行 ID：");
        if (saleLineId == null) {
            return null;
        }
        String kindCode = prompt("售后类型（RETURN / EXCHANGE）：");
        if (kindCode == null) {
            return null;
        }
        RedemptionKind kind = parseKind(kindCode);
        if (kind == null) {
            return null;
        }
        Integer quantity = readQuantity("售后数量（1-9999）：");
        if (quantity == null) {
            return null;
        }
        Boolean opened = readYesNo("原商品是否已开封（y/n）：");
        if (opened == null) {
            return null;
        }
        Boolean qualityProblem = readYesNo("是否质量问题（y/n）：");
        if (qualityProblem == null) {
            return null;
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
                return null;
            }
            reasonText = reasonText.trim();
        }
        String replacementBarcode = null;
        int replacementQuantity = 0;
        if (kind == RedemptionKind.EXCHANGE) {
            replacementBarcode = prompt("替换品条码：");
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                output.println("输入错误：换货必须填写替换品条码");
                return null;
            }
            replacementBarcode = replacementBarcode.trim();
            Integer parsedReplacementQuantity = readQuantity("替换数量（1-9999）：");
            if (parsedReplacementQuantity == null) {
                return null;
            }
            replacementQuantity = parsedReplacementQuantity;
        }
        return new AftersaleRequest(saleLineId.trim(),kind,quantity,opened,qualityProblem,reasonKind,reasonText,replacementBarcode,replacementQuantity,null);
    }
    private void aftersale(CuiActor actor) {
        try {var request=readAftersaleLine();if(request==null)return;
            if(operations instanceof MaintenanceOperations full) {
                String supplied=retryKey();String key=supplied==null?java.util.UUID.randomUUID().toString():supplied;
                var quote=full.quoteAftersale(actor,java.util.List.of(request),key);print(quote);if(!quote.success())return;
                Boolean confirm=readYesNo("确认本次售后结算？y/n（n 保留可取消草稿）：");if(confirm==null || !confirm)return;
                print(full.aftersaleBatch(actor,java.util.List.of(request),key));
            } else print(operations.aftersale(actor,request));
        } catch(RuntimeException e){output.println("操作拒绝："+e.getMessage());}
    }
    private String retryKey() {
        String value=prompt("原请求键（首次留空；结果未知时填写原键并保留相同输入）：");
        if(value==null)throw new CancelInput();
        if(value.isBlank())return null;
        return java.util.UUID.fromString(value.trim()).toString();
    }
    private void reverseDocument(CuiActor actor) {
        String targetId = prompt("待冲销单据 ID：");
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        String reason = prompt("冲销原因（必填）：");
        if (reason == null || reason.isBlank()) {
            output.println("输入错误：冲销原因必须填写");
            return;
        }
        try {
            if(operations instanceof SharedOperations full) {String key=retryKey();print(full.reverseDocument(actor,targetId.trim(),reason.trim(),key==null?java.util.UUID.randomUUID().toString():key));}
            else print(operations.reverseDocument(actor, targetId.trim(), reason.trim()));
        } catch (RuntimeException ex) {
            output.println("操作失败：" + ex.getMessage());
        }
    }

    private void deleteAftersaleDraft(CuiActor actor) {
        String draftId = prompt("售后草稿 ID：");
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        try {
            print(operations.deleteAftersaleDraft(actor, draftId.trim()));
        } catch (RuntimeException ex) {
            output.println("操作失败：" + ex.getMessage());
        }
    }

    private void cancelAftersaleDraft(CuiActor actor) {
        String draftId = prompt("售后草稿 ID：");
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        try {
            print(operations.cancelAftersaleDraft(actor, draftId.trim()));
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
            String value=input.readLine();
            return value!=null && value.trim().equalsIgnoreCase("exit")?null:value;
        } catch (IOException ex) {
            return null;
        }
    }
}
