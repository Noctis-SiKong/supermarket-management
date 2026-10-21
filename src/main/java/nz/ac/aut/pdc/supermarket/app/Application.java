package nz.ac.aut.pdc.supermarket.app;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;

/** Real storage and A services; no built-in users, goods, B substitutes or default-success validators. */
public final class Application implements AutoCloseable {
    private final JsonStore store;
    private final AOperations operations;
    private final OriginalSaleAccess sales;
    private final AttachmentStore attachments;
    private final IdClock ids;
    public Application(DataRoot root, BIntegration b, Clock clock) throws Exception {
        this(root,b,clock,p -> {});
    }
    /** Fault hook remains before the atomic commit point; production constructor uses the real filesystem. */
    public Application(DataRoot root, BIntegration b, Clock clock, JsonStore.BeforeReplace beforeReplace) throws Exception {
        Objects.requireNonNull(b);
        AtomicReference<JsonStore> initialized = new AtomicReference<>();
        var identity = Objects.requireNonNull(b.identities(() -> {
            JsonStore value = initialized.get();
            if (value == null) throw new IllegalStateException("Authentication storage not initialized");
            return value.authenticationView();
        }));
        ids = new IdClock(clock);
        operations = new AOperations(ids,identity,Objects.requireNonNull(b.stock()));
        sales = new OriginalSaleAccess(identity,ids,Objects.requireNonNull(b.drafts()));
        attachments = new AttachmentStore(root);
        Map<String,CommitValidator> validators = new HashMap<>(operations.validators());
        validators.put(AftersaleReadRequest.OPERATION,sales);
        for (var entry : Objects.requireNonNull(b.validators()).entrySet()) {
            if (validators.putIfAbsent(entry.getKey(),entry.getValue()) != null)
                throw new IllegalArgumentException("Duplicate operation owner: " + entry.getKey());
        }
        var integrity = Objects.requireNonNull(b.integrity());
        store = new JsonStore(root,identity,validators,wc -> {
            operations.validateRecovered(wc); sales.validateRecovered(wc); integrity.validate(wc);
        },beforeReplace,JsonStore.RecoveryMode.CURRENT,Objects.requireNonNull(b.initialAccounts()),ids::now);
        initialized.set(store);
    }
    /** Internal coordinator API; never expose WorkingCopy/SubmissionPort directly through a CUI menu. */
    public SubmissionPort submissions() { return store; }
    JsonStore storage() { return store; }
    public AOperations operations() { return operations; }
    public OriginalSaleAccess originalSales() { return sales; }
    public AttachmentStore attachments() { return attachments; }
    public IdClock ids() { return ids; }
    public Result<CommitReceipt> execute(OperationContext context, JsonCodec.BusinessRequest request) {
        var prior = store.lookupResult(context,request);
        if (prior.status() != Result.Status.NOT_FOUND) return prior;
        var candidate = operations.prepare(store,context,request);
        if (!candidate.hasValue()) return candidate.failure();
        return store.submit(context,request,candidate.value().workingCopy(),candidate.value().changes());
    }
    @Override public void close() throws Exception { store.close(); }
}
