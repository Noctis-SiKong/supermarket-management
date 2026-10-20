package nz.ac.aut.pdc.supermarket.app;

import nz.ac.aut.pdc.fixture.FictionalDependencies;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.app.AOperations.*;
import nz.ac.aut.pdc.supermarket.sales.Pricing;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

/** Real A services and JSON, fictional identity/stock; NOT real IF-07 integration or AC acceptance. */
public class AOperationsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    FictionalDependencies deps; AOperations a; JsonStore store; IdClock ids; DataRoot root;
    @Before public void start() throws Exception {
        deps = new FictionalDependencies(); ids = new IdClock(Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"),ZoneOffset.UTC));
        a = new AOperations(ids,deps,deps);
        Path project = temp.newFolder().toPath(); Files.writeString(project.resolve("README.md"), "fixture"); Files.writeString(project.resolve("pom.xml"), "fixture"); Files.createDirectory(project.resolve("docs"));
        root = DataRoot.forProject(project); store = new JsonStore(root,deps,a.validators(),a::validateRecovered);
    }
    @After public void stop() throws Exception { if (store != null) store.close(); }
    OperationContext op(String kind,Object identity) { return new OperationContext(identity,ids.newRequestKey(),kind,null); }
    Result<SubmissionPort.CommitReceipt> execute(OperationContext op,Object value) {
        BusinessRequest request = BusinessRequest.of(value);
        var prepared = a.prepare(store,op,request);
        if (!prepared.hasValue()) return prepared.failure();
        return store.submit(op,request,prepared.value().workingCopy(),prepared.value().changes());
    }
    void product() {
        assertEquals(Result.Status.SUCCESS,execute(op("PRODUCT_PUT",deps.manager),new ProductPut(new Product("00123456","虚构品牌牛奶","盒",new BigDecimal("3.33"),null),0)).status());
    }
    WorkingCopy state() { return store.open(op("INTERNAL_TEST",deps.manager)).value(); }
    @Test public void productMaintenanceAndOpeningRespectRolesAndAlwaysHaveSourceDocument() {
        assertEquals("FORBIDDEN",execute(op("PRODUCT_PUT",deps.clerk),new ProductPut(new Product("00123456","虚构牛奶","盒",new BigDecimal("1.00"),null),0)).code());
        product();
        assertEquals("EVENT_CONFLICT",execute(op("PRODUCT_PUT",deps.manager),new ProductPut(new Product("00123456","重复","盒",new BigDecimal("1.00"),null),0)).code());
        Opening opening = new Opening(new Item("00123456",10),Position.S,"虚构期初来源");
        assertEquals("FORBIDDEN",execute(op("OPENING",deps.clerk),opening).code());
        assertEquals(Result.Status.SUCCESS,execute(op("OPENING",deps.admin),opening).status());
        WorkingCopy state = state(); StockEntry entry = state.records("STOCK_ENTRY").get(0).as(StockEntry.class);
        assertEquals(2,entry.commitOrdinal()); assertEquals(10,entry.quantityDelta());
        DocumentLine line = state.find("DOC_LINE",entry.sourceLineId()).orElseThrow().as(DocumentLine.class);
        assertEquals("OPENING",state.find("DOCUMENT",line.documentId()).orElseThrow().as(DocumentHeader.class).kind());
        assertSame(deps.seenCopy.find("PRODUCT","00123456").orElseThrow().as(Product.class).getClass(),Product.class);
    }
    @Test public void receiptNeedsCorrectionThenSelfReviewApprovesOnlyOnceWithMatchingOrdinal() {
        product(); OperationContext create = op("RECEIPT_CREATE",deps.manager);
        ReceiptCreate input = new ReceiptCreate(List.of(new Item("00123456",4)),List.of());
        assertEquals(Result.Status.SUCCESS,execute(create,input).status());
        String receiptId = ids.entityId(create.requestKey(),"document"), lineId = ids.entityId(create.requestKey(),"line:0");
        assertTrue(state().records("STOCK_ENTRY").isEmpty());
        assertEquals(Result.Status.SUCCESS,execute(op("RECEIPT_REVIEW",deps.manager),new ReceiptReview(receiptId,1,false,"修正实收数量",List.of())).status());
        assertTrue(state().records("STOCK_ENTRY").isEmpty());
        assertEquals(Result.Status.SUCCESS,execute(op("RECEIPT_CORRECT",deps.manager),new ReceiptCorrect(receiptId,2,Map.of(lineId,new Item("00123456",3)),"更正为三盒")).status());
        OperationContext approve = op("RECEIPT_REVIEW",deps.manager);
        ReceiptReview approval = new ReceiptReview(receiptId,3,true,"无图自审通过",List.of());
        assertEquals(Result.Status.SUCCESS,execute(approve,approval).status());
        assertTrue(store.lookupResult(approve,BusinessRequest.of(approval)).value().replayed());
        assertEquals("INVALID_RECEIPT_STATE",execute(op("RECEIPT_REVIEW",deps.manager),new ReceiptReview(receiptId,4,true,"重复审核",List.of())).code());
        StockEntry entry = state().records("STOCK_ENTRY").get(0).as(StockEntry.class);
        assertEquals(3,entry.quantityDelta()); assertEquals(Position.W,entry.position()); assertEquals(5,entry.commitOrdinal());
        assertEquals(ids.entityId(approve.requestKey(),"review"),entry.receiptReviewId());
    }
    @Test public void saleUsesLegalOpeningMergesLinesAndKeepsSnapshotAfterPriceChangeAndRestart() throws Exception {
        product(); assertEquals(Result.Status.SUCCESS,execute(op("OPENING",deps.manager),new Opening(new Item("00123456",10),Position.S,"虚构期初")).status());
        OperationContext sale = op("SALE",deps.clerk); Sale input = new Sale(List.of(new Item("00123456",1),new Item("00123456",2)),true);
        assertEquals(Result.Status.SUCCESS,execute(sale,input).status());
        assertEquals(1,state().records("SALE_LINE").size());
        Pricing.SaleLineSnapshot line = state().records("SALE_LINE").get(0).as(Pricing.SaleLineSnapshot.class);
        assertEquals(new BigDecimal("9.99"),line.paidAmount());
        assertEquals(Result.Status.SUCCESS,execute(op("PRODUCT_PUT",deps.manager),new ProductPut(new Product("00123456","虚构新名称","盒",new BigDecimal("4.00"),null),1)).status());
        assertEquals(line,state().records("SALE_LINE").get(0).as(Pricing.SaleLineSnapshot.class));
        store.close(); store = new JsonStore(root,deps,a.validators(),a::validateRecovered);
        assertEquals(line,state().records("SALE_LINE").get(0).as(Pricing.SaleLineSnapshot.class));
        assertEquals(4,state().baseLast());
    }
    @Test public void paymentAndStockRejectionLeaveNoSaleAndCommitRechecksStock() {
        product(); execute(op("OPENING",deps.manager),new Opening(new Item("00123456",10),Position.S,"虚构期初"));
        assertEquals("PAYMENT_INCOMPLETE",execute(op("SALE",deps.clerk),new Sale(List.of(new Item("00123456",1)),false)).code());
        OperationContext sale = op("SALE",deps.clerk); BusinessRequest request = BusinessRequest.of(new Sale(List.of(new Item("00123456",1)),true));
        var prepared = a.prepare(store,sale,request); assertEquals(Result.Status.PREPARED,prepared.status());
        deps.rejectStock = true;
        assertEquals("INSUFFICIENT_STOCK",store.submit(sale,request,prepared.value().workingCopy(),prepared.value().changes()).code());
        assertEquals(2,state().baseLast()); assertTrue(state().records("SALE").isEmpty()); assertEquals(2,deps.reviews);
    }
    @Test public void alteredCandidateCannotUseUnchangedOriginalRequest() {
        product(); OperationContext sale = op("SALE",deps.clerk); BusinessRequest request = BusinessRequest.of(new Sale(List.of(new Item("00123456",1)),true));
        var prepared = a.prepare(store,sale,request); ChangeSet original = prepared.value().changes();
        ChangeSet tampered = new ChangeSet(original.inserts().stream().filter(r -> !r.table().equals("MONEY_ENTRY")).toList(),original.patches());
        assertEquals("REFERENCE_INVALID",store.submit(sale,request,prepared.value().workingCopy(),tampered).code());
        assertEquals(1,state().baseLast()); assertTrue(state().records("SALE").isEmpty());
    }
    @Test public void fractionalQuantityNeverCoercesToIntegerAndOriginalSaleReadsRemainRestricted() {
        OperationContext bad = op("OPENING",deps.manager);
        assertEquals("INVALID_INPUT",a.prepare(store,bad,new BusinessRequest("{\"item\":{\"barcode\":\"00123456\",\"quantity\":1.5},\"position\":\"S\",\"source\":\"fixture\"}")).code());
        product(); execute(op("OPENING",deps.manager),new Opening(new Item("00123456",5),Position.S,"虚构期初"));
        OperationContext sale = op("SALE",deps.manager); execute(sale,new Sale(List.of(new Item("00123456",1)),true));
        String lineId = ids.entityId(sale.requestKey(),"line:0");
        assertEquals("FORBIDDEN",a.readSaleLine(op("READ",deps.clerk),state(),lineId).code());
        var line = a.readSaleLine(op("READ",deps.admin),state(),lineId);
        assertEquals(Result.Status.VALUE,line.status()); assertEquals(new BigDecimal("3.33"),line.value().paidAmount());
        assertEquals(1,line.value().originalQuantity());
    }
    @Test public void correctionCanAddAndRemoveUnpostedLinesWithoutAlteringPostedFacts() {
        product(); OperationContext create = op("RECEIPT_CREATE",deps.manager);
        execute(create,new ReceiptCreate(List.of(new Item("00123456",4)),List.of()));
        String receiptId = ids.entityId(create.requestKey(),"document"), oldLine = ids.entityId(create.requestKey(),"line:0");
        execute(op("RECEIPT_REVIEW",deps.manager),new ReceiptReview(receiptId,1,false,"重新列明",List.of()));
        String newLine = ids.entityId(ids.newRequestKey(),"corrected-line");
        assertEquals(Result.Status.SUCCESS,execute(op("RECEIPT_CORRECT",deps.manager),new ReceiptCorrect(receiptId,2,Map.of(newLine,new Item("00123456",2)),"替换明细")).status());
        assertTrue(state().find("DOC_LINE",oldLine).isEmpty()); assertTrue(state().find("DOC_LINE",newLine).isPresent());
        assertEquals(3,state().records("DOC_EVENT").size()); assertTrue(state().records("STOCK_ENTRY").isEmpty());
    }
    @Test public void activityExpiryAtCommitRequiresRepricingButKeepsOriginalIntentAndStableIds() throws Exception {
        class MutableClock extends Clock {
            Instant instant = Instant.parse("2026-10-20T15:59:59Z");
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return instant; }
        }
        MutableClock clock = new MutableClock();
        store.close(); ids = new IdClock(clock); a = new AOperations(ids,deps,deps); store = new JsonStore(root,deps,a.validators(),a::validateRecovered);
        product(); execute(op("OPENING",deps.manager),new Opening(new Item("00123456",5),Position.S,"虚构期初"));
        var promotion = new nz.ac.aut.pdc.supermarket.promotion.Promotion("p",nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.PERCENT,
            new BigDecimal("50"),null,null,null,"2026-10-20","2026-10-20",Set.of("00123456"));
        assertEquals(Result.Status.SUCCESS,execute(op("PROMOTION_PUT",deps.manager),new PromotionPut(promotion,0)).status());
        OperationContext sale = op("SALE",deps.clerk); BusinessRequest intent = BusinessRequest.of(new Sale(List.of(new Item("00123456",1)),true));
        var prepared = a.prepare(store,sale,intent); String stable = ids.entityId(sale.requestKey(),"line:0");
        clock.instant = Instant.parse("2026-10-20T16:00:00Z");
        assertEquals("STALE_PRICE",store.submit(sale,intent,prepared.value().workingCopy(),prepared.value().changes()).code());
        assertTrue(state().records("SALE").isEmpty());
        var rebuilt = a.prepare(store,sale,intent);
        assertEquals(Result.Status.SUCCESS,store.submit(sale,intent,rebuilt.value().workingCopy(),rebuilt.value().changes()).status());
        assertEquals(new BigDecimal("3.33"),state().find("SALE_LINE",stable).orElseThrow().as(Pricing.SaleLineSnapshot.class).paidAmount());
    }
    @Test public void persistedReceiptAttachmentHashAndOrdinalCorruptionBlockStartup() throws Exception {
        product(); OperationContext create = op("RECEIPT_CREATE",deps.manager); String doc = ids.entityId(create.requestKey(),"document");
        Path image = temp.newFile("fictional.png").toPath();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        var attachment = new AttachmentStore(root).importImage(image,doc,0).value();
        assertEquals(Result.Status.SUCCESS,execute(create,new ReceiptCreate(List.of(new Item("00123456",1)),List.of(attachment))).status());
        assertEquals(Result.Status.SUCCESS,execute(op("RECEIPT_REVIEW",deps.manager),new ReceiptReview(doc,1,true,"虚构文件",List.of())).status());
        store.close(); byte[] saved = Files.readAllBytes(root.resolve(attachment.relativePath()));
        byte[] corrupted = saved.clone(); corrupted[corrupted.length-1] ^= 1; Files.write(root.resolve(attachment.relativePath()),corrupted);
        try { new JsonStore(root,deps,a.validators(),a::validateRecovered).close(); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("RECOVERY_WRITE_BLOCKED")); }
        Files.write(root.resolve(attachment.relativePath()),saved);
        String snapshot = Files.readString(root.resolve("snapshot.json"));
        assertTrue(snapshot.contains("\"last_commit_ordinal\":3"));
        Files.writeString(root.resolve("snapshot.json"),snapshot.replace("\"last_commit_ordinal\":3","\"last_commit_ordinal\":2"));
        try { new JsonStore(root,deps,a.validators(),a::validateRecovered).close(); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("RECOVERY_WRITE_BLOCKED")); }
        Files.writeString(root.resolve("snapshot.json"),snapshot);
        store = new JsonStore(root,deps,a.validators(),a::validateRecovered); assertEquals(3,state().baseLast());
    }
    @Test public void promotedSaleKeepsOriginalActivityAndAllocationAfterActivityEditAndRestart() throws Exception {
        product(); execute(op("OPENING",deps.manager),new Opening(new Item("00123456",5),Position.S,"虚构期初"));
        var activity = new nz.ac.aut.pdc.supermarket.promotion.Promotion("p",nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.PERCENT,
            new BigDecimal("90"),null,null,1,"2026-10-20","2026-10-20",Set.of("00123456"));
        assertEquals(Result.Status.SUCCESS,execute(op("PROMOTION_PUT",deps.manager),new PromotionPut(activity,0)).status());
        OperationContext sale = op("SALE",deps.clerk);
        assertEquals(Result.Status.SUCCESS,execute(sale,new Sale(List.of(new Item("00123456",3)),true)).status());
        String line = ids.entityId(sale.requestKey(),"line:0");
        Pricing.SaleLineSnapshot snapshot = state().find("SALE_LINE",line).orElseThrow().as(Pricing.SaleLineSnapshot.class);
        assertEquals(new BigDecimal("8.99"),snapshot.paidAmount());
        var edited = new nz.ac.aut.pdc.supermarket.promotion.Promotion("p",nz.ac.aut.pdc.supermarket.promotion.Promotion.Kind.PERCENT,
            new BigDecimal("50"),null,null,1,"2026-10-20","2026-10-20",Set.of("00123456"));
        assertEquals(Result.Status.SUCCESS,execute(op("PROMOTION_PUT",deps.manager),new PromotionPut(edited,1)).status());
        store.close(); store = new JsonStore(root,deps,a.validators(),a::validateRecovered);
        assertEquals(snapshot,state().find("SALE_LINE",line).orElseThrow().as(Pricing.SaleLineSnapshot.class));
        assertEquals(0,new BigDecimal("90").compareTo(snapshot.promotionSnapshot().payPercent()));
    }
}
