package nz.ac.aut.pdc.supermarket.app;

import java.util.*;
import java.util.function.Supplier;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.IdentityResolver;
import nz.ac.aut.pdc.shared.SubmissionPort.CommitValidator;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;

/** Assembly boundary implemented by the repository assembly, not a second definition of B's domain contracts.
 * Production composes {@code RepositoryBIntegration} directly in {@code Main}; the interface remains the
 * constructor seam for tests and alternate assemblies. No service-provider file is used.
 * Credential supplier belongs exclusively to the authentication backend, never CUI.
 */
public interface BIntegration {
    IdentityResolver identities(Supplier<List<JsonStore.Credential>> credentials);
    AOperations.StockDependency stock();
    OriginalSaleAccess.DraftAccess drafts();
    Map<String,CommitValidator> validators();
    JsonStore.IntegrityValidator integrity();
    List<JsonStore.Credential> initialAccounts();
    /** Backend coordinators receive internal services. CUI receives their authorized projections only. */
    void run(Application services) throws Exception;
}
