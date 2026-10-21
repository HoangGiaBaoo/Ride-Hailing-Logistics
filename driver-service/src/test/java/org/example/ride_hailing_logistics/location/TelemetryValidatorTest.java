package org.example.ride_hailing_logistics.location;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-LOC-003, FR-LOC-004, FR-LOC-013 voi nguong TBD-14. */
class TelemetryValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-05T08:30:00Z");
    private final TelemetryValidator validator = new TelemetryValidator(500, Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofSeconds(30), 70);

    @Test
    void validMessageIsAccepted() {
        TelemetryValidator.Result r = validator.validate(at(10.7769, 106.7009, 8, NOW), null, NOW);
        assertEquals(TelemetryValidator.Outcome.ACCEPTED, r.outcome());
        assertFalse(r.suspicious());
    }

    @Test
    void coordinateBoundsAreInclusive() {
        assertEquals(TelemetryValidator.Outcome.ACCEPTED, validator.validate(at(90, 180, 5, NOW), null, NOW).outcome());
        assertEquals(TelemetryValidator.Outcome.ACCEPTED, validator.validate(at(-90, -180, 5, NOW), null, NOW).outcome());
        assertRejected(at(90.0001, 0, 5, NOW));
        assertRejected(at(0, -180.0001, 5, NOW));
    }

    @Test
    void poorAccuracyIsRejected() {
        assertRejected(at(10.7, 106.7, 500.1, NOW));
        assertEquals(TelemetryValidator.Outcome.ACCEPTED, validator.validate(at(10.7, 106.7, 500, NOW), null, NOW).outcome());
    }

    @Test
    void timestampTooFarInFutureIsRejected() {
        assertRejected(at(10.7, 106.7, 5, NOW.plusSeconds(31)));
        assertEquals(TelemetryValidator.Outcome.ACCEPTED,
                validator.validate(at(10.7, 106.7, 5, NOW.plusSeconds(29)), null, NOW).outcome());
    }

    @Test
    void messageOlderThanMaxAgeIsRejected() {
        assertRejected(at(10.7, 106.7, 5, NOW.minus(Duration.ofMinutes(6))));
    }

    @Test
    void backfilledMessageIsHistoricalNotCurrent() {
        // Mat mang 1 phut roi gui bu: ghi nhan nhung khong lam vi tri hien tai (FR-LOC-013)
        assertEquals(TelemetryValidator.Outcome.HISTORICAL,
                validator.validate(at(10.7, 106.7, 5, NOW.minusSeconds(60)), null, NOW).outcome());
    }

    @Test
    void impossibleJumpIsFlaggedSuspicious() {
        LatestLocation previous = new LatestLocation(UUID.randomUUID(), 10.7769, 106.7009, 5, null, null,
                NOW.minusSeconds(3), NOW.minusSeconds(3), 1, "MOTORBIKE", false);
        // ~1 km trong 3 s = ~333 m/s > 70 m/s
        assertTrue(validator.validate(at(10.7859, 106.7009, 5, NOW), previous, NOW).suspicious());
        // ~100 m trong 3 s = ~33 m/s: binh thuong
        assertFalse(validator.validate(at(10.7778, 106.7009, 5, NOW), previous, NOW).suspicious());
    }

    @Test
    void missingFieldsAreRejected() {
        assertRejected(new Telemetry(null, 106.7, 5.0, null, null, NOW, 1L));
        assertRejected(new Telemetry(10.7, 106.7, null, null, null, NOW, 1L));
        assertRejected(new Telemetry(10.7, 106.7, 5.0, null, null, null, 1L));
    }

    private void assertRejected(Telemetry t) {
        assertEquals(TelemetryValidator.Outcome.REJECTED, validator.validate(t, null, NOW).outcome());
    }

    private static Telemetry at(double lat, double lng, double accuracy, Instant deviceTs) {
        return new Telemetry(lat, lng, accuracy, null, null, deviceTs, 1L);
    }
}
