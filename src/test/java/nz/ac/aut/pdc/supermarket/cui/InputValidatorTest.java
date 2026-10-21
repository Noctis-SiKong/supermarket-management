package nz.ac.aut.pdc.supermarket.cui;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class InputValidatorTest {

    private final InputValidator validator = new InputValidator();

    @Test
    public void acceptsQuantityAtBoundaries() throws Exception {
        assertEquals(1, validator.parseQuantity("1"));
        assertEquals(9999, validator.parseQuantity("9999"));
    }

    @Test
    public void rejectsNonIntegerOrOutOfRangeQuantity() {
        assertInvalidQuantity("0");
        assertInvalidQuantity("10000");
        assertInvalidQuantity("1.5");
        assertInvalidQuantity("abc");
        assertInvalidQuantity("");
    }

    @Test
    public void parsesMoneyWithinTwoDecimalPlaces() throws Exception {
        assertEquals(new BigDecimal("0.00"), validator.parseMoney("0.00"));
        assertEquals(new BigDecimal("999999.99"), validator.parseMoney("999999.99"));
        assertEquals(new BigDecimal("12.30"), validator.parseMoney("12.30"));
    }

    @Test
    public void rejectsInvalidMoney() {
        assertInvalidMoney("-1");
        assertInvalidMoney("1000000");
        assertInvalidMoney("12.345");
        assertInvalidMoney("abc");
    }

    private void assertInvalidQuantity(String raw) {
        try {
            validator.parseQuantity(raw);
            fail("expected InvalidInputException for " + raw);
        } catch (InputValidator.InvalidInputException expected) {
            // expected
        }
    }

    private void assertInvalidMoney(String raw) {
        try {
            validator.parseMoney(raw);
            fail("expected InvalidInputException for " + raw);
        } catch (InputValidator.InvalidInputException expected) {
            // expected
        }
    }
}
