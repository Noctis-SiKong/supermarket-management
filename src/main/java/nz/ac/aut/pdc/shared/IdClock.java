package nz.ac.aut.pdc.shared;

import java.time.*;
import java.util.*;

/** IF-03: request keys are UUIDs; entity IDs are stable for an operation across wc rebuilds. */
public final class IdClock {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;
    public IdClock(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    public String newRequestKey() { return UUID.randomUUID().toString(); }
    public Instant now() { return clock.instant(); }
    public String entityId(String requestKey, String purpose) {
        UUID.fromString(requestKey);
        return UUID.nameUUIDFromBytes((requestKey + ":" + purpose).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    public static int compareIds(String a, String b) { return a.compareTo(b); }
}
