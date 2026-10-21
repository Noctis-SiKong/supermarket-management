package nz.ac.aut.pdc.supermarket.cui;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Test;

import nz.ac.aut.pdc.shared.IdClock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SharedIdGeneratorTest {

    @Test
    public void generatesDistinctIdsAndUsesSharedClock() {
        Instant now = Instant.parse("2026-10-21T04:00:00Z");
        IdClock clock = new IdClock(Clock.fixed(now, ZoneId.of("Asia/Shanghai")));
        SharedIdGenerator ids = new SharedIdGenerator(
                clock, "11111111-1111-1111-1111-111111111111");

        String first = ids.nextId("SE");
        String second = ids.nextId("SE");

        assertNotEquals(first, second);
        assertEquals(now, ids.now());
    }
}
