package nz.ac.aut.pdc.supermarket.cui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import nz.ac.aut.pdc.infrastructure.DataRoot;
import nz.ac.aut.pdc.infrastructure.JsonStore;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.supermarket.aftersale.BCommitValidators;
import nz.ac.aut.pdc.supermarket.app.AOperations;
import nz.ac.aut.pdc.supermarket.auth.AuthService;
import nz.ac.aut.pdc.supermarket.auth.LocalAuthService;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.auth.SharedCredentialStore;
import nz.ac.aut.pdc.supermarket.auth.SharedIdentityResolver;
import nz.ac.aut.pdc.supermarket.stock.SharedStockAdapter;

/**
 * B 侧应用装配：A DataRoot/JsonStore + B 认证 + B 校验器 + A/S 业务校验器。
 *
 * <p>入口 main/pom 仍由 A 的 Maven 框架决定；本类只提供可复用的装配，
 * 不创建 CUI、不写真实 data/ 之外的路径。</p>
 */
public final class BApplication implements AutoCloseable {

    private final JsonStore store;
    private final AuthService authService;

    public BApplication(Path projectRoot, List<JsonStore.Credential> credentials) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(credentials, "credentials");

        DataRoot dataRoot = DataRoot.forProject(projectRoot);
        AtomicReference<AuthService> authRef = new AtomicReference<>();
        OperationContext.IdentityResolver identityResolver = session -> {
            AuthService auth = authRef.get();
            if (auth == null) {
                return Result.failed("AUTH_TECHNICAL_FAILURE", "Authentication not initialized");
            }
            return new SharedIdentityResolver(auth).resolve(session);
        };

        SharedStockAdapter stockAdapter = new SharedStockAdapter();
        AOperations aOperations = new AOperations(
                new IdClock(Clock.system(IdClock.BUSINESS_ZONE)), identityResolver, stockAdapter);
        Map<String, SubmissionPort.CommitValidator> validators = new HashMap<>(aOperations.validators());
        validators.putAll(BCommitValidators.create(stockAdapter));

        this.store = new JsonStore(
                dataRoot,
                identityResolver,
                Map.copyOf(validators),
                state -> {
                    aOperations.validateRecovered(state);
                    stockAdapter.validateRecovered(state);
                },
                path -> { },
                JsonStore.RecoveryMode.CURRENT,
                credentials);
        this.authService = new LocalAuthService(
                new SharedCredentialStore(store), new Sha256PasswordHasher());
        authRef.set(authService);
    }

    public AuthService authService() {
        return authService;
    }

    public JsonStore store() {
        return store;
    }

    @Override
    public void close() throws IOException {
        store.close();
    }
}
