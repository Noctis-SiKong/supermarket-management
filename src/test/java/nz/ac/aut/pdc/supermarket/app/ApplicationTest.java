package nz.ac.aut.pdc.supermarket.app;

import nz.ac.aut.pdc.fixture.FictionalDependencies;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.Assert.*;

/** Real bootstrap/A/files, explicitly fictional B ports. No provider is registered in production. */
public class ApplicationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    Path project() throws Exception {
        Path p=temp.newFolder().toPath(); Files.writeString(p.resolve("README.md"),"fixture");
        Files.writeString(p.resolve("pom.xml"),"fixture"); Files.createDirectory(p.resolve("docs")); return p;
    }
    static class BDouble implements BIntegration {
        final FictionalDependencies deps=new FictionalDependencies();
        Supplier<List<JsonStore.Credential>> credentials;
        public IdentityResolver identities(Supplier<List<JsonStore.Credential>> credentials) {
            this.credentials=credentials;
            return session -> { credentials.get(); return deps.resolve(session); };
        }
        public AOperations.StockDependency stock(){ return deps; }
        public OriginalSaleAccess.DraftAccess drafts(){ return new OriginalSaleAccess.DraftAccess() {
            public Result<ChangeSet> prepareTarget(OperationContext o,ActorContext a,AftersaleReadRequest r,WorkingCopy w){ return Result.failed("TEST_NOT_CONNECTED","No fictional draft behavior requested"); }
            public Result<Void> reviewTarget(OperationContext o,ActorContext a,AftersaleReadRequest r,WorkingCopy w,ChangeSet c,Instant t){ return Result.failed("TEST_NOT_CONNECTED","No fictional draft behavior requested"); }
        }; }
        public Map<String,CommitValidator> validators(){ return Map.of(); }
        public JsonStore.IntegrityValidator integrity(){ return wc -> {}; }
        public List<JsonStore.Credential> initialAccounts(){ return List.of(); }
        public void run(Application app){ throw new AssertionError("No fictional CUI"); }
    }
    @Test public void bootstrapRealStorageLegalOpeningSaleAndRecoveryWithoutSeedingGoods() throws Exception {
        Path project=project(); BDouble b=new BDouble();
        Clock clock=Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC);
        OperationContext product,sale; JsonCodec.BusinessRequest saleRequest;
        try(Application app=new Application(DataRoot.forProject(project),b,clock)) {
            product=new OperationContext(b.deps.manager,app.ids().newRequestKey(),"PRODUCT_PUT",null);
            assertTrue(app.submissions().open(product).value().records("PRODUCT").isEmpty());
            assertTrue(b.credentials.get().isEmpty()); // Auth can read before a business login, without open().
            assertEquals(Result.Status.SUCCESS,app.execute(product,JsonCodec.BusinessRequest.of(new AOperations.ProductPut(new Product("00123456","虚构商品","件",new BigDecimal("1.00"),null),0))).status());
            var opening=new OperationContext(b.deps.manager,app.ids().newRequestKey(),"OPENING",null);
            assertEquals(Result.Status.SUCCESS,app.execute(opening,JsonCodec.BusinessRequest.of(new AOperations.Opening(new AOperations.Item("00123456",5),StockEntry.Position.S,"虚构期初"))).status());
            sale=new OperationContext(b.deps.clerk,app.ids().newRequestKey(),"SALE",null);
            saleRequest=JsonCodec.BusinessRequest.of(new AOperations.Sale(List.of(new AOperations.Item("00123456",1)),true));
            assertEquals(Result.Status.SUCCESS,app.execute(sale,saleRequest).status());
            assertTrue(app.execute(sale,saleRequest).value().replayed());
            try { new Application(DataRoot.forProject(project),new BDouble(),clock).close(); fail("Second writer"); }
            catch(IOException expected){ assertTrue(expected.getMessage().contains("WRITE_BLOCKED")); }
        }
        try(Application app=new Application(DataRoot.forProject(project),b,clock)) {
            assertTrue(app.execute(sale,saleRequest).value().replayed());
            var wc=app.submissions().open(product).value(); assertEquals(3,wc.baseLast());
            assertEquals(2,wc.records("STOCK_ENTRY").size()); assertEquals(1,wc.records("MONEY_ENTRY").size());
        }
    }
    @Test public void startupChecksRealBWithoutSeedingBusinessData() throws Exception {
        Path project=project(); ByteArrayOutputStream output=new ByteArrayOutputStream(),error=new ByteArrayOutputStream();
        assertEquals(0,Main.run(new String[]{"--project-root",project.toString(),"--check"},new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8)));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Storage recovery"));
        assertFalse(Files.exists(project.resolve("data/snapshot.json")));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("schemaVersion"));
        assertEquals(0,Main.run(new String[]{"--help"},System.out,System.err));
        assertEquals(1,Main.run(new String[]{"--unexpected"},System.out,System.err));
    }
    @Test public void duplicateOwnerCannotReplaceAValidator() throws Exception {
        BDouble b=new BDouble(){ @Override public Map<String,CommitValidator> validators(){
            return Map.of("SALE",(op,actor,request,wc,c,t) -> Result.value(null));
        }};
        Path project=project();
        try { new Application(DataRoot.forProject(project),b,Clock.systemUTC()).close(); fail("Owner replaced"); }
        catch(IllegalArgumentException expected){ assertTrue(expected.getMessage().contains("Duplicate operation owner")); }
        assertFalse(Files.exists(project.resolve("data/snapshot.json")));
    }
}
