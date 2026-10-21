package nz.ac.aut.pdc.infrastructure;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Single-process coordinator plus OS single-writer lock. No domain algorithm is duplicated here. */
public final class JsonStore implements SubmissionPort, AutoCloseable {
    public record SavedResult(String operation, String accountId, String fingerprint, CommitReceipt receipt) {}
    public record Credential(String accountId, String username, String passwordHash, Role role) {}
    public record Snapshot(int schemaVersion, @com.fasterxml.jackson.annotation.JsonProperty("last_commit_ordinal") long lastCommitOrdinal, List<RecordData> records,
                           Map<String, SavedResult> results, List<Credential> credentials) {
        public Snapshot { records = List.copyOf(records); results = Map.copyOf(results); credentials = List.copyOf(credentials); }
    }
    @FunctionalInterface public interface IntegrityValidator { void validate(WorkingCopy state) throws IOException; }
    /** Fault hook executes BEFORE the commit point only. Used for real filesystem fault tests. */
    @FunctionalInterface public interface BeforeReplace { void run(Path temporary) throws IOException; }
    public enum RecoveryMode { CURRENT, PREVIOUS }
    private final DataRoot root;
    private final IdentityResolver identities;
    private final Map<String, CommitValidator> validators;
    private final IntegrityValidator domainIntegrity;
    private final BeforeReplace beforeReplace;
    private final java.util.function.Supplier<java.time.Instant> businessTime;
    private final FileChannel lockChannel;
    private final FileLock writerLock;
    private final Map<String, Handle> handles = new HashMap<>();
    private record Handle(WorkingCopy copy, String actor, String operation, String target) {}
    private Snapshot state;
    private boolean closed;

