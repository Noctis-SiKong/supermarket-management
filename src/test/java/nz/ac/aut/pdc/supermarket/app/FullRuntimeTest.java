package nz.ac.aut.pdc.supermarket.app;

import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.supermarket.auth.*;
import nz.ac.aut.pdc.supermarket.cui.*;
import nz.ac.aut.pdc.supermarket.aftersale.*;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.stock.LossWorkflow;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** Fictional users/goods, REAL storage, auth, stock, quota, draft/read/cancel, CUI and reversal. Main chains use no dependency doubles; fault injections and the historical writer fixture are explicitly marked. */
public class FullRuntimeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    Path project; DataRoot root; Application app; RepositoryBIntegration b;
    List<JsonStore.Credential> accounts; String password; CuiActor manager,clerk,admin; boolean fail;
    final Clock clock=Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"),ZoneOffset.UTC);
    @Before public void start() throws Exception {
        project=temp.newFolder().toPath(); Files.writeString(project.resolve("pom.xml"),"fixture");Files.writeString(project.resolve("README.md"),"fixture");Files.createDirectory(project.resolve("docs"));
        root=DataRoot.forProject(project);password=UUID.randomUUID().toString();String hash=new Sha256PasswordHasher().hash(password.toCharArray());
        accounts=List.of(new JsonStore.Credential("fictional-manager","fictional-manager",hash,OperationContext.Role.MANAGER),
            new JsonStore.Credential("fictional-clerk","fictional-clerk",hash,OperationContext.Role.CLERK),new JsonStore.Credential("fictional-admin","fictional-admin",hash,OperationContext.Role.ADMIN));
        open();
    }
    void open() throws Exception {
        b=new RepositoryBIntegration(accounts);app=new Application(root,b,clock,p -> {if(fail)throw new IOException("Injected BEFORE replacement");});
        manager=login("fictional-manager");clerk=login("fictional-clerk");admin=login("fictional-admin");
    }
    CuiActor login(String name) {
        var success=(AuthResult.Success)b.auth().login(name,password.toCharArray());return new CuiActor(success.accountId(),success.role(),success.session());
    }
    @After public void stop() throws Exception {if(app!=null)app.close();}
    SharedOperations operations(){return (SharedOperations)b.cuiOperations(app);}
    OperationContext op(CuiActor actor,String kind,String target){return new OperationContext(actor.session(),app.ids().newRequestKey(),kind,target);}
    WorkingCopy state(){return app.submissions().open(op(manager,"TEST",null)).value();}
    String execute(CuiActor actor,String kind,Object input) {
        var op=op(actor,kind,null);var result=app.execute(op,BusinessRequest.of(input));assertEquals(result.detail(),Result.Status.SUCCESS,result.status());return app.ids().entityId(op.requestKey(),"document");
    }
    void product(){execute(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","虚构商品","件",new BigDecimal("3.33"),null),0));}
    int stock(StockEntry.Position position) {return state().records("STOCK_ENTRY").stream().map(r -> r.as(StockEntry.class)).filter(s -> s.position()==position).mapToInt(StockEntry::quantityDelta).sum();}
    String sale(){return execute(manager,"SALE",new AOperations.Sale(List.of(new AOperations.Item("00123456",2)),true));}
    AftersaleRequest refund(String line,String key){return new AftersaleRequest(line,RedemptionKind.RETURN,1,false,false,AftersaleReasonKind.OTHER,"虚构退货原因",null,0,key);}
    String line(String sale){return state().records("DOC_LINE").stream().filter(r -> r.data().path("documentId").asText().equals(sale)).findFirst().orElseThrow().id();}
    @Test public void realReceivingReplenishmentSaleReadRefundBlockedReversalThenReleaseAndReverseApprovedReceipt() throws Exception {
        product();String receipt=execute(manager,"RECEIPT_CREATE",new AOperations.ReceiptCreate(List.of(new AOperations.Item("00123456",10)),List.of()));
        execute(manager,"RECEIPT_REVIEW",new AOperations.ReceiptReview(receipt,1,true,"虚构真实链审核",List.of()));
        assertEquals(10,stock(StockEntry.Position.W));
        var ops=operations();assertTrue(ops.replenish(clerk,"00123456",5).success());
        String transfer=state().records("DOCUMENT").stream().filter(r -> r.data().path("kind").asText().equals("REPLENISHMENT")).findFirst().orElseThrow().id();
        String sale=sale(); String key=app.ids().newRequestKey();
        assertTrue(ops.aftersale(clerk,refund(line(sale),key)).success());
        String aftersale=state().records("AFTERSALE").get(0).id();
        assertEquals(1,state().records("LOSS_RECORD").size());assertNull(state().records("LOSS_RECORD").get(0).data().get("position").textValue());
        byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));
        var blocked=ops.reverseDocument(manager,sale,"虚构依赖阻断",app.ids().newRequestKey());assertFalse(blocked.message(),blocked.success());
        assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));
        String reverseKey=app.ids().newRequestKey();assertTrue(ops.reverseDocument(manager,aftersale,"撤销虚构退货",reverseKey).success());
        assertTrue(ops.retry(manager,reverseKey).success());
        assertTrue(ops.reverseDocument(manager,sale,"撤销虚构销售",app.ids().newRequestKey()).success());
        assertTrue(ops.reverseDocument(manager,transfer,"撤销虚构补货",app.ids().newRequestKey()).success());
        var reversed=ops.reverseDocument(manager,receipt,"撤销虚构已审收货",app.ids().newRequestKey());assertTrue(reversed.message(),reversed.success());
        assertEquals(0,stock(StockEntry.Position.S));assertEquals(0,stock(StockEntry.Position.W));
        assertEquals("VOIDED",state().find("DOCUMENT",receipt).orElseThrow().data().path("status").asText());
        app.close();open();assertEquals(0,stock(StockEntry.Position.W));
    }
    @Test public void realTraceFailureUnknownResponseRetryCancelAndDeleteReplay() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构合法期初"));String sale=sale();
        String target=app.ids().entityId(app.ids().newRequestKey(),"draft");var read=op(clerk,AftersaleReadRequest.OPERATION,target);var request=new AftersaleReadRequest(sale,target,List.of(line(sale)));
        assertFalse(b.auth().login("fictional-clerk",UUID.randomUUID().toString().toCharArray()) instanceof AuthResult.Success);
        assertNull(app.originalSales().readForAftersale(app.submissions(),new OperationContext(new Object(),read.requestKey(),read.operation(),target),request).value());
        byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));fail=true;
        assertNull(app.originalSales().readForAftersale(app.submissions(),read,request).value());assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));fail=false;
        SubmissionPort lost=new SubmissionPort(){
            public Result<WorkingCopy> open(OperationContext o){return app.submissions().open(o);}
            public Result<CommitReceipt> lookupResult(OperationContext o,BusinessRequest r){return app.submissions().lookupResult(o,r);}
            public Result<CommitReceipt> submit(OperationContext o,BusinessRequest r,WorkingCopy w,ChangeSet d){assertEquals(Result.Status.SUCCESS,app.submissions().submit(o,r,w,d).status());return Result.failed("UNKNOWN","Injected response loss after commit");}
        };
        assertNull(app.originalSales().readForAftersale(lost,read,request).value());long ordinal=state().baseLast();
        var replay=app.originalSales().readForAftersale(app.submissions(),read,request);assertTrue(replay.value().trace().replayed());assertEquals(ordinal,state().baseLast());
        var actor=new ActorContext(clerk.accountId(),OperationContext.Role.CLERK);var drafts=new AftersaleDraftService(app.storage());
        var delete=op(clerk,DraftDeletion.OPERATION,target);assertFalse(drafts.delete(delete,actor).hasValue());
        assertEquals(Result.Status.SUCCESS,drafts.cancel(op(clerk,AftersaleDraftOperations.CANCEL,target),actor).status());
        assertEquals("AFTERSALE_CLOSED",app.originalSales().readForAftersale(app.submissions(),read,request).code());
        String untraced=app.ids().entityId(app.ids().newRequestKey(),"draft");assertEquals(Result.Status.SUCCESS,drafts.create(op(clerk,AftersaleDraftOperations.CREATE,untraced),actor).status());
        var deletion=op(clerk,DraftDeletion.OPERATION,untraced);before=Files.readAllBytes(root.resolve("snapshot.json"));fail=true;
        assertEquals(Result.Status.FAILED,drafts.delete(deletion,actor).status());assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));fail=false;
        assertEquals(Result.Status.SUCCESS,drafts.delete(deletion,actor).status());assertTrue(drafts.delete(deletion,actor).value().replayed());
        assertTrue(state().find("DOCUMENT",untraced).isEmpty());
        app.close();open();assertEquals("CANCELLED",state().find("DOCUMENT",target).orElseThrow().data().path("status").asText());
    }
    @Test public void completeCuiUsesRealAuthProductOpeningSaleAndAuthorizedHistory() throws Exception {
        String script=String.join("\n","fictional-manager",password,"7","new","00123456","虚构 CUI 商品","件","3.33","8","00123456","5","S","虚构期初","13","00123456","1","","y","14","DOCUMENT","","0","exit","");
        ByteArrayOutputStream capture=new ByteArrayOutputStream();
        new CuiApp(b.auth(),operations(),new InputValidator(),new BufferedReader(new StringReader(script)),new PrintStream(capture,true,StandardCharsets.UTF_8)).run();
        String output=capture.toString(StandardCharsets.UTF_8);assertTrue(output,output.contains("Login successful"));assertTrue(output,output.contains("SALE"));assertFalse(output.contains(password));
        assertEquals(3,state().baseLast());assertEquals(4,stock(StockEntry.Position.S));
        assertFalse(operations().inspect(clerk,"DOCUMENT",null).message().contains(state().records("SALE").get(0).id()));
    }
    @Test public void dispositionDoesNotDeductTwiceAndAdminCorrectionKeepsOriginalAndDelta() {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构合法期初"));
        var ops=operations();assertTrue(ops.recordLoss(manager,"00123456",nz.ac.aut.pdc.supermarket.stock.Position.S,2,"虚构破损").success());
        String loss=state().records("LOSS_RECORD").get(0).id();int entries=state().records("STOCK_ENTRY").size();
        assertTrue(ops.executeA(manager,LossWorkflow.DISPOSE,new LossWorkflow.Request(loss,0,"虚构已处理","处置说明"),null).success());
        assertEquals(entries,state().records("STOCK_ENTRY").size());assertEquals(8,stock(StockEntry.Position.S));
        assertFalse(ops.executeA(manager,LossWorkflow.CORRECT,new LossWorkflow.Request(loss,1,"修正","核实数量"),null).success());
        var corrected=ops.executeA(admin,LossWorkflow.CORRECT,new LossWorkflow.Request(loss,1,"修正后处置","核实数量"),null);assertTrue(corrected.message(),corrected.success());
        assertEquals(9,stock(StockEntry.Position.S));assertEquals(2,state().find("LOSS_RECORD",loss).orElseThrow().data().path("quantity").asInt());
        assertEquals(1,LossWorkflow.effective(state(),loss).quantity());assertEquals(1,state().records("LOSS_CORRECTION").size());
    }
    @Test public void reversalRejectsMissingEffectsSmugglingAndClerkAndSupportsOtherProcessWriterLock() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));String unrelatedSale=sale();String sale=sale();
        var op=op(manager,"REVERSAL",sale);var request=BusinessRequest.of(new nz.ac.aut.pdc.supermarket.reversal.DocumentReversalCommand(sale,"虚构整单冲销",op.requestKey()));
        var wc=app.submissions().open(op).value();var actor=new ActorContext(manager.accountId(),OperationContext.Role.MANAGER);
        var correct=new nz.ac.aut.pdc.supermarket.reversal.ReversalChangeSetAssembler().assemble(wc,sale,op,actor,request,app.ids());
        byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));
        for(String omitted:List.of("STOCK_ENTRY","MONEY_ENTRY","DOC_LINE","REVERSAL")) {
            var incomplete=new ChangeSet(correct.inserts().stream().filter(r -> !r.table().equals(omitted)).toList(),correct.patches());
            assertEquals(Result.Status.REJECTED,app.submissions().submit(op,request,wc,incomplete).status());
            assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));
        }
        String foreignStock=wc.records("STOCK_ENTRY").stream().map(r -> r.as(StockEntry.class)).filter(r -> wc.find("DOC_LINE",r.sourceLineId()).orElseThrow().data().path("documentId").asText().equals(unrelatedSale)).findFirst().orElseThrow().stockEntryId();
        String foreignMoney=wc.records("MONEY_ENTRY").stream().map(r -> r.as(MoneyEntry.class)).filter(r -> r.documentId().equals(unrelatedSale)).findFirst().orElseThrow().moneyEntryId();
        for(String kind:List.of("STOCK_ENTRY","MONEY_ENTRY")) {
            var changed=new ChangeSet(correct.inserts().stream().map(r -> {
                if(r.table().equals("STOCK_ENTRY") && kind.equals("STOCK_ENTRY")) {var x=r.as(StockEntry.class);return RecordData.of(r.table(),r.id(),new StockEntry(x.stockEntryId(),x.sourceLineId(),x.position(),x.quantityDelta(),x.eventKey(),x.receiptReviewId(),foreignStock,x.transferKey(),0));}
                if(r.table().equals("MONEY_ENTRY") && kind.equals("MONEY_ENTRY")) {var x=r.as(MoneyEntry.class);return RecordData.of(r.table(),r.id(),new MoneyEntry(x.moneyEntryId(),x.documentId(),x.amountSigned(),x.requestKey(),foreignMoney));}
                return r;
            }).toList(),correct.patches());
            assertEquals(Result.Status.REJECTED,app.submissions().submit(op,request,wc,changed).status());assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));
        }
        var smuggled=new ChangeSet.Builder().merge(correct).patch(new ChangeSet.Patch("PRODUCT","00123456",1,"{\"name\":\"smuggled\"}")).build();
        assertEquals(Result.Status.REJECTED,app.submissions().submit(op,request,wc,smuggled).status());
        assertFalse(operations().reverseDocument(clerk,sale,"越权尝试",app.ids().newRequestKey()).success());
        var javaExecutable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process process=new ProcessBuilder(javaExecutable,"-cp",System.getProperty("java.class.path"),Main.class.getName(),"--check","--project-root",project.toString()).redirectErrorStream(true).start();
        boolean finished=process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS);
        if(!finished)process.destroyForcibly();assertTrue("Child startup timed out",finished);
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(output,1,process.exitValue());assertTrue(output,output.contains("WRITE_BLOCKED"));
    }
    @Test public void multipleRefundLinesAllocateTailByStableIdAndQualitySwapIgnoresChangedCurrentPrice() {
        execute(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","虚构商品","件",new BigDecimal("3.34"),null),0));
        execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));
        var promotion=new nz.ac.aut.pdc.supermarket.promotion.Promotion("fictional-promo",nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.REDUCTION,null,new BigDecimal("10.00"),new BigDecimal("0.02"),null,"2026-10-21","2026-10-21",Set.of("00123456"));
        execute(manager,"PROMOTION_PUT",new AOperations.PromotionPut(promotion,0));
        String sale=execute(manager,"SALE",new AOperations.Sale(List.of(new AOperations.Item("00123456",3)),true));
        String target=app.ids().entityId(app.ids().newRequestKey(),"draft");var read=op(clerk,AftersaleReadRequest.OPERATION,target);
        assertNotNull(app.originalSales().readForAftersale(app.submissions(),read,new AftersaleReadRequest(sale,target,List.of(line(sale)))).value());
        var context=op(clerk,"AFTERSALE",target);var input=refund(line(sale),context.requestKey());
        var row=new AftersaleCommandLine(input.saleLineId(),input.kind(),1,false,false,input.reasonKind(),input.reasonText(),null,0,false);
        var command=new AftersaleCommand(context.requestKey(),null,List.of(row,row,row));var wc=app.submissions().open(context).value();
        var engine=AftersaleEngine.create(wc,app.ids(),context.requestKey());var result=new AftersaleSubmissionService(engine,new AftersaleChangeSetAssembler(),app.storage()).submit(command,wc,context,new ActorContext(clerk.accountId(),OperationContext.Role.CLERK));
        assertEquals(result.detail(),Result.Status.SUCCESS,result.status());
        var redemptions=state().records("REDEMPTION").stream().map(r -> r.as(Redemption.class)).sorted(Comparator.comparing(Redemption::redemptionId)).toList();
        assertEquals(List.of(new BigDecimal("3.33"),new BigDecimal("3.33"),new BigDecimal("3.34")),redemptions.stream().map(Redemption::amount).toList());
        String another=sale();execute(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","虚构调价","件",new BigDecimal("5.00"),null),1));
        int money=state().records("MONEY_ENTRY").size();
        var quality=new AftersaleRequest(line(another),RedemptionKind.EXCHANGE,1,true,true,AftersaleReasonKind.QUALITY,null,"00123456",1,app.ids().newRequestKey());
        var swapped=operations().aftersale(clerk,quality);assertTrue(swapped.message(),swapped.success());
        assertEquals(money,state().records("MONEY_ENTRY").size());
        assertNotNull(state().records("REPLACEMENT").get(0).as(Replacement.class).qualityRedemptionId());
    }
    @Test public void allAuthenticatedRolesUseSinglePointReadAndWrongVoucherOrForeignDraftLeavesNoTrace() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));String sale=sale();
        for(var actor:List.of(clerk,manager,admin)) {
            String target=app.ids().entityId(app.ids().newRequestKey(),"draft");var context=op(actor,AftersaleReadRequest.OPERATION,target);
            var result=app.originalSales().readForAftersale(app.submissions(),context,new AftersaleReadRequest(sale,target,List.of(line(sale))));
            assertEquals(result.detail(),Result.Status.VALUE,result.status());
            assertEquals(actor.accountId(),state().find("DOC_EVENT",result.value().eventId()).orElseThrow().data().path("actorId").asText());
            if(actor==manager) {
                byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));
                var denied=app.originalSales().readForAftersale(app.submissions(),op(clerk,AftersaleReadRequest.OPERATION,target),new AftersaleReadRequest(sale,target,List.of(line(sale))));
                assertEquals("FORBIDDEN",denied.code());assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));
            }
        }
        byte[] before=Files.readAllBytes(root.resolve("snapshot.json"));
        String target="wrong-voucher-draft";
        assertNull(app.originalSales().readForAftersale(app.submissions(),op(clerk,AftersaleReadRequest.OPERATION,target),new AftersaleReadRequest("wrong-exact-voucher",target,List.of())).value());
        assertArrayEquals(before,Files.readAllBytes(root.resolve("snapshot.json")));assertTrue(state().find("DOCUMENT",target).isEmpty());
    }
    @Test public void preexistingLegacyReadEventRemainsReadableAndIsNeverRewritten() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));String sale=sale();String line=line(sale);
        String target="fictional-legacy-draft";var actor=new ActorContext(clerk.accountId(),OperationContext.Role.CLERK);
        assertEquals(Result.Status.SUCCESS,new AftersaleDraftService(app.storage()).create(op(clerk,AftersaleDraftOperations.CREATE,target),actor).status());
        // Historical writer fixture only: reproduces the old B event shape without altering any balances.
        var legacy=RecordData.of("DOC_EVENT",target+"-READ",new DocEvent(target+"-READ",target,2,"READ_FOR_AFTERSALE",clerk.accountId(),clock.instant().toString(),JsonCodec.write(Map.of("originalSaleLineId",line,"returnedLineId",line))));
        var oldIdentity=new SharedIdentityResolver(b.auth());app.close();app=null;
        try(var historical=new JsonStore(root,oldIdentity,Map.of("LEGACY_FIXTURE",(o,a,r,w,d,t) -> Result.value(null)),w -> {},clock::instant)) {
            var context=new OperationContext(clerk.session(),UUID.randomUUID().toString(),"LEGACY_FIXTURE",target);
            assertEquals(Result.Status.SUCCESS,historical.submit(context,BusinessRequest.of(Map.of("draftId",target)),historical.open(context).value(),new ChangeSet(List.of(legacy),List.of())).status());
        }
        open();assertEquals(legacy,state().find("DOC_EVENT",legacy.id()).orElseThrow());
        var result=app.originalSales().readForAftersale(app.submissions(),op(clerk,AftersaleReadRequest.OPERATION,target),new AftersaleReadRequest(sale,target,List.of(line)));
        assertEquals(result.detail(),Result.Status.VALUE,result.status());assertEquals(legacy,state().find("DOC_EVENT",legacy.id()).orElseThrow());
    }
    @Test public void realCuiRefundAndReversalKeepOriginalKeysAcrossLoginAndRestart() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));String sale=sale();
        String aftersaleKey=app.ids().newRequestKey(),reversalKey=app.ids().newRequestKey();
        String target=app.ids().entityId(aftersaleKey,"aftersale");
        String refundInput=String.join("\n","3",line(sale),"RETURN","1","n","n","虚构退货",aftersaleKey,"y");
        String script=String.join("\n","fictional-clerk",password,refundInput,refundInput,"0","fictional-manager",password,"6",target,"虚构冲销",reversalKey,"0","exit","");
        ByteArrayOutputStream capture=new ByteArrayOutputStream();
        new CuiApp(b.auth(),operations(),new InputValidator(),new BufferedReader(new StringReader(script)),new PrintStream(capture,true,StandardCharsets.UTF_8)).run();
        String output=capture.toString(StandardCharsets.UTF_8);assertTrue(output,output.toLowerCase(Locale.ROOT).contains("committed:"));assertTrue(output,output.contains("Whole-document reversal committed"));
        long ordinal=state().baseLast();assertEquals(2,state().records("REDEMPTION").size());assertEquals(3,state().records("MONEY_ENTRY").size());
        assertEquals(1,state().records("DOC_EVENT").stream().filter(r -> r.data().path("action").asText().equals("READ_FOR_AFTERSALE")).count());
        app.close();open();capture.reset();
        script=String.join("\n","fictional-manager",password,"6",target,"虚构冲销",reversalKey,"0","exit","");
        new CuiApp(b.auth(),operations(),new InputValidator(),new BufferedReader(new StringReader(script)),new PrintStream(capture,true,StandardCharsets.UTF_8)).run();
        assertTrue(capture.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("committed:"));assertEquals(ordinal,state().baseLast());
    }
    @Test public void actualStartupRejectsCorruptSnapshotAndDoesNotInstallAnEmptyReplacement() throws Exception {
        product();app.close();app=null;
        String broken="{invalid snapshot";Files.writeString(root.resolve("snapshot.json"),broken);
        ByteArrayOutputStream error=new ByteArrayOutputStream();
        assertEquals(1,Main.run(new String[]{"--check","--project-root",project.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error,true,StandardCharsets.UTF_8)));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("RECOVERY_WRITE_BLOCKED"));assertEquals(broken,Files.readString(root.resolve("snapshot.json")));
    }
    @Test public void savedReadQuoteCanBeCancelledAndStaleSaleQuoteCannotSilentlyReprice() {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));
        var ops=operations();String key=app.ids().newRequestKey();var rows=List.of(new AOperations.Item("00123456",1));
        assertTrue(ops.quoteSale(manager,rows,key).success());assertTrue(state().records("SALE").isEmpty());
        execute(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","虚构调价","件",new BigDecimal("4.00"),null),1));
        assertFalse(ops.executeA(manager,"SALE",new AOperations.Sale(rows,true),key).success());assertTrue(state().records("SALE").isEmpty());
        String sale=sale();int money=state().records("MONEY_ENTRY").size();key=app.ids().newRequestKey();
        assertTrue(ops.quoteAftersale(clerk,List.of(refund(line(sale),key)),key).success());
        assertTrue(state().records("REDEMPTION").isEmpty());assertEquals(money,state().records("MONEY_ENTRY").size());
        String draft=app.ids().entityId(key,"aftersale");assertTrue(ops.cancelAftersaleDraft(clerk,draft).success());
        assertEquals("CANCELLED",state().find("DOCUMENT",draft).orElseThrow().data().path("status").asText());assertEquals(money,state().records("MONEY_ENTRY").size());
    }
    @Test public void realCuiMixedAftersaleUsesOneQuotaAndOneNetSettlement() {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",20),StockEntry.Position.S,"虚构期初"));
        execute(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123457","虚构替换品","件",new BigDecimal("1.00"),null),0));
        execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123457",5),StockEntry.Position.S,"虚构期初"));
        String sale=execute(manager,"SALE",new AOperations.Sale(List.of(new AOperations.Item("00123456",3)),true));
        String key=app.ids().newRequestKey(),line=line(sale);
        String script=String.join("\n","fictional-clerk",password,"18",line,"RETURN","1","n","n","虚构退货","y",line,"EXCHANGE","1","n","n","虚构换货","00123457","2","n",key,"y","0","exit","");
        ByteArrayOutputStream capture=new ByteArrayOutputStream();
        new CuiApp(b.auth(),operations(),new InputValidator(),new BufferedReader(new StringReader(script)),new PrintStream(capture,true,StandardCharsets.UTF_8)).run();
        String output=capture.toString(StandardCharsets.UTF_8);assertTrue(output,output.toLowerCase(Locale.ROOT).contains("committed:"));
        var target=app.ids().entityId(key,"aftersale");
        var money=state().records("MONEY_ENTRY").stream().map(r -> r.as(MoneyEntry.class)).filter(r -> r.documentId().equals(target)).toList();
        assertEquals(1,money.size());assertEquals(new BigDecimal("-4.66"),money.get(0).amountSigned());
        var history=new SharedQuotaLedgerReader(state()).effectiveFor(line);
        var quota=new QuotaViewBuilder().build(new SharedSaleReader(state()).readLine(line).orElseThrow(),history);
        assertEquals(1,quota.remainingQty());assertEquals(new BigDecimal("3.33"),quota.remainingAmount());
        assertEquals(2,state().records("LOSS_RECORD").size());
    }
    @Test public void abruptSeparateJvmAfterCommitRecoversOriginalTraceWithoutDuplicate() throws Exception {
        product();execute(manager,"OPENING",new AOperations.Opening(new AOperations.Item("00123456",10),StockEntry.Position.S,"虚构期初"));String sale=sale();
        String target="fictional-crashed-read",key=app.ids().newRequestKey();app.close();app=null;
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        var command=new ProcessBuilder(javaExecutable,"-cp",System.getProperty("java.class.path"),CrashAfterCommitProbe.class.getName(),project.toString(),sale,target,key).redirectErrorStream(true);
        command.environment().put("P1_TEST_SECRET",password);
        Process process=command.start();boolean finished=process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS);
        if(!finished)process.destroyForcibly();assertTrue("Crash probe timeout",finished);
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(output,23,process.exitValue());
        open();long ordinal=state().baseLast();
        String eventId=app.ids().entityId(key,"aftersale-read");var event=state().find("DOC_EVENT",eventId).orElseThrow();
        var context=new OperationContext(clerk.session(),key,AftersaleReadRequest.OPERATION,target);
        var result=app.originalSales().readForAftersale(app.submissions(),context,new AftersaleReadRequest(sale,target,List.of()));
        assertTrue(result.value().trace().replayed());assertEquals(ordinal,state().baseLast());assertEquals(event,state().find("DOC_EVENT",eventId).orElseThrow());
        assertEquals(1,state().records("AFTERSALE").size());assertTrue(state().records("REDEMPTION").isEmpty());
    }
}
