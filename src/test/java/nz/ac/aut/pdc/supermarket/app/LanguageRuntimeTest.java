package nz.ac.aut.pdc.supermarket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.supermarket.auth.*;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.cui.*;
import nz.ac.aut.pdc.supermarket.i18n.Texts;

/** Real services and isolated files; the before-replace hook alone injects failure.
  */
public class LanguageRuntimeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Application app; private RepositoryBIntegration integration; private SharedOperations ops;
    private CuiActor manager,clerk; private String password; private DataRoot root; private boolean fail;
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"),ZoneOffset.UTC);
    @Before public void open() throws Exception {
        Path project=temp.newFolder().toPath();Files.writeString(project.resolve("pom.xml"),"fictional");
        Files.writeString(project.resolve("README.md"),"fictional");Files.createDirectory(project.resolve("docs"));root=DataRoot.forProject(project);
        password=UUID.randomUUID().toString();String hash=new Sha256PasswordHasher().hash(password.toCharArray());
        integration=new RepositoryBIntegration(List.of(new JsonStore.Credential("fictional-manager","fictional-manager",hash,OperationContext.Role.MANAGER),
            new JsonStore.Credential("fictional-clerk","fictional-clerk",hash,OperationContext.Role.CLERK),
            new JsonStore.Credential("fictional-literal-language","language",hash,OperationContext.Role.MANAGER),
            new JsonStore.Credential("fictional-literal-login","login",hash,OperationContext.Role.MANAGER)));
        app=new Application(root,integration,clock,p -> {if(fail)throw new IOException("Injected failure");});
        var identity=(AuthResult.Success)integration.auth().login("fictional-manager",password.toCharArray());
        manager=new CuiActor(identity.accountId(),identity.role(),identity.session());
        var cashier=(AuthResult.Success)integration.auth().login("fictional-clerk",password.toCharArray());clerk=new CuiActor(cashier.accountId(),cashier.role(),cashier.session());ops=(SharedOperations)integration.cuiOperations(app);
    }
    @After public void close() throws Exception {if(app!=null)app.close();}
    private String cui(String script,Texts texts) {
        var bytes=new ByteArrayOutputStream();
        new CuiApp(integration.auth(),ops,new InputValidator(),new BufferedReader(new StringReader(script)),new PrintStream(bytes,true,StandardCharsets.UTF_8),texts).run();
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    private String logged(String script){return "fictional-manager\n"+password+"\n"+script;}
    private WorkingCopy state(){return app.submissions().open(new OperationContext(manager.session(),app.ids().newRequestKey(),"QUERY",null)).value();}
    @Test public void startupArgumentsAreExplicitAndInvalidOnesFailBeforeStorage() {
        for(String[] args:List.of(new String[]{"--help"},new String[]{"--lang","en","--help"},new String[]{"--lang","zh","--help"},new String[]{"--help","--lang","zh"})) {
            var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);
            assertEquals(0,Main.run(args,stream,stream));String text=bytes.toString(StandardCharsets.UTF_8);
            assertEquals(Arrays.asList(args).contains("zh"),Pattern.compile("[\\p{IsHan}]").matcher(text).find());
        }
        for(String[] args:List.of(new String[]{"--lang","fr"},new String[]{"--lang"},new String[]{"--lang","--check"},new String[]{"--help","--lang","fr"},new String[]{"--help","--lang"})) {
            var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);
            assertEquals(1,Main.run(args,stream,stream));assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("INVALID_LANGUAGE"));
        }
    }
    @Test public void preAndPostLoginSwitchingSurvivesLogoutWithoutAnyCommit() throws Exception {
        Texts texts=new Texts("en");long before=state().baseLast();String snapshot=Files.readString(root.path().resolve("snapshot.json"));
        String output=cui("language\nzh\n"+logged("language\nen\nlanguage\nzh\n0\nexit\n"),texts);
        assertTrue(output,output.contains("登录成功"));assertTrue(output,output.contains("Language changed to English."));
        assertTrue(output,output.contains("已退出登录\n用户名"));assertEquals("zh",texts.language());
        assertEquals(before,state().baseLast());assertEquals(snapshot,Files.readString(root.path().resolve("snapshot.json")));
        assertTrue(cui("exit\n",new Texts("en")).startsWith("== Supermarket"));
    }
    @Test public void navigationCommandsDoNotMakeExistingUsernamesUnreachable() {
        for(String name:List.of("language","login")) {
            String output=cui("login\n"+name+"\n"+password+"\n0\nexit\n",new Texts("en"));
            assertTrue(output,output.contains("Login successful: fictional-literal-"+name));
            assertEquals(0,state().baseLast());
        }
    }
    @Test public void sameInputInBothLanguagesHasSameEffectsAndKeepsOriginalUserText() throws Exception {
        String key=app.ids().newRequestKey();
        var request=new AOperations.ProductPut(new Product("00123456","中文原文商品","件",new BigDecimal("3.33"),null),0);
        var submitted=ops.executeA(manager,"PRODUCT_PUT",request,key);assertTrue(submitted.message(),submitted.success());
        String original=Files.readString(root.path().resolve("snapshot.json"));long ordinal=state().baseLast();
        String en=submitted.render(new Texts("en")),zh=submitted.render(new Texts("zh"));
        assertTrue(en,en.contains("Committed"));assertTrue(zh,zh.contains("已提交"));
        for(String lang:List.of("en","zh")) {
            var retry=ops.retry(manager,key);assertTrue(retry.message(),retry.success());
            assertEquals(en,retry.render(new Texts("en")));assertEquals(zh,retry.render(new Texts("zh")));
            String projection=ops.inspect(manager,"PRODUCT",null).render(new Texts(lang));
            assertTrue(projection,projection.contains("中文原文商品"));assertTrue(projection,projection.contains("件"));
            assertEquals(original,Files.readString(root.path().resolve("snapshot.json")));assertEquals(ordinal,state().baseLast());
        }
        assertNoLanguageFields(JsonCodec.tree(original));
    }
    @Test public void equivalentCuiInputsProduceSameBusinessFacts() throws Exception {
        String form="7\nnew\n00123456\nFictional product\nitem\n3.33\n8\n00123456\n5\nS\nFictional opening\n13\n00123456\n1\n\ny\n14\nDOCUMENT\n\n0\nexit\n";
        String output=cui(logged(form),new Texts("en"));assertFalse(output,Pattern.compile("[\\p{IsHan}]").matcher(output).find());
        assertFalse(output,output.contains(Texts.UNAVAILABLE));assertTrue(output,output.contains("Fictional product"));
        var english=businessFacts(); app.close(); open();
        String chinese=cui(logged(form),new Texts("zh"));assertTrue(chinese,chinese.contains("登录成功"));
        assertFalse(chinese,chinese.contains(Texts.UNAVAILABLE));assertEquals(english,businessFacts());
    }
    private void assertNoLanguageFields(com.fasterxml.jackson.databind.JsonNode node) {
        if(node.isObject())node.fields().forEachRemaining(field -> {
            String name=field.getKey().toLowerCase(Locale.ROOT);
            assertFalse(field.getKey(),name.contains("language") || name.contains("locale"));
            if(field.getKey().equals("json") && field.getValue().isTextual())assertNoLanguageFields(JsonCodec.tree(field.getValue().asText()));
            else assertNoLanguageFields(field.getValue());
        });
        else if(node.isArray())node.forEach(this::assertNoLanguageFields);
    }
    private Object businessFacts() {
        assertEquals(3,state().baseLast());assertEquals(1,state().records("SALE").size());assertEquals(2,state().records("STOCK_ENTRY").size());
        var quote=state().records("SALE").get(0).as(AOperations.SaleRecord.class).quote();
        return List.of(state().records("PRODUCT").get(0).as(Product.class),quote.grossTotal(),quote.discountTotal(),quote.paidTotal(),
            state().records("STOCK_ENTRY").stream().map(r -> r.data().path("quantityDelta").asInt()).toList(),
            state().records("MONEY_ENTRY").stream().map(r -> r.data().path("amountSigned").decimalValue()).toList());
    }
    @Test public void originalKeyRetryAfterMenuSwitchDoesNotRepeatTheBusinessEffect() throws Exception {
        String key=app.ids().newRequestKey();
        assertTrue(ops.executeA(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","Fictional product","item",BigDecimal.ONE,null),0),key).success());
        String before=Files.readString(root.path().resolve("snapshot.json"));
        String output=cui(logged("language\nzh\n15\n"+key+"\nlanguage\nen\n15\n"+key+"\n0\nexit\n"),new Texts("en"));
        assertTrue(output,output.contains("已提交"));assertTrue(output,output.contains("Committed:"));
        assertEquals(before,Files.readString(root.path().resolve("snapshot.json")));assertEquals(1,state().baseLast());
    }
    @Test public void realRejectionAndPersistenceFailureAreBilingualAndPreserveState() {
        var bad=ops.executeA(manager,"SALE",new AOperations.Sale(List.of(new AOperations.Item("00123456",1)),true),app.ids().newRequestKey());
        assertFalse(bad.success());assertTrue(bad.render(new Texts("en")).contains("Request rejected"));assertTrue(bad.render(new Texts("zh")).contains("请求被拒绝"));
        fail=true;
        var failed=ops.executeA(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","Fictional product","item",BigDecimal.ONE,null),0),app.ids().newRequestKey());
        assertFalse(failed.success());assertTrue(failed.render(new Texts("en")).contains("Technical failure"));assertTrue(failed.render(new Texts("zh")).contains("技术失败"));
        assertTrue(failed.render(new Texts("en")).contains("PERSISTENCE_FAILED"));assertEquals(0,state().baseLast());
    }
    @Test public void realReceivingSaleAftersaleAndQueriesHaveCompleteDisplayResources() {
        checked(ops.executeA(manager,"PRODUCT_PUT",new AOperations.ProductPut(new Product("00123456","Fictional product","item",new BigDecimal("3.33"),null),0),null));
        checked(ops.executeA(manager,"RECEIPT_CREATE",new AOperations.ReceiptCreate(List.of(new AOperations.Item("00123456",8)),List.of()),null));
        String receipt=state().records("DOCUMENT").stream().filter(r -> r.data().path("kind").asText().equals("RECEIPT")).findFirst().orElseThrow().id();
        checked(ops.executeA(manager,"RECEIPT_REVIEW",new AOperations.ReceiptReview(receipt,1,true,"Fictional review",List.of()),null));
        checked(ops.replenish(manager,"00123456",5));
        var rows=List.of(new AOperations.Item("00123456",2));String key=app.ids().newRequestKey();
        checked(ops.quoteSale(manager,rows,key));checked(ops.executeA(manager,"SALE",new AOperations.Sale(rows,true),key));
        String sale=state().records("SALE").get(0).id();
        String line=state().records("DOC_LINE").stream().filter(r -> r.data().path("documentId").asText().equals(sale)).findFirst().orElseThrow().id();
        String refundKey=app.ids().newRequestKey();
        var refund=new AftersaleRequest(line,nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind.RETURN,1,false,false,nz.ac.aut.pdc.supermarket.aftersale.AftersaleReasonKind.OTHER,"Fictional reason",null,0,refundKey);
        checked(ops.quoteAftersale(clerk,List.of(refund),refundKey));checked(ops.aftersaleBatch(clerk,List.of(refund),refundKey));
        for(String category:List.of("PRODUCT","STOCK","PROMOTION","DOCUMENT","LOSS","QUOTA")) checked(ops.inspect(manager,category,category.equals("QUOTA")?line:null));
    }
    private void checked(OperationFeedback feedback) {
        assertTrue(feedback.message(),feedback.success());
        for(String lang:List.of("en","zh")) {
            String text=feedback.render(new Texts(lang));assertFalse(text,text.contains(Texts.UNAVAILABLE));
            assertFalse(text,text.contains("{\""));assertFalse(text,text.contains("Quote["));
            if(lang.equals("en"))assertFalse(text,Pattern.compile("[\\p{IsHan}]").matcher(text.replace("顾客交回待处置（不回库）","")).find());
        }
    }
    @Test public void loginRejectionAndTechnicalFailureUseSelectedLanguageWithoutLeakingRawMessages() {
        for(String lang:List.of("en","zh")) {
            String rejected=cui("fictional-manager\nwrong\nexit\n",new Texts(lang));
            assertTrue(rejected,rejected.contains("INVALID_CREDENTIALS"));
            var unavailable=new AuthService() {
                public AuthResult login(String username,char[] password){return new AuthResult.Failed("AUTH_TECHNICAL_FAILURE","故障细节不能作为系统展示");}
                public AuthResult resolve(SessionRef session){return login("",new char[0]);}
            };
            var bytes=new ByteArrayOutputStream();
            new CuiApp(unavailable,ops,new InputValidator(),new BufferedReader(new StringReader("fictional\nwrong\nexit\n")),new PrintStream(bytes,true,StandardCharsets.UTF_8),new Texts(lang)).run();
            String technical=bytes.toString(StandardCharsets.UTF_8);assertTrue(technical,technical.contains("AUTH_TECHNICAL_FAILURE"));
            assertFalse(technical,technical.contains("故障细节"));
            if(lang.equals("en")) {assertFalse(rejected,Pattern.compile("[\\p{IsHan}]").matcher(rejected).find());assertFalse(technical,Pattern.compile("[\\p{IsHan}]").matcher(technical).find());}
        }
    }
}
