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

/** Complete P1 CUI for role-based business, quotation, history and original-key retry. Calls Operations only; authorized projections/feedback never expose WorkingCopy. Errors/exits follow FR-11.
 *
 *
 * 完整 P1 CUI：角色业务、报价确认、历史和原键重试。
 *
 * <p>菜单只调用 {@link Operations} 接口；统一运行适配器只返回授权投影和提交反馈，不暴露全量工作副本。交互错误反馈
 * 与退出路径遵循 FR-11。</p>
 */
public final class CuiApp {

    private static final String EXIT_COMMAND = "exit";
    /** Menu text is independent of permissions; backend validation authorizes operations.
 *
 * 菜单文本与权限分离：只列出当前装配实际提供的能力，权限仍由后台校验。 */
    private final nz.ac.aut.pdc.supermarket.i18n.Texts texts;
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
        this(authService, operations, inputValidator, input, output, new nz.ac.aut.pdc.supermarket.i18n.Texts("en"));
    }

    public CuiApp(AuthService authService, Operations operations, InputValidator inputValidator,
                  BufferedReader input, PrintStream output, nz.ac.aut.pdc.supermarket.i18n.Texts texts) {
        this.texts = Objects.requireNonNull(texts);
        this.authService = Objects.requireNonNull(authService, "authService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.inputValidator = Objects.requireNonNull(inputValidator, "inputValidator");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /** Login loop; exit leaves the program.
 *
 * 登录循环；输入 exit 退出程序。 */
    public void run() {
        output.println(texts.text("cui.003"));
        while (true) {
            String username = prompt(texts.text("cui.004"));
            if (username == null || EXIT_COMMAND.equalsIgnoreCase(username.trim())) {
                output.println(texts.text("cui.005"));
                return;
            }
            if (username.trim().equalsIgnoreCase("language")) { switchLanguage(); continue; }
            // Literal login keeps accounts named after navigation commands reachable.
            // 原样登录入口保证与导航命令同名的既有账号仍可登录。
            if (username.trim().equalsIgnoreCase("login")) {
                username = prompt(texts.text("language.literalUsername"));
                if (username == null) return;
            }
            String password = prompt(texts.text("cui.006"));
            if (password == null) {
                output.println(texts.text("cui.007"));
                return;
            }

            AuthResult result = authService.login(username.trim(), password.toCharArray());
            if (result instanceof AuthResult.Success success) {
                output.print(texts.text("cui.008", success.accountId(), texts.label(success.role().name())));
                runMainMenu(success);
            } else if (result instanceof AuthResult.Rejected rejected) {
                output.println(texts.text("cui.009") + texts.error(rejected.code()));
            } else if (result instanceof AuthResult.Failed failed) {
                output.println(texts.text("cui.010") + texts.error(failed.code()));
            }
        }
    }

    private void runMainMenu(AuthResult.Success session) {
        CuiActor actor = new CuiActor(session.accountId(), session.role(), session.session());
        while (true) {
            output.println();
            output.println(texts.text("cui.011"));
            output.println(texts.text("cui.000"));
            output.println(texts.text("language.entry"));
            if (operations instanceof MaintenanceOperations) {
                output.println(texts.text("cui.012"));
                output.println(texts.text("cui.001") + texts.text("cui.002"));
            }
            String choice = prompt(texts.text("cui.013"));
            if (choice == null || EXIT_COMMAND.equalsIgnoreCase(choice.trim())) {
                return;
            }
            if (choice.trim().equalsIgnoreCase("language")) { switchLanguage(); continue; }
            switch (choice.trim()) {
                case "0" -> {
                    output.println(texts.text("cui.014"));
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
        catch(InputValidator.InvalidInputException e){throw new IllegalArgumentException(texts.inputError(e.getMessage()));}
    }
    private java.util.List<nz.ac.aut.pdc.supermarket.app.AOperations.Item> items() {
        var items=new java.util.ArrayList<nz.ac.aut.pdc.supermarket.app.AOperations.Item>();
        while(true) {
            String barcode=required(texts.text("cui.015"));
            if(barcode.isEmpty()) break;
            items.add(new nz.ac.aut.pdc.supermarket.app.AOperations.Item(barcode,quantity(texts.text("cui.016"))));
        }
        return items;
    }
    private java.util.List<nz.ac.aut.pdc.infrastructure.AttachmentStore.Attachment> attachments(MaintenanceOperations full,CuiActor actor,String receiptId) {
        var files=new java.util.ArrayList<nz.ac.aut.pdc.infrastructure.AttachmentStore.Attachment>();
        while(true) {
            String path=required(texts.text("cui.017"));
            if(path.isEmpty()) return files;
            var imported=full.attach(actor,path,receiptId,files.size());
            if(!imported.hasValue()) {output.println(texts.text("cui.018")+texts.error(imported.code()));continue;}
            files.add(imported.value()); output.println(texts.text("cui.019"));
        }
    }
    private void maintenance(CuiActor actor,String choice) {
        if(!(operations instanceof MaintenanceOperations full)) {output.println(texts.text("cui.020"));return;}
        try {
            var clock=new nz.ac.aut.pdc.shared.IdClock(java.time.Clock.systemUTC());
            String key=clock.newRequestKey();
            switch(choice) {
                case "7" -> {
                    String mode=required(texts.text("cui.021"));if(!mode.equals("new") && !mode.equals("edit"))throw new IllegalArgumentException(texts.text("cui.022"));
                    String barcode=required(texts.text("cui.023")),name=required(texts.text("cui.024")),unit=required(texts.text("cui.025"));
                    var price=new java.math.BigDecimal(required(texts.text("cui.026")));
                    String note=price.signum()==0?required(texts.text("cui.027")):null;
                    print(full.executeA(actor,"PRODUCT_PUT",new nz.ac.aut.pdc.supermarket.app.AOperations.ProductPut(new nz.ac.aut.pdc.supermarket.catalog.Product(barcode,name,unit,price,note),mode.equals("new")?0:-1),key));
                }
                case "8" -> {
                    String barcode=required(texts.text("cui.023")); int qty=quantity(texts.text("cui.028"));
                    var position=nz.ac.aut.pdc.shared.StockEntry.Position.valueOf(required(texts.text("cui.029")).toUpperCase(Locale.ROOT));
                    print(full.executeA(actor,"OPENING",new nz.ac.aut.pdc.supermarket.app.AOperations.Opening(new nz.ac.aut.pdc.supermarket.app.AOperations.Item(barcode,qty),position,required(texts.text("cui.030"))),key));
                }
                case "9" -> {
                    var rows=items(); String receiptId=clock.entityId(key,"document");
                    print(full.executeA(actor,"RECEIPT_CREATE",new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptCreate(rows,attachments(full,actor,receiptId)),key));
                }
                case "10" -> {
                    String id=required(texts.text("cui.031")); print(full.inspect(actor,"DOCUMENT",id));
                    Boolean approve=readYesNo(texts.text("cui.032")); if(approve==null)return;
                    String opinion=required(texts.text("cui.033"));
                    print(full.executeA(actor,"RECEIPT_REVIEW",new nz.ac.aut.pdc.supermarket.app.AOperations.ReceiptReview(id,0,approve,opinion,attachments(full,actor,id)),key));
                }
                case "11" -> {
                    String id=required(texts.text("cui.031")); print(full.inspect(actor,"DOCUMENT",id));
                    output.println(texts.text("cui.034"));
                    var rows=items(); print(full.executeA(actor,"RECEIPT_CORRECT",new MaintenanceOperations.ReceiptEdit(id,rows,required(texts.text("cui.035"))),key));
                }
                case "12" -> {
                    String id=required(texts.text("cui.036")); if(id.isEmpty())id=clock.entityId(key,"promotion");
                    var kind=nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.valueOf(required(texts.text("cui.037")).toUpperCase(Locale.ROOT));
                    java.math.BigDecimal percent=null,threshold=null,reduction=null;
                    if(kind==nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.PERCENT) percent=new java.math.BigDecimal(required(texts.text("cui.038")));
                    else {threshold=new java.math.BigDecimal(required(texts.text("cui.039")));reduction=new java.math.BigDecimal(required(texts.text("cui.040")));}
                    String priority=required(texts.text("cui.041")),start=required(texts.text("cui.042")),end=required(texts.text("cui.043"));
                    var barcodes=new java.util.HashSet<>(java.util.Arrays.asList(required(texts.text("cui.044")).split("\\s*,\\s*")));
                    var promotion=new nz.ac.aut.pdc.supermarket.promotion.Promotion(id,kind,percent,threshold,reduction,priority.isEmpty()?null:Integer.valueOf(priority),start,end,barcodes);
                    print(full.executeA(actor,"PROMOTION_PUT",new nz.ac.aut.pdc.supermarket.app.AOperations.PromotionPut(promotion,-1),key));
                }
                case "13" -> {
                    var rows=items();var quote=full.quoteSale(actor,rows,key);print(quote);if(!quote.success())return; Boolean paid=readYesNo(texts.text("cui.045"));if(paid==null || !paid)return;
                    print(full.executeA(actor,"SALE",new nz.ac.aut.pdc.supermarket.app.AOperations.Sale(rows,true),key));
                }
                case "14" -> {
                    String category=required(texts.text("cui.046")).toUpperCase(Locale.ROOT);
                    print(full.inspect(actor,category,category.equals("DOCUMENT")?required(texts.text("cui.047")):category.equals("QUOTA")?required(texts.text("cui.048")):null));
                }
                case "18" -> {
                    var rows=new java.util.ArrayList<AftersaleRequest>();
                    while(true) {var row=readAftersaleLine();if(row==null)return;rows.add(row);Boolean more=readYesNo(texts.text("cui.049"));if(more==null)return;if(!more)break;}
                    String supplied=retryKey();String finalKey=supplied==null?java.util.UUID.randomUUID().toString():supplied;
                    var quote=full.quoteAftersale(actor,rows,finalKey);print(quote);if(!quote.success())return;Boolean confirm=readYesNo(texts.text("cui.050"));if(confirm==null || !confirm)return;
                    print(full.aftersaleBatch(actor,rows,finalKey));
                }
                case "16", "17" -> {
                    print(full.inspect(actor,"LOSS",null));
                    String id=required(texts.text("cui.051"));
                    int qty=choice.equals("17")?quantity(texts.text("cui.052")):0;
                    var request=new nz.ac.aut.pdc.supermarket.stock.LossWorkflow.Request(id,qty,required(texts.text("cui.053")),required(texts.text("cui.054")));
                    print(full.executeA(actor,choice.equals("17")?nz.ac.aut.pdc.supermarket.stock.LossWorkflow.CORRECT:nz.ac.aut.pdc.supermarket.stock.LossWorkflow.DISPOSE,request,key));
                }
                case "15" -> print(full.retry(actor,required(texts.text("cui.055"))));
                default -> output.println(texts.text("cui.020"));
            }
        } catch(CancelInput ignored) { output.println(texts.text("cui.056")); }
        catch(RuntimeException e) { output.println(texts.text("cui.057")+texts.inputError(e.getMessage())); }
    }

    private void replenish(CuiActor actor) {
        String barcode = prompt(texts.text("cui.058"));
        if (barcode == null) {
            return;
        }
        Integer quantity = readQuantity(texts.text("cui.059"));
        if (quantity == null) {
            return;
        }
        print(operations.replenish(actor, barcode.trim(), quantity));
    }

    private void recordLoss(CuiActor actor) {
        String barcode = prompt(texts.text("cui.058"));
        if (barcode == null) {
            return;
        }
        String positionCode = prompt(texts.text("cui.060"));
        if (positionCode == null) {
            return;
        }
        Position position = parsePosition(positionCode);
        if (position == null) {
            return;
        }
        Integer quantity = readQuantity(texts.text("cui.061"));
        if (quantity == null) {
            return;
        }
        if(operations instanceof MaintenanceOperations) {String reason=prompt(texts.text("cui.062"));if(reason==null)return;print(operations.recordLoss(actor,barcode.trim(),position,quantity,reason));}
            else print(operations.recordLoss(actor, barcode.trim(), position, quantity));
    }

    private AftersaleRequest readAftersaleLine() {
        String saleLineId = prompt(texts.text("cui.063"));
        if (saleLineId == null) {
            return null;
        }
        String kindCode = prompt(texts.text("cui.064"));
        if (kindCode == null) {
            return null;
        }
        RedemptionKind kind = parseKind(kindCode);
        if (kind == null) {
            return null;
        }
        Integer quantity = readQuantity(texts.text("cui.065"));
        if (quantity == null) {
            return null;
        }
        Boolean opened = readYesNo(texts.text("cui.066"));
        if (opened == null) {
            return null;
        }
        Boolean qualityProblem = readYesNo(texts.text("cui.067"));
        if (qualityProblem == null) {
            return null;
        }
        AftersaleReasonKind reasonKind;
        String reasonText = null;
        if (qualityProblem) {
            reasonKind = AftersaleReasonKind.QUALITY;
        } else {
            reasonKind = AftersaleReasonKind.OTHER;
            reasonText = prompt(texts.text("cui.068"));
            if (reasonText == null || reasonText.isBlank()) {
                output.println(texts.text("cui.069"));
                return null;
            }
            reasonText = reasonText.trim();
        }
        String replacementBarcode = null;
        int replacementQuantity = 0;
        if (kind == RedemptionKind.EXCHANGE) {
            replacementBarcode = prompt(texts.text("cui.070"));
            if (replacementBarcode == null || replacementBarcode.isBlank()) {
                output.println(texts.text("cui.071"));
                return null;
            }
            replacementBarcode = replacementBarcode.trim();
            Integer parsedReplacementQuantity = readQuantity(texts.text("cui.072"));
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
                Boolean confirm=readYesNo(texts.text("cui.050"));if(confirm==null || !confirm)return;
                print(full.aftersaleBatch(actor,java.util.List.of(request),key));
            } else print(operations.aftersale(actor,request));
        } catch(RuntimeException e){output.println(texts.text("cui.073")+texts.inputError(e.getMessage()));}
    }
    private String retryKey() {
        String value=prompt(texts.text("cui.074"));
        if(value==null)throw new CancelInput();
        if(value.isBlank())return null;
        return java.util.UUID.fromString(value.trim()).toString();
    }
    private void reverseDocument(CuiActor actor) {
        String targetId = prompt(texts.text("cui.075"));
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        String reason = prompt(texts.text("cui.076"));
        if (reason == null || reason.isBlank()) {
            output.println(texts.text("cui.077"));
            return;
        }
        try {
            if(operations instanceof SharedOperations full) {String key=retryKey();print(full.reverseDocument(actor,targetId.trim(),reason.trim(),key==null?java.util.UUID.randomUUID().toString():key));}
            else print(operations.reverseDocument(actor, targetId.trim(), reason.trim()));
        } catch (RuntimeException ex) {
            output.println(texts.text("cui.078") + texts.inputError(ex.getMessage()));
        }
    }

    private void deleteAftersaleDraft(CuiActor actor) {
        String draftId = prompt(texts.text("cui.079"));
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        try {
            print(operations.deleteAftersaleDraft(actor, draftId.trim()));
        } catch (RuntimeException ex) {
            output.println(texts.text("cui.078") + texts.inputError(ex.getMessage()));
        }
    }

    private void cancelAftersaleDraft(CuiActor actor) {
        String draftId = prompt(texts.text("cui.079"));
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        try {
            print(operations.cancelAftersaleDraft(actor, draftId.trim()));
        } catch (RuntimeException ex) {
            output.println(texts.text("cui.078") + texts.inputError(ex.getMessage()));
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
            output.println(texts.text("cui.080") + texts.inputError(ex.getMessage()));
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
        output.println(texts.text("cui.081"));
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
        output.println(texts.text("cui.082"));
        return null;
    }

    private RedemptionKind parseKind(String raw) {
        try {
            return RedemptionKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            output.println(texts.text("cui.083"));
            return null;
        }
    }

    private void print(OperationFeedback feedback) {
        output.println(feedback.render(texts));
    }

    private void switchLanguage() {
        String selected = prompt(texts.text("language.choose"));
        if (selected == null) return;
        if (!selected.equals("en") && !selected.equals("zh")) { output.println(texts.text("language.invalid")); return; }
        texts.select(selected);
        output.println(texts.text("language.changed"));
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
