package nz.ac.aut.pdc.supermarket.stock;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StockValidatorTest {

    private final StockValidator validator = new StockValidator();

    @Test
    public void acceptsBalancedReplenishmentWhenSourceHasEnough() {
        List<StockChangeLine> changes = List.of(
                new StockChangeLine("A", Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine("A", Position.S, 3, "doc-1", "T-1", null));

        assertTrue(validator.validate(changes, balances(Position.W, 10, Position.S, 5)).isEmpty());
    }

    @Test
    public void rejectsReplenishmentWhenWarehouseInsufficient() {
        List<StockChangeLine> changes = List.of(
                new StockChangeLine("A", Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine("A", Position.S, 3, "doc-1", "T-1", null));

        StockValidator.StockRejection rejection =
                validator.validate(changes, balances(Position.W, 2, Position.S, 5)).orElseThrow();

        assertEquals("INSUFFICIENT_STOCK", rejection.code());
    }

    @Test
    public void rejectsUnbalancedTransfer() {
        List<StockChangeLine> changes = List.of(
                new StockChangeLine("A", Position.W, -3, "doc-1", "T-1", null),
                new StockChangeLine("A", Position.S, 2, "doc-1", "T-1", null));

        StockValidator.StockRejection rejection =
                validator.validate(changes, balances(Position.W, 10, Position.S, 5)).orElseThrow();

        assertEquals("TRANSFER_UNBALANCED", rejection.code());
    }

    @Test
    public void rejectsZeroAndOutOfRangeQuantities() {
        List<StockChangeLine> zero = List.of(new StockChangeLine("A", Position.S, 0, null, null, null));
        List<StockChangeLine> tooLarge = List.of(new StockChangeLine("A", Position.S, 10000, null, null, null));

        assertEquals("INVALID_INPUT",
                validator.validate(zero, balances(Position.S, 100)).orElseThrow().code());
        assertEquals("INVALID_INPUT",
                validator.validate(tooLarge, balances(Position.S, 100)).orElseThrow().code());
    }

    private Map<StockValidator.BalanceKey, Integer> balances(Position position, int quantity) {
        return Map.of(new StockValidator.BalanceKey("A", position), quantity);
    }

    private Map<StockValidator.BalanceKey, Integer> balances(
            Position firstPosition, int firstQuantity, Position secondPosition, int secondQuantity) {
        return Map.of(
                new StockValidator.BalanceKey("A", firstPosition), firstQuantity,
                new StockValidator.BalanceKey("A", secondPosition), secondQuantity);
    }
}
