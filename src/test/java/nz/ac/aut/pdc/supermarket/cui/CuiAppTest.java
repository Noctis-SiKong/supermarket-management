package nz.ac.aut.pdc.supermarket.cui;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import nz.ac.aut.pdc.supermarket.aftersale.RedemptionKind;
import nz.ac.aut.pdc.supermarket.auth.AuthService;
import nz.ac.aut.pdc.supermarket.auth.CredentialStore;
import nz.ac.aut.pdc.supermarket.auth.LocalAuthService;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher;
import nz.ac.aut.pdc.supermarket.fixture.InMemoryCredentialStore;
import nz.ac.aut.pdc.supermarket.stock.Position;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CuiAppTest {

    @Test
    public void replenishMenuDispatchesToOperations() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n1\n690123\n3\n0\nexit\n", operations);

        assertTrue(output.contains("登录成功"));
        assertEquals("acc-1", operations.lastActor.accountId());
        assertEquals(Role.CLERK, operations.lastActor.role());
        assertEquals("690123", operations.replenishBarcode);
        assertEquals(3, operations.replenishQuantity);
    }

    @Test
    public void lossMenuDispatchesSourcePosition() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n2\n690123\nW\n2\n0\nexit\n", operations);

        assertTrue(output.contains("登录成功"));
        assertEquals("690123", operations.lossBarcode);
        assertEquals(Position.W, operations.lossPosition);
        assertEquals(2, operations.lossQuantity);
    }

    @Test
    public void aftersaleMenuDispatchesQualificationFacts() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n3\nSL-1\nRETURN\n2\ny\nn\n0\nexit\n", operations);

        assertTrue(output.contains("登录成功"));
        assertEquals("SL-1", operations.saleLineId);
        assertEquals(RedemptionKind.RETURN, operations.kind);
        assertEquals(2, operations.aftersaleQuantity);
        assertTrue(operations.opened);
        assertEquals(Boolean.FALSE, operations.qualityProblem);
    }

    @Test
    public void invalidQuantityIsRejectedBeforeOperations() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n1\n690123\n0\n0\nexit\n", operations);

        assertTrue(output.contains("输入错误"));
        assertEquals(null, operations.replenishBarcode);
    }

    @Test
    public void invalidPositionIsRejectedBeforeOperations() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n2\n690123\nX\n2\n0\nexit\n", operations);

        assertTrue(output.contains("输入错误：位置只能是 S 或 W"));
        assertEquals(null, operations.lossBarcode);
    }

    @Test
    public void exchangeAftersaleDispatchesQualityFact() {
        RecordingOperations operations = new RecordingOperations();

        String output = run("alice\nsecret\n3\nSL-1\nEXCHANGE\n1\nn\ny\n0\nexit\n", operations);

        assertTrue(output.contains("登录成功"));
        assertEquals(RedemptionKind.EXCHANGE, operations.kind);
        assertEquals(Boolean.FALSE, operations.opened);
        assertEquals(Boolean.TRUE, operations.qualityProblem);
    }

    private String run(String input, RecordingOperations operations) {
        Sha256PasswordHasher hasher = new Sha256PasswordHasher();
        InMemoryCredentialStore credentialStore = new InMemoryCredentialStore();
        credentialStore.add(new CredentialStore.StoredCredential(
                "acc-1", "alice", Role.CLERK, hasher.hash("secret".toCharArray())));
        AuthService authService = new LocalAuthService(credentialStore, hasher);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8);
        CuiApp app = new CuiApp(
                authService,
                operations,
                new InputValidator(),
                new BufferedReader(new StringReader(input)),
                printStream);
        app.run();
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class RecordingOperations implements Operations {
        private CuiActor lastActor;
        private String replenishBarcode;
        private int replenishQuantity;
        private String lossBarcode;
        private Position lossPosition;
        private int lossQuantity;
        private String saleLineId;
        private RedemptionKind kind;
        private int aftersaleQuantity;
        private boolean opened;
        private Boolean qualityProblem;

        @Override
        public OperationFeedback replenish(CuiActor actor, String barcode, int quantity) {
            lastActor = actor;
            replenishBarcode = barcode;
            replenishQuantity = quantity;
            return OperationFeedback.ok("补货候选已受理（测试替身，未提交）");
        }

        @Override
        public OperationFeedback recordLoss(
                CuiActor actor, String barcode, Position position, int quantity) {
            lastActor = actor;
            lossBarcode = barcode;
            lossPosition = position;
            lossQuantity = quantity;
            return OperationFeedback.ok("报损候选已受理（测试替身，未提交）");
        }

        @Override
        public OperationFeedback aftersale(
                CuiActor actor, String saleLineId, RedemptionKind kind,
                int quantity, boolean opened, boolean qualityProblem) {
            lastActor = actor;
            this.saleLineId = saleLineId;
            this.kind = kind;
            this.aftersaleQuantity = quantity;
            this.opened = opened;
            this.qualityProblem = qualityProblem;
            return OperationFeedback.ok("售后候选已受理（测试替身，未提交）");
        }
    }
}

