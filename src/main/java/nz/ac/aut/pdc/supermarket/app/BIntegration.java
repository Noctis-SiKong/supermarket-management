package nz.ac.aut.pdc.supermarket.app;

import java.util.*;
import java.util.function.Supplier;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.IdentityResolver;
import nz.ac.aut.pdc.shared.SubmissionPort.CommitValidator;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;

/** Assembly SPI implemented by B, not a second definition of B's domain contracts.
 * Register one provider in META-INF/services/nz.ac.aut.pdc.supermarket.app.BIntegration.
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
