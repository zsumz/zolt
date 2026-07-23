package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.error.ActionableException;

final class SbomTimestampTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-06-15T08:30:45Z"), ZoneOffset.UTC);
    private static final Map<String, String> EPOCH = Map.of("SOURCE_DATE_EPOCH", "1577836800");

    @Test
    void omittedByDefault() {
        assertEquals(Optional.empty(), SbomTimestamp.resolve(Optional.empty(), Map.of(), CLOCK));
    }

    @Test
    void explicitIsoWinsOverSourceDateEpoch() {
        assertEquals(
                Optional.of("2026-07-23T00:00:00Z"),
                SbomTimestamp.resolve(Optional.of("2026-07-23T00:00:00Z"), EPOCH, CLOCK));
    }

    @Test
    void sourceDateEpochUsedWhenNoExplicitValue() {
        assertEquals(
                Optional.of("2020-01-01T00:00:00Z"),
                SbomTimestamp.resolve(Optional.empty(), EPOCH, CLOCK));
    }

    @Test
    void sourceDateEpochBeatsNow() {
        assertEquals(
                Optional.of("2020-01-01T00:00:00Z"),
                SbomTimestamp.resolve(Optional.of("now"), EPOCH, CLOCK));
    }

    @Test
    void nowUsesWallClockWhenNoEpoch() {
        assertEquals(
                Optional.of("2024-06-15T08:30:45Z"),
                SbomTimestamp.resolve(Optional.of("now"), Map.of(), CLOCK));
    }

    @Test
    void malformedSourceDateEpochFallsThroughToOmission() {
        assertEquals(
                Optional.empty(),
                SbomTimestamp.resolve(Optional.empty(), Map.of("SOURCE_DATE_EPOCH", "not-a-number"), CLOCK));
    }

    @Test
    void malformedExplicitTimestampIsActionable() {
        ActionableException exception = assertThrows(
                ActionableException.class,
                () -> SbomTimestamp.resolve(Optional.of("yesterday"), Map.of(), CLOCK));
        assertTrue(exception.getMessage().contains("--timestamp"));
    }
}
