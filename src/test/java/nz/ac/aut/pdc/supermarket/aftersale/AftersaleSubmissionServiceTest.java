package nz.ac.aut.pdc.supermarket.aftersale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import nz.ac.aut.pdc.shared.ChangeSet;
import nz.ac.aut.pdc.shared.JsonCodec.BusinessRequest;
import nz.ac.aut.pdc.shared.OperationContext;
import nz.ac.aut.pdc.shared.Result;
import nz.ac.aut.pdc.shared.SubmissionPort;
import nz.ac.aut.pdc.shared.WorkingCopy;
import nz.ac.aut.pdc.supermarket.auth.Role;
import nz.ac.aut.pdc.supermarket.fixture.FakeIdGenerator;
import nz.ac.aut.pdc.supermarket.fixture.FakeProductCatalog;
import nz.ac.aut.pdc.supermarket.stock.StockService;
import nz.ac.aut.pdc.supermarket.stock.StockValidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AftersaleSubmissionServiceTest {

    @Test
    public void submitsAssembledChangeSetOnce() {
        FakeIdGenerator ids = new FakeIdGenerator(Instant.parse("2026-10-21T04:00:00Z"));
        CapturingSubmissionPort port = new CapturingSubmissionPort();
        AftersaleService aftersaleService = new AftersaleService(
                id -> Optional.of(new OriginalSaleLine(
                        "SL-1", "A", 3, new BigDecimal("10.00"),
                        Instant.parse("2026-10-01T00:00:00Z"))),
                id -> List.of(),
                id -> Optional.of(new ProductSnapshot("商品", new BigDecimal("8.00"))),
                new StockService(new FakeProductCatalog(Set.of("A")), ids, new StockValidator()),
                new QuotaViewBuilder(),
                new QuotaCalculator(),
                new QualityPairValidator(),
                new AftersaleAssembler(ids),
                ids);
        AftersaleSubmissionService service = new AftersaleSubmissionService(
                aftersaleService, new AftersaleChangeSetAssembler(), port);
        AftersaleCommand command = new AftersaleCommand("REQ-1", "V-1", List.of(
                new AftersaleCommandLine("SL-1", RedemptionKind.RETURN, 1, false, false,
                        AftersaleReasonKind.OTHER, "顾客原因", null, 0, false)));
        OperationContext operation = new OperationContext(
                null, "REQ-1", AftersaleChangeSetAssembler.OPERATION, "AF-1");
        OperationContext.ActorContext actor =
                new OperationContext.ActorContext("acc-1", OperationContext.Role.CLERK);

        Result<SubmissionPort.CommitReceipt> result = service.submit(
                command, new WorkingCopy("wc-1", 1, List.of()), operation, actor);

        assertEquals(Result.Status.SUCCESS, result.status());
        assertNotNull(port.captured);
        assertTrue(port.captured.inserts().stream().anyMatch(r -> r.table().equals("REDEMPTION")));
        assertEquals(1, port.submitCount);
    }

    private static final class CapturingSubmissionPort implements SubmissionPort {
        private ChangeSet captured;
        private int submitCount;

        @Override
        public Result<WorkingCopy> open(OperationContext op) {
            return Result.failed("NOT_IMPLEMENTED", "test double");
        }

        @Override
        public Result<CommitReceipt> submit(
                OperationContext op, BusinessRequest request, WorkingCopy wc, ChangeSet changes) {
            captured = changes;
            submitCount++;
            return new Result<>(Result.Status.SUCCESS,
                    new CommitReceipt(request.json(), List.of("AF-1"), 1, false),
                    null, null, null, null);
        }

        @Override
        public Result<CommitReceipt> lookupResult(OperationContext op, BusinessRequest request) {
            return new Result<>(Result.Status.NOT_FOUND, null, null, null, null, null);
        }
    }
}
