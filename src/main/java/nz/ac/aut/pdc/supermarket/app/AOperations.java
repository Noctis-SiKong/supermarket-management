package nz.ac.aut.pdc.supermarket.app;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.shared.StockEntry.Position;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.supermarket.catalog.Product;
import nz.ac.aut.pdc.supermarket.promotion.Promotion;
import nz.ac.aut.pdc.supermarket.sales.Pricing;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** A-owned business coordinators. B supplies stock and identity adapters at assembly time. */
public final class AOperations implements CommitValidator {
    public static final Set<String> OPERATIONS = Set.of("PRODUCT_PUT", "OPENING", "RECEIPT_CREATE", "RECEIPT_CORRECT", "RECEIPT_REVIEW", "PROMOTION_PUT", "SALE");
    public record ProductPut(Product product, long expectedVersion) {}
    public record Item(String barcode, int quantity) {
        public Item { if (barcode == null || quantity < 1 || quantity > 9999) throw new IllegalArgumentException("Invalid quantity/barcode"); }
    }
    public record Opening(Item item, Position position, String source) {}
    public record ReceiptCreate(List<Item> items, List<AttachmentStore.Attachment> attachments) {
        public ReceiptCreate { items = List.copyOf(items); attachments = List.copyOf(attachments); }
    }
    /** Supplied existing IDs are retained; new input IDs are stable request data; omitted unposted lines are removed. */
    public record ReceiptCorrect(String receiptId, long expectedVersion, Map<String, Item> correctedLines, String reason) {
        public ReceiptCorrect { correctedLines = Map.copyOf(correctedLines); }
    }
    public record ReceiptReview(String receiptId, long expectedVersion, boolean approve, String opinion,
                                List<AttachmentStore.Attachment> attachments) {
        public ReceiptReview { attachments = List.copyOf(attachments); }
    }
    public record PromotionPut(Promotion promotion, long expectedVersion) {}
    public record Sale(List<Item> items, boolean paymentCompleted) { public Sale { items = List.copyOf(items); } }
    public record Prepared(WorkingCopy workingCopy, BusinessRequest request, ChangeSet changes) {}
    public record Receipt(String documentId, String reviewState) {}
    public record Review(String reviewId, String receiptId, String reviewerId, String decision,
                         long reviewedVersion, String reviewedAt, String opinion) {}
    public record SaleRecord(String documentId, String selectedPromotionId, Pricing.Quote quote, boolean paidConfirmed) {}
    /** A-side adapter requirement, not a replacement definition of B's IF-07 API. */
    public interface StockDependency {
        Result<ChangeSet> prepare(WorkingCopy wc, ChangeSet overlay, List<StockEntry> completeGroup);
        Result<Void> review(WorkingCopy wc, BusinessRequest original, ChangeSet completeChanges);
        void validateRecovered(WorkingCopy state) throws IOException;
    }
    private final IdClock ids;
    private final IdentityResolver identities;
    private final StockDependency stock;
    public AOperations(IdClock ids, IdentityResolver identities, StockDependency stock) {
        this.ids = Objects.requireNonNull(ids); this.identities = Objects.requireNonNull(identities); this.stock = Objects.requireNonNull(stock);
    }
    public Map<String, CommitValidator> validators() {
        Map<String, CommitValidator> out = new HashMap<>(); OPERATIONS.forEach(op -> out.put(op, this)); return Map.copyOf(out);
    }
    public Result<Product> product(WorkingCopy wc, String barcode) {
        return wc.find("PRODUCT", barcode).map(r -> Result.value(r.as(Product.class)))
            .orElseGet(() -> Result.rejected("NOT_FOUND", "Product not found"));
    }
    /** Ordinary authorized projection only; this does not implement the IF-11 cross-person exception. */
    public Result<SaleLineSnapshot> readSaleLine(OperationContext op, WorkingCopy wc, String lineId) {
        if (op == null || op.sessionRef() == null) return Result.rejected("AUTH_REQUIRED", "Login required");
        Result<ActorContext> actor;
        try { actor = identities.resolve(op.sessionRef()); }
        catch (RuntimeException e) { return Result.failed("AUTH_TECHNICAL_FAILURE", "Identity verification unavailable"); }
        if (!actor.hasValue()) return actor.failure();
        try {
            DocumentLine line = existing(wc,"DOC_LINE",lineId).as(DocumentLine.class);
            DocumentHeader header = existing(wc,"DOCUMENT",line.documentId()).as(DocumentHeader.class);
            if (actor.value().role() == Role.CLERK && !actor.value().accountId().equals(header.createdBy()))
                return Result.rejected("FORBIDDEN", "Ordinary history is limited to your own records");
            Pricing.SaleLineSnapshot snapshot = existing(wc,"SALE_LINE",lineId).as(Pricing.SaleLineSnapshot.class);
            return Result.value(new SaleLineSnapshot(line.documentId(),lineId,line.barcode(),snapshot.productNameSnapshot(),
                snapshot.unitPriceSnapshot(),line.quantity(),JsonCodec.write(snapshot.promotionSnapshot()),snapshot.grossAmount(),
                snapshot.discountAmount(),snapshot.paidAmount(),header.confirmedAt()));
        } catch (RuleFailure e) { return Result.rejected("NOT_FOUND", "Sale line not found"); }
    }
    public Result<Prepared> prepare(SubmissionPort store, OperationContext op, BusinessRequest request) {
        Result<CommitReceipt> prior = store.lookupResult(op, request);
        if (prior.status() != Result.Status.NOT_FOUND) {
            if (prior.status() == Result.Status.SUCCESS) return Result.rejected("ALREADY_COMMITTED", "Use lookupResult to obtain the committed receipt");
            return prior.failure();
        }
        Result<ActorContext> actor;
        try { actor = identities.resolve(op.sessionRef()); }
        catch (RuntimeException e) { return Result.failed("AUTH_TECHNICAL_FAILURE", "Identity verification unavailable"); }
        if (!actor.hasValue()) return actor.failure();
        Result<WorkingCopy> opened = store.open(op); if (!opened.hasValue()) return opened.failure();
        try {
            // Preview only: IF-05 assigns the authoritative time under its submit lock.
            ChangeSet expected = assemble(op, actor.value(), request, opened.value(), ids.now().toString());
            List<StockEntry> group = expected.inserts().stream().filter(r -> r.table().equals("STOCK_ENTRY")).map(r -> r.as(StockEntry.class)).toList();
            ChangeSet nonStock = new ChangeSet(expected.inserts().stream().filter(r -> !r.table().equals("STOCK_ENTRY")).toList(), expected.patches(), expected.removals());
            if (!group.isEmpty()) {
                Result<ChangeSet> candidate = stock.prepare(opened.value(), nonStock, group);
                if (!candidate.hasValue()) return candidate.failure();
                ChangeSet merged = new ChangeSet.Builder().merge(nonStock).merge(candidate.value()).build();
                if (!equivalent(merged, expected)) return Result.rejected("EVENT_CONFLICT", "Stock adapter returned effects outside requested group");
            }
            return Result.prepared(new Prepared(opened.value(), request, expected));
        } catch (RuleFailure e) { return Result.rejected(e.code, e.getMessage()); }
        catch (IllegalArgumentException | ArithmeticException e) { return Result.rejected("INVALID_INPUT", "Invalid business input"); }
        catch (RuntimeException e) { return Result.failed("DEPENDENCY_FAILED", "Business dependency unavailable"); }
    }
    @Override public Result<Void> validate(OperationContext op, ActorContext actor, BusinessRequest request, WorkingCopy wc, ChangeSet changes, Instant acceptedAt) {
        try {
            ChangeSet expected = assemble(op, actor, request, wc, acceptedAt.toString());
            if (!equivalent(expected, changes)) {
                var priced = expected.inserts().stream().filter(r -> r.table().equals("SALE")).findFirst();
                var supplied = changes.inserts().stream().filter(r -> r.table().equals("SALE")).findFirst();
                boolean priceChanged = priced.isPresent() && supplied.isPresent()
                    && !priced.get().data().path("quote").equals(supplied.get().data().path("quote"));
                return Result.rejected(priceChanged ? "STALE_PRICE" : "REFERENCE_INVALID",
                    "Candidate effects do not match original intent at the accepted business time; rebuild with the same request/key");
            }
            if (changes.inserts().stream().anyMatch(r -> r.table().equals("STOCK_ENTRY"))) return stock.review(wc, request, changes);
            return Result.value(null);
        } catch (RuleFailure e) { return Result.rejected(e.code, e.getMessage()); }
        catch (IllegalArgumentException | ArithmeticException e) { return Result.rejected("INVALID_INPUT", "Invalid business input"); }
    }
    private static boolean equivalent(ChangeSet a, ChangeSet b) {
        return a.inserts().size() == b.inserts().size() && a.patches().size() == b.patches().size()
            && a.removals().size() == b.removals().size() && new HashSet<>(a.removals()).equals(new HashSet<>(b.removals()))
            && new HashSet<>(a.inserts()).equals(new HashSet<>(b.inserts())) && new HashSet<>(a.patches()).equals(new HashSet<>(b.patches()));
    }
    private ChangeSet assemble(OperationContext op, ActorContext actor, BusinessRequest request, WorkingCopy wc, String time) {
        require(OPERATIONS.contains(op.operation()), "INVALID_INPUT", "Unknown A operation");
        Role role = actor.role();
        switch (op.operation()) {
            case "RECEIPT_CREATE", "RECEIPT_CORRECT" -> require(role == Role.MANAGER, "FORBIDDEN", "Receipt registration/correction requires manager");
            case "SALE" -> require(role == Role.MANAGER || role == Role.CLERK, "FORBIDDEN", "Sales require manager/clerk");
            default -> require(role == Role.MANAGER || role == Role.ADMIN, "FORBIDDEN", "Manager/admin required");
        }
        ChangeSet.Builder out = new ChangeSet.Builder();
        String documentId = ids.entityId(op.requestKey(), "document");
        switch (op.operation()) {
            case "PRODUCT_PUT" -> {
                ProductPut put = request.as(ProductPut.class);
                put(out, wc, "PRODUCT", put.product().barcode(), put.expectedVersion(), put.product());
            }
            case "PROMOTION_PUT" -> {
                PromotionPut put = request.as(PromotionPut.class);
                put.promotion().barcodes().forEach(barcode -> existing(wc, "PRODUCT", barcode));
                put(out, wc, "PROMOTION", put.promotion().promotionId(), put.expectedVersion(), put.promotion());
            }
            case "OPENING" -> {
                Opening opening = request.as(Opening.class);
                require(opening.position() != null && opening.source() != null && !opening.source().isBlank(), "INVALID_INPUT", "Position/source required");
                existing(wc, "PRODUCT", opening.item().barcode());
                header(out, op, actor, request, documentId, "OPENING", "CONFIRMED", opening.source(), time);
                String line = line(out, op, documentId, 0, "OPENING", opening.item());
                stock(out, op, line, opening.position(), opening.item().quantity(), null);
            }
            case "RECEIPT_CREATE" -> {
                ReceiptCreate create = request.as(ReceiptCreate.class); require(!create.items().isEmpty(), "INVALID_INPUT", "Receipt requires lines");
                header(out, op, actor, request, documentId, "RECEIPT", "PENDING_REVIEW", null, time);
                out.add(RecordData.of("RECEIPT", documentId, new Receipt(documentId, "PENDING_REVIEW")));
                int index = 0; for (Item item : create.items()) { existing(wc, "PRODUCT", item.barcode()); line(out, op, documentId, index++, "RECEIPT", item); }
                attachments(out, wc, documentId, create.attachments());
            }
            case "RECEIPT_CORRECT" -> {
                ReceiptCorrect correct = request.as(ReceiptCorrect.class);
                RecordData document = receipt(wc, correct.receiptId(), correct.expectedVersion(), "NEEDS_CORRECTION");
                require(correct.reason() != null && !correct.reason().isBlank(), "INVALID_INPUT", "Correction reason required");
                List<RecordData> lines = wc.records("DOC_LINE").stream().filter(r -> r.as(DocumentLine.class).documentId().equals(correct.receiptId())).toList();
                require(!correct.correctedLines().isEmpty(), "INVALID_INPUT", "Receipt must retain at least one line");
                for (RecordData r : lines) {
                    Item item = correct.correctedLines().get(r.id());
                    if (item == null) out.remove(new ChangeSet.Removal("DOC_LINE",r.id(),r.version()));
                    else { existing(wc, "PRODUCT", item.barcode()); patch(out, r, Map.of("barcode", item.barcode(), "quantity", item.quantity())); }
                }
                Set<String> existingIds = lines.stream().map(RecordData::id).collect(java.util.stream.Collectors.toSet());
                for (var added : correct.correctedLines().entrySet()) if (!existingIds.contains(added.getKey())) {
                    UUID.fromString(added.getKey()); existing(wc,"PRODUCT",added.getValue().barcode());
                    out.add(RecordData.of("DOC_LINE",added.getKey(),new DocumentLine(added.getKey(),correct.receiptId(),"RECEIPT",added.getValue().barcode(),added.getValue().quantity())));
                }
                patch(out, document, Map.of("status", "PENDING_REVIEW", "reason", correct.reason()));
                patch(out, existing(wc, "RECEIPT", correct.receiptId()), Map.of("reviewState", "PENDING_REVIEW"));
                event(out, op, actor, correct.receiptId(), document.version()+1, "CORRECT", request.json(), time);
            }
            case "RECEIPT_REVIEW" -> {
                ReceiptReview review = request.as(ReceiptReview.class);
                RecordData document = receipt(wc, review.receiptId(), review.expectedVersion(), "PENDING_REVIEW");
                String state = review.approve() ? "APPROVED" : "NEEDS_CORRECTION";
                require(review.opinion() != null && !review.opinion().isBlank(), "INVALID_INPUT", "Review opinion required");
                String reviewId = ids.entityId(op.requestKey(), "review");
                out.add(RecordData.of("RECEIPT_REVIEW", reviewId, new Review(reviewId, review.receiptId(), actor.accountId(), state, document.version(), time, review.opinion())));
                Map<String,Object> fields = new LinkedHashMap<>(); fields.put("status", state);
                if (review.approve()) { fields.put("confirmedBy", actor.accountId()); fields.put("confirmedAt", time); }
                patch(out, document, fields);
                patch(out, existing(wc, "RECEIPT", review.receiptId()), Map.of("reviewState", state));
                event(out, op, actor, review.receiptId(), document.version()+1, "REVIEW", request.json(), time);
                attachments(out, wc, review.receiptId(), review.attachments());
                if (review.approve()) for (RecordData r : wc.records("DOC_LINE")) {
                    DocumentLine l = r.as(DocumentLine.class);
                    if (l.documentId().equals(review.receiptId())) stock(out, op, l.lineId(), Position.W, l.quantity(), reviewId);
                }
            }
            case "SALE" -> {
                Sale sale = request.as(Sale.class); require(sale.paymentCompleted(), "PAYMENT_INCOMPLETE", "Confirm simulated payment completed first");
                List<Pricing.Line> lines = saleLines(sale, wc, op);
                Pricing.Quote quote = quote(sale, wc, op, Instant.parse(time));
                header(out, op, actor, request, documentId, "SALE", "CONFIRMED", null, time);
                out.add(RecordData.of("SALE", documentId, new SaleRecord(documentId, quote.promotion() == null ? null : quote.promotion().promotionId(), quote, true)));
                for (Pricing.Line l : lines) {
                    out.add(RecordData.of("DOC_LINE", l.lineId(), new DocumentLine(l.lineId(), documentId, "SALE", l.barcode(), l.quantity())));
                    stock(out, op, l.lineId(), Position.S, -l.quantity(), null);
                }
                quote.lines().forEach(l -> out.add(RecordData.of("SALE_LINE", l.lineId(), l)));
                String moneyId = ids.entityId(op.requestKey(), "money");
                out.add(RecordData.of("MONEY_ENTRY", moneyId, new MoneyEntry(moneyId, documentId, quote.paidTotal(), op.requestKey(), null)));
            }
            default -> throw new IllegalArgumentException("Unknown operation");
        }
        return out.build();
    }
    private Pricing.Quote quote(Sale sale, WorkingCopy wc, OperationContext op, Instant now) {
        return new Pricing().quote(saleLines(sale, wc, op), wc.records("PROMOTION").stream().map(r -> r.as(Promotion.class)).toList(), now);
    }
    private List<Pricing.Line> saleLines(Sale sale, WorkingCopy wc, OperationContext op) {
        require(!sale.items().isEmpty(), "INVALID_INPUT", "Sale requires lines");
        Map<String,Integer> merged = new LinkedHashMap<>();
        for (Item item : sale.items()) merged.merge(item.barcode(), item.quantity(), Math::addExact);
        List<Pricing.Line> lines = new ArrayList<>(); int index = 0;
        for (var entry : merged.entrySet()) {
            Product p = existing(wc, "PRODUCT", entry.getKey()).as(Product.class);
            lines.add(new Pricing.Line(ids.entityId(op.requestKey(), "line:"+index++), p.barcode(), p.name(), p.sellingPrice(), entry.getValue()));
        }
        return lines;
    }
    private static RecordData existing(WorkingCopy wc, String table, String id) {
        return wc.find(table, id).orElseThrow(() -> new RuleFailure("REFERENCE_INVALID", "Missing " + table + " reference"));
    }
    private static void put(ChangeSet.Builder out, WorkingCopy wc, String table, String id, long version, Object value) {
        Optional<RecordData> old = wc.find(table, id);
        if (version == 0) { require(old.isEmpty(), "EVENT_CONFLICT", "Record already exists"); out.add(RecordData.of(table,id,value)); }
        else { RecordData record = existing(wc, table, id); require(record.version() == version, "STALE_WORKING_COPY", "Record version changed"); patch(out, record, value); }
    }
    private static void patch(ChangeSet.Builder out, RecordData old, Object changed) {
        out.patch(new ChangeSet.Patch(old.table(), old.id(), old.version(), JsonCodec.write(changed)));
    }
    private RecordData receipt(WorkingCopy wc, String id, long version, String state) {
        RecordData record = existing(wc, "DOCUMENT", id);
        DocumentHeader header = record.as(DocumentHeader.class);
        require(header.kind().equals("RECEIPT") && header.status().equals(state), "INVALID_RECEIPT_STATE", "Receipt state does not permit this action");
        require(record.version() == version, "STALE_WORKING_COPY", "Receipt version changed"); return record;
    }
    private void header(ChangeSet.Builder out, OperationContext op, ActorContext actor, BusinessRequest request, String id, String kind, String status, String reason, String time) {
        boolean confirmed = status.equals("CONFIRMED");
        out.add(RecordData.of("DOCUMENT", id, new DocumentHeader(id, kind, status, op.requestKey(), JsonCodec.fingerprint(op.operation(), actor.accountId(), request),
            actor.accountId(), time, confirmed ? actor.accountId() : null, confirmed ? time : null, reason)));
        event(out, op, actor, id, 1, "CREATE", request.json(), time);
    }
    private void event(ChangeSet.Builder out, OperationContext op, ActorContext actor, String document, long version, String action, String detail, String time) {
        String id = ids.entityId(op.requestKey(), "event");
        out.add(RecordData.of("DOC_EVENT", id, new DocEvent(id, document, version, action, actor.accountId(), time, detail)));
    }
    private String line(ChangeSet.Builder out, OperationContext op, String doc, int index, String kind, Item item) {
        String id = ids.entityId(op.requestKey(), "line:" + index);
        out.add(RecordData.of("DOC_LINE", id, new DocumentLine(id, doc, kind, item.barcode(), item.quantity()))); return id;
    }
    private void stock(ChangeSet.Builder out, OperationContext op, String line, Position position, int delta, String reviewId) {
        String id = ids.entityId(op.requestKey(), "stock:" + line);
        out.add(RecordData.of("STOCK_ENTRY", id, new StockEntry(id, line, position, delta, id, reviewId, null, null, 0)));
    }
    private static void attachments(ChangeSet.Builder out, WorkingCopy wc, String receiptId, List<AttachmentStore.Attachment> attachments) {
        long count = wc.records("ATTACHMENT").stream().filter(r -> r.as(AttachmentStore.Attachment.class).receiptId().equals(receiptId)).count();
        require(count + attachments.size() <= 3, "INVALID_INPUT", "At most three receipt attachments");
        for (AttachmentStore.Attachment a : attachments) {
            require(a.receiptId().equals(receiptId), "REFERENCE_INVALID", "Attachment receipt mismatch");
            out.add(RecordData.of("ATTACHMENT", a.attachmentId(), a));
        }
    }
    public void validateRecovered(WorkingCopy view) throws IOException {
        try {
            for (RecordData r : view.records("PRODUCT")) { Product p = r.as(Product.class); require(r.id().equals(p.barcode()), "REFERENCE_INVALID", "Product ID mismatch"); }
            for (RecordData r : view.records("PROMOTION")) {
                Promotion p = r.as(Promotion.class); require(r.id().equals(p.promotionId()), "REFERENCE_INVALID", "Promotion ID mismatch");
                p.barcodes().forEach(barcode -> existing(view,"PRODUCT",barcode));
            }
            for (RecordData r : view.records("RECEIPT")) {
                Receipt receipt = r.as(Receipt.class); DocumentHeader header = existing(view,"DOCUMENT",r.id()).as(DocumentHeader.class);
                require(header.kind().equals("RECEIPT") && header.status().equals(receipt.reviewState()), "REFERENCE_INVALID", "Receipt state mismatch");
                require(view.records("ATTACHMENT").stream().filter(f -> f.as(AttachmentStore.Attachment.class).receiptId().equals(r.id())).count() <= 3,"INVALID_INPUT","Too many receipt attachments");
                require(view.records("DOC_LINE").stream().anyMatch(l -> l.as(DocumentLine.class).documentId().equals(r.id())),"REFERENCE_INVALID","Receipt requires lines");
                if (receipt.reviewState().equals("APPROVED")) {
                    List<StockEntry> effects = view.records("STOCK_ENTRY").stream().map(s -> s.as(StockEntry.class)).filter(s -> s.reverseOfId() == null && s.receiptReviewId() != null).toList();
                    for (RecordData l : view.records("DOC_LINE")) if (l.as(DocumentLine.class).documentId().equals(r.id()))
                        require(effects.stream().filter(s -> s.sourceLineId().equals(l.id()) && s.position() == Position.W && s.quantityDelta() == l.as(DocumentLine.class).quantity()).count() == 1,
                            "REFERENCE_INVALID","Approved receipt must have exactly one W+ per line");
                }
            }
            for (RecordData r : view.records("RECEIPT_REVIEW")) {
                Review review = r.as(Review.class); existing(view,"RECEIPT",review.receiptId());
                require(r.id().equals(review.reviewId()) && review.reviewedVersion() > 0,"REFERENCE_INVALID","Invalid review identity/version");
                Instant.parse(review.reviewedAt());
            }
            for (RecordData r : view.records("SALE_LINE")) {
                DocumentLine line = existing(view,"DOC_LINE",r.id()).as(DocumentLine.class);
                existing(view,"SALE",line.documentId());
            }
            for (RecordData r : view.records("SALE")) {
                SaleRecord sale = r.as(SaleRecord.class); Pricing.Quote quote = sale.quote();
                DocumentHeader saleHeader = existing(view,"DOCUMENT",r.id()).as(DocumentHeader.class);
                require(sale.paidConfirmed() && saleHeader.kind().equals("SALE") && !quote.lines().isEmpty(), "REFERENCE_INVALID", "Invalid sale");
                java.math.BigDecimal gross = java.math.BigDecimal.ZERO, discount = java.math.BigDecimal.ZERO, paid = java.math.BigDecimal.ZERO;
                List<Pricing.Line> storedLines = new ArrayList<>();
                for (Pricing.SaleLineSnapshot l : quote.lines()) {
                    DocumentLine line = existing(view,"DOC_LINE",l.lineId()).as(DocumentLine.class);
                    require(line.documentId().equals(r.id()) && existing(view,"SALE_LINE",l.lineId()).as(Pricing.SaleLineSnapshot.class).equals(l), "REFERENCE_INVALID", "Sale line mismatch");
                    require(l.discountAmount().signum() >= 0 && l.paidAmount().signum() >= 0 && l.grossAmount().subtract(l.discountAmount()).compareTo(l.paidAmount()) == 0, "INVALID_INPUT", "Sale amounts inconsistent");
                    storedLines.add(new Pricing.Line(l.lineId(),line.barcode(),l.productNameSnapshot(),l.unitPriceSnapshot(),line.quantity()));
                    gross = gross.add(l.grossAmount()); discount = discount.add(l.discountAmount()); paid = paid.add(l.paidAmount());
                }
                require(gross.compareTo(quote.grossTotal()) == 0 && discount.compareTo(quote.discountTotal()) == 0 && paid.compareTo(quote.paidTotal()) == 0, "INVALID_INPUT", "Sale totals inconsistent");
                Pricing.Quote rebuilt = new Pricing().quote(storedLines,quote.promotion() == null ? List.of() : List.of(quote.promotion()),Instant.parse(saleHeader.confirmedAt()));
                require(rebuilt.equals(quote),"INVALID_INPUT","Stored sale pricing snapshot inconsistent");
                List<MoneyEntry> payments = view.records("MONEY_ENTRY").stream().map(m -> m.as(MoneyEntry.class)).filter(m -> m.documentId().equals(r.id()) && m.reverseOfId() == null).toList();
                require(payments.size() == 1 && payments.get(0).amountSigned().compareTo(quote.paidTotal()) == 0,"REFERENCE_INVALID","Sale payment missing/mismatched");
            }
            stock.validateRecovered(view);
        } catch (RuntimeException e) { throw new IOException("A domain integrity failed", e); }
    }
    private static void require(boolean condition, String code, String detail) { if (!condition) throw new RuleFailure(code, detail); }
    private static final class RuleFailure extends RuntimeException {
        private final String code;
        private RuleFailure(String code, String detail) { super(detail); this.code = code; }
    }
}
