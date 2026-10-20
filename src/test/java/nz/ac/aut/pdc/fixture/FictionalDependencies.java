package nz.ac.aut.pdc.fixture;

import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import java.io.IOException;
import java.util.*;

/** FICTIONAL/NON-PRODUCTION v1.0.2: identity tokens and capturing stock double; NO stock algorithm. */
public final class FictionalDependencies implements IdentityResolver, AOperations.StockDependency {
    public final Object manager = new Object(), clerk = new Object(), admin = new Object();
    public boolean rejectStock;
    public int prepares, reviews;
    public WorkingCopy seenCopy;
    public ChangeSet seenOverlay;
    @Override public Result<ActorContext> resolve(Object token) {
        if (token == manager) return Result.value(new ActorContext("fictional-manager", Role.MANAGER));
        if (token == clerk) return Result.value(new ActorContext("fictional-clerk", Role.CLERK));
        if (token == admin) return Result.value(new ActorContext("fictional-admin", Role.ADMIN));
        return Result.rejected("AUTH_REQUIRED", "Fictional token unrecognized");
    }
    @Override public Result<ChangeSet> prepare(WorkingCopy wc, ChangeSet overlay, List<StockEntry> group) {
        prepares++; seenCopy = wc; seenOverlay = overlay;
        if (rejectStock) return Result.rejected("INSUFFICIENT_STOCK", "Fictional rejection, not calculated stock");
        return Result.prepared(new ChangeSet(group.stream().map(e -> RecordData.of("STOCK_ENTRY",e.stockEntryId(),e)).toList(), List.of()));
    }
    @Override public Result<Void> review(WorkingCopy wc, BusinessRequest original, ChangeSet changes) {
        reviews++; return rejectStock ? Result.rejected("INSUFFICIENT_STOCK", "Fictional rejection") : Result.value(null);
    }
    @Override public void validateRecovered(WorkingCopy state) throws IOException {
        // Intentionally no stock/aftersale validation: this fixture does not certify AC-85/full recovery.
    }
}