    public JsonStore(DataRoot root, IdentityResolver identities, Map<String, CommitValidator> validators,
                     IntegrityValidator domainIntegrity) throws IOException {
        this(root, identities, validators, domainIntegrity, p -> {});
    }
    public JsonStore(DataRoot root, IdentityResolver identities, Map<String, CommitValidator> validators,
                     IntegrityValidator domainIntegrity, java.util.function.Supplier<java.time.Instant> businessTime) throws IOException {
        this(root, identities, validators, domainIntegrity, p -> {}, RecoveryMode.CURRENT, List.of(), businessTime);
    }
    public JsonStore(DataRoot root, IdentityResolver identities, Map<String, CommitValidator> validators,
                     IntegrityValidator domainIntegrity, BeforeReplace beforeReplace) throws IOException {
        this(root, identities, validators, domainIntegrity, beforeReplace, RecoveryMode.CURRENT, List.of());
    }
    /** PREVIOUS is an explicit recovery choice, never an automatic fallback from corrupt data.
     *  preconfiguredAccounts are supplied by B's bootstrap, never embedded passwords here. */
    public JsonStore(DataRoot root, IdentityResolver identities, Map<String, CommitValidator> validators,
                     IntegrityValidator domainIntegrity, BeforeReplace beforeReplace, RecoveryMode recovery,
                     List<Credential> preconfiguredAccounts) throws IOException {
        this(root,identities,validators,domainIntegrity,beforeReplace,recovery,preconfiguredAccounts,java.time.Instant::now);
    }
    public JsonStore(DataRoot root, IdentityResolver identities, Map<String, CommitValidator> validators,
                     IntegrityValidator domainIntegrity, BeforeReplace beforeReplace, RecoveryMode recovery,
                     List<Credential> preconfiguredAccounts, java.util.function.Supplier<java.time.Instant> businessTime) throws IOException {
        this.root = Objects.requireNonNull(root); this.identities = Objects.requireNonNull(identities);
        this.validators = Map.copyOf(validators); this.domainIntegrity = Objects.requireNonNull(domainIntegrity);
        this.beforeReplace = Objects.requireNonNull(beforeReplace);
        this.businessTime = Objects.requireNonNull(businessTime);
        lockChannel = FileChannel.open(root.resolve("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { lockChannel.close(); throw new IOException("RECOVERY_WRITE_BLOCKED: writer already active", e); }
        if (acquired == null) { lockChannel.close(); throw new IOException("RECOVERY_WRITE_BLOCKED: writer already active"); }
        writerLock = acquired;
        try {
            Path current = root.resolve("snapshot.json");
            if (recovery == RecoveryMode.CURRENT && !Files.exists(current) && Files.exists(root.resolve("previous.json")))
                throw new IOException("RECOVERY_WRITE_BLOCKED: snapshot missing; explicit full recovery required");
            Path selected = recovery == RecoveryMode.PREVIOUS ? root.resolve("previous.json") : current;
            if (recovery == RecoveryMode.PREVIOUS && !Files.isRegularFile(selected)) throw new IOException("No previous snapshot");
            state = Files.exists(selected) ? JsonCodec.read(Files.readString(selected, StandardCharsets.UTF_8), Snapshot.class)
                : new Snapshot(1, 0, List.of(), Map.of(), preconfiguredAccounts);
            validate(state);
            if (recovery == RecoveryMode.PREVIOUS) {
                // Validate the WHOLE selected snapshot and its own referenced attachments first.
                // Do not rotate a corrupt current file over the verified previous snapshot.
                Path recoveryTemp = root.resolve("recovery.next.json");
                writeForced(recoveryTemp, JsonCodec.write(state).getBytes(StandardCharsets.UTF_8));
                beforeReplace.run(recoveryTemp);
                Files.move(recoveryTemp, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else if (!Files.exists(current) && !preconfiguredAccounts.isEmpty()) {
                save(state);
            }
        } catch (Exception e) { writerLock.release(); lockChannel.close(); throw new IOException("RECOVERY_WRITE_BLOCKED: validate snapshot and attachments before recovery", e); }
    }
    private WorkingCopy baseline() { return new WorkingCopy(UUID.randomUUID().toString(), state.lastCommitOrdinal(), state.records()); }
    private Result<ActorContext> actor(OperationContext op) {
        if (closed) return Result.failed("SNAPSHOT_UNAVAILABLE", "Store closed");
        if (op == null || op.sessionRef() == null) return Result.rejected("AUTH_REQUIRED", "Login required");
        try { return identities.resolve(op.sessionRef()); }
        catch (RuntimeException e) { return Result.failed("AUTH_TECHNICAL_FAILURE", "Identity verification unavailable"); }
    }
    @Override public synchronized Result<WorkingCopy> open(OperationContext op) {
        Result<ActorContext> actor = actor(op); if (!actor.hasValue()) return actor.failure();
        WorkingCopy wc = baseline();
        handles.put(wc.workingCopyId(), new Handle(wc, actor.value().accountId(), op.operation(), op.targetId()));
        return Result.value(wc);
    }
    /** Bootstrap inject this method only into B's auth implementation; never into CUI/business readers. */
    public synchronized List<Credential> authenticationView() {
        if (closed) throw new IllegalStateException("Store closed");
        return state.credentials();
    }
    @Override public synchronized Result<CommitReceipt> lookupResult(OperationContext op, BusinessRequest request) {
        Result<ActorContext> actor = actor(op); if (!actor.hasValue()) return actor.failure();
        try { return lookup(op, request, actor.value()); }
        catch (IllegalArgumentException e) { return Result.rejected("INVALID_INPUT", "Request key/original request invalid"); }
    }
    private Result<CommitReceipt> lookup(OperationContext op, BusinessRequest request, ActorContext actor) {
        if (op.requestKey() == null || !UUID.fromString(op.requestKey()).toString().equals(op.requestKey()))
            return Result.rejected("INVALID_INPUT", "Canonical UUID request key required");
        String fingerprint = JsonCodec.fingerprint(op.operation(), actor.accountId(), request);
        SavedResult saved = state.results().get(op.requestKey());
        if (saved == null) return new Result<>(Result.Status.NOT_FOUND, null, "NOT_FOUND", null, null, "No committed result");
        if (!saved.operation().equals(op.operation()) || !saved.accountId().equals(actor.accountId()) || !saved.fingerprint().equals(fingerprint))
            return Result.rejected("REQUEST_KEY_CONFLICT", "Request key belongs to another business intent");
        return new Result<>(Result.Status.SUCCESS, saved.receipt().replay(), null, null, null, null);
    }
    @Override public synchronized Result<CommitReceipt> submit(OperationContext op, BusinessRequest request, WorkingCopy wc, ChangeSet changes) {
        Result<ActorContext> actor = actor(op); if (!actor.hasValue()) return actor.failure();
        try {
            Result<CommitReceipt> prior = lookup(op, request, actor.value());
            if (prior.status() != Result.Status.NOT_FOUND) return prior;
            Handle handle = wc == null ? null : handles.get(wc.workingCopyId());
            if (handle == null || handle.copy() != wc || !handle.actor().equals(actor.value().accountId())
                || !handle.operation().equals(op.operation()) || !Objects.equals(handle.target(), op.targetId())
                || wc.baseLast() != state.lastCommitOrdinal())
                return Result.rejected("STALE_WORKING_COPY", "Reopen and rebuild with the original request/key");
            CommitValidator validator = validators.get(op.operation());
            if (validator == null) return Result.failed("VALIDATOR_UNAVAILABLE", "Business owner validator not connected");
            if (changes == null || changes.inserts().isEmpty() && changes.patches().isEmpty() && changes.removals().isEmpty())
                return Result.rejected("INVALID_INPUT", "Empty submission");
            // One lock-held acceptance time, after replay/stale checks and before owner validation.
            java.time.Instant acceptedAt = Objects.requireNonNull(businessTime.get());
            changes = SubmissionTime.assign(wc,changes,acceptedAt);
            Result<Void> check = validator.validate(op, actor.value(), request, wc, changes, acceptedAt);
            if (!check.hasValue()) return check.failure();
            boolean draftDeletion = op.operation().equals(DraftDeletion.OPERATION);
            if (draftDeletion) DraftDeletionGuard.check(op,request,wc,changes);
            long next = Math.addExact(state.lastCommitOrdinal(), 1);
            List<RecordData> assigned = new ArrayList<>();
            for (RecordData record : changes.inserts()) {
                if (record.version() != 1) return Result.rejected("INVALID_INPUT", "New records start at version one");
                if (record.table().equals("STOCK_ENTRY")) {
                    ObjectNode data = (ObjectNode) record.data();
                    if (data.path("commit_ordinal").asLong(-1) != 0) return Result.rejected("INVALID_INPUT", "Candidate must not assign commitOrdinal");
                    data.put("commit_ordinal", next);
                    record = new RecordData(record.table(), record.id(), record.version(), JsonCodec.write(data));
                }
                assigned.add(record);
            }
            for (ChangeSet.Patch p : changes.patches()) {
                if (!Set.of("PRODUCT", "PROMOTION", "DOCUMENT", "RECEIPT", "DOC_LINE", "LOSS_RECORD", "AFTERSALE").contains(p.table()))
                    return Result.rejected("INVALID_INPUT", "Append-only facts cannot be patched");
                if (p.table().equals("DOC_LINE")) {
                    DocumentLine line = wc.find("DOC_LINE",p.id()).orElseThrow().as(DocumentLine.class);
                    DocumentHeader document = wc.find("DOCUMENT",line.documentId()).orElseThrow().as(DocumentHeader.class);
                    if (!Set.of("DRAFT","PENDING_REVIEW","NEEDS_CORRECTION").contains(document.status()))
                        return Result.rejected("INVALID_INPUT", "Posted document lines cannot be patched");
                }
            }
            for (ChangeSet.Removal r : changes.removals()) {
                if (draftDeletion) continue; // Exact group/version/reference guard has already run.
                if (!op.operation().equals("RECEIPT_CORRECT") || !r.table().equals("DOC_LINE"))
                    return Result.rejected("INVALID_INPUT", "Posted facts cannot be removed");
                RecordData line = wc.find("DOC_LINE",r.id()).orElseThrow();
                DocumentHeader header = wc.find("DOCUMENT",line.as(DocumentLine.class).documentId()).orElseThrow().as(DocumentHeader.class);
                if (!header.kind().equals("RECEIPT") || !header.status().equals("NEEDS_CORRECTION"))
                    return Result.rejected("INVALID_INPUT", "Only unposted receipt correction lines may be removed");
            }
            WorkingCopy candidate = wc.overlay(new ChangeSet(assigned, changes.patches(), changes.removals()));
            CommitReceipt receipt = new CommitReceipt(op.requestKey(), changes.inserts().stream().map(RecordData::id).toList(), next, false);
            Map<String, SavedResult> results = new LinkedHashMap<>(state.results());
            results.put(op.requestKey(), new SavedResult(op.operation(), actor.value().accountId(),
                JsonCodec.fingerprint(op.operation(), actor.value().accountId(), request), receipt));
            Snapshot snapshot = new Snapshot(1, next, candidate.allRecords(), results, state.credentials());
            validate(snapshot);
            save(snapshot);
            // Nothing fallible after the atomic replacement: an installed snapshot is committed.
            state = snapshot; handles.clear();
            return new Result<>(Result.Status.SUCCESS, receipt, null, null, null, null);
        } catch (IllegalArgumentException e) {
            String code = Set.of("EVENT_CONFLICT", "STALE_WORKING_COPY", "REFERENCE_INVALID", "INVALID_DELETION_GROUP", "DRAFT_HAS_READ_TRACE", "DRAFT_HAS_EFFECTS").contains(e.getMessage()) ? e.getMessage() : "INVALID_INPUT";
            return Result.rejected(code, "Invalid request, reference or candidate");
        } catch (IOException | RuntimeException e) {
            return Result.failed("PERSISTENCE_FAILED", "No commit installed; retain original request and query result before retry");
        }
    }
    private void save(Snapshot snapshot) throws IOException {
        byte[] bytes = JsonCodec.write(snapshot).getBytes(StandardCharsets.UTF_8);
        Path temp = root.resolve("snapshot.next.json"), current = root.resolve("snapshot.json");
        writeForced(temp, bytes);
        if (Files.exists(current)) {
            Path backupTemp = root.resolve("previous.next.json");
            writeForced(backupTemp, Files.readAllBytes(current));
            Files.move(backupTemp, root.resolve("previous.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        beforeReplace.run(temp);
        Files.move(temp, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private static void writeForced(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private void validate(Snapshot snapshot) throws IOException {
        if (snapshot.schemaVersion() != 1 || snapshot.lastCommitOrdinal() < 0) throw new IOException("Invalid schema/last");
        WorkingCopy view = new WorkingCopy("integrity", snapshot.lastCommitOrdinal(), snapshot.records());
        Set<String> eventKeys = new HashSet<>(), reverseStock = new HashSet<>(), moneyKeys = new HashSet<>(), reverseMoney = new HashSet<>(), docKeys = new HashSet<>(), eventVersions = new HashSet<>();
        Set<String> accountIds = new HashSet<>(), usernames = new HashSet<>();
        for (Credential c : snapshot.credentials()) {
            if (c.accountId() == null || c.username() == null || c.passwordHash() == null || c.passwordHash().isBlank() || c.role() == null
                || !accountIds.add(c.accountId()) || !usernames.add(c.username())) throw new IOException("Invalid credentials view");
        }
        for (RecordData r : snapshot.records()) {
            if (!r.data().isObject()) throw new IOException("Record must be an object");
            if (r.table().equals("DOCUMENT")) {
                DocumentHeader h = r.as(DocumentHeader.class);
                if (!r.id().equals(h.documentId()) || !docKeys.add(h.requestKey()) || h.createdBy() == null || h.createdAt() == null
                    || (!accountIds.isEmpty() && !accountIds.contains(h.createdBy()))) throw new IOException("Invalid document identity");
                java.time.Instant.parse(h.createdAt());
            } else if (r.table().equals("DOC_EVENT")) {
                var data = r.data(); String document = data.path("documentId").asText();
                require(view,"DOCUMENT",document);
                if (!eventVersions.add(document + ":" + data.path("version").asLong()) || data.path("actorId").asText().isBlank()) throw new IOException("Invalid event/version");
                java.time.Instant.parse(data.path("occurredAt").asText());
            } else if (r.table().equals("DOC_LINE")) {
                DocumentLine line = r.as(DocumentLine.class);
                if (!r.id().equals(line.lineId())) throw new IOException("Invalid line identity");
                require(view, "DOCUMENT", line.documentId()); require(view, "PRODUCT", line.barcode());
                if (line.quantity() < 1 || line.quantity() > 9999) throw new IOException("Invalid quantity");
            } else if (r.table().equals("STOCK_ENTRY")) {
                StockEntry entry = r.as(StockEntry.class); require(view, "DOC_LINE", entry.sourceLineId());
                if (!r.id().equals(entry.stockEntryId()) || entry.position() == null || entry.eventKey() == null || !eventKeys.add(entry.eventKey())) throw new IOException("Invalid stock identity/event key");
                if (entry.commitOrdinal() <= 0 || entry.commitOrdinal() > snapshot.lastCommitOrdinal()) throw new IOException("Invalid ordinal");
                if (entry.receiptReviewId() != null) require(view, "RECEIPT_REVIEW", entry.receiptReviewId());
                if (entry.reverseOfId() != null) {
                    require(view, "STOCK_ENTRY", entry.reverseOfId());
                    if (entry.reverseOfId().equals(r.id()) || !reverseStock.add(entry.reverseOfId())) throw new IOException("Invalid duplicate stock reversal");
                }
            } else if (r.table().equals("MONEY_ENTRY")) {
                MoneyEntry entry = r.as(MoneyEntry.class); require(view, "DOCUMENT", entry.documentId());
                if (!r.id().equals(entry.moneyEntryId()) || entry.amountSigned() == null || !moneyKeys.add(entry.requestKey())) throw new IOException("Invalid money identity/key");
                entry.amountSigned().setScale(2, java.math.RoundingMode.UNNECESSARY);
                if (entry.reverseOfId() != null) {
                    require(view, "MONEY_ENTRY", entry.reverseOfId());
                    MoneyEntry original = view.find("MONEY_ENTRY",entry.reverseOfId()).orElseThrow().as(MoneyEntry.class);
                    if (original.reverseOfId() != null || !reverseMoney.add(entry.reverseOfId()) || original.amountSigned().add(entry.amountSigned()).signum() != 0)
                        throw new IOException("Invalid money reversal pairing");
                }
            } else if (r.table().equals("ATTACHMENT")) {
                AttachmentStore.Attachment a = r.as(AttachmentStore.Attachment.class);
                require(view,"RECEIPT",a.receiptId());
                if (!r.id().equals(a.attachmentId())) throw new IOException("Invalid attachment identity");
                AttachmentStore.verify(root, a);
            }
        }
        Set<Long> ordinals = new HashSet<>();
        for (Map.Entry<String, SavedResult> entry : snapshot.results().entrySet()) {
            CommitReceipt receipt = entry.getValue().receipt();
            if (!entry.getKey().equals(receipt.requestKey()) || receipt.commitOrdinal() <= 0
                || receipt.commitOrdinal() > snapshot.lastCommitOrdinal() || !ordinals.add(receipt.commitOrdinal()))
                throw new IOException("Invalid result/ordinal association");
        }
        if (snapshot.results().size() != snapshot.lastCommitOrdinal()) throw new IOException("Missing committed result");
        domainIntegrity.validate(view);
    }
    private static void require(WorkingCopy view, String table, String id) throws IOException {
        if (view.find(table, id).isEmpty()) throw new IOException("Invalid reference: " + table);
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; handles.clear(); try { writerLock.release(); } finally { lockChannel.close(); } }
    }
}
