package nz.ac.aut.pdc.supermarket.app;

import java.io.*;
import nz.ac.aut.pdc.supermarket.i18n.Texts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.shared.OperationContext.*;
import nz.ac.aut.pdc.shared.SubmissionPort.*;
import nz.ac.aut.pdc.supermarket.auth.*;
import nz.ac.aut.pdc.supermarket.aftersale.*;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;
import nz.ac.aut.pdc.supermarket.sales.OriginalSaleAccess;
import nz.ac.aut.pdc.supermarket.cui.*;

/** Unified assembly of the delivered real services; no test dependencies. */
public final class RepositoryBIntegration implements BIntegration {
    private final List<JsonStore.Credential> accounts;
    private final SharedStockAdapter stock=new SharedStockAdapter();
    private AuthService auth;
    private Supplier<List<JsonStore.Credential>> credentialView;
    private final Texts texts;
    private final PrintStream output;
    private final InputStream input;
    public RepositoryBIntegration(List<JsonStore.Credential> accounts) { this(accounts,new Texts("en"),System.out,System.in); }
    public RepositoryBIntegration(List<JsonStore.Credential> accounts,Texts texts,PrintStream output,InputStream input) {
        this.accounts=List.copyOf(accounts); this.texts=Objects.requireNonNull(texts);
        this.output=Objects.requireNonNull(output); this.input=Objects.requireNonNull(input);
    }
    public AuthService auth() { return Objects.requireNonNull(auth,"Application not initialized"); }
    @Override public IdentityResolver identities(Supplier<List<JsonStore.Credential>> credentials) {
        credentialView=credentials;
        auth=new LocalAuthService(new SharedCredentialStore(credentials),new Sha256PasswordHasher());
        return new SharedIdentityResolver(auth);
    }
    @Override public AOperations.StockDependency stock() { return stock; }
    @Override public List<JsonStore.Credential> initialAccounts() { return accounts; }
    @Override public JsonStore.IntegrityValidator integrity() { return new AftersaleIntegrityValidator(); }
    @Override public Map<String,CommitValidator> validators() {
        var registered=new HashMap<>(BCommitValidators.create(stock));
        // There is one production read admission. The older B trace-only operation is not registered.
        registered.remove(AftersaleDraftOperations.READ);
        return Map.copyOf(registered);
    }
    @Override public OriginalSaleAccess.DraftAccess drafts() { return new AftersaleDraftAccess(); }
    public Operations cuiOperations(Application app) { return new SharedOperations(app.storage(),app.ids()); }
    @Override public void run(Application app) {
        if (credentialView.get().isEmpty()) {
            output.println(texts.text("start.noAccounts")); return;
        }

        new CuiApp(auth(),cuiOperations(app),new InputValidator(),new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)),output,texts).run();
    }
}
