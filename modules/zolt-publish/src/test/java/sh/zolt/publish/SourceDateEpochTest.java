package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class SourceDateEpochTest {
    @Test
    void absentValueIsNotReproducibleAndCarriesNoEpoch() {
        SourceDateEpoch parsed = SourceDateEpoch.parse(env(null));

        assertFalse(parsed.reproducible());
        assertEquals(Optional.empty(), parsed.epochSeconds());
    }

    @Test
    void validNonNegativeIntegerIsReproducibleAndCarriesTheEpoch() {
        SourceDateEpoch parsed = SourceDateEpoch.parse(env("1700000000"));

        assertTrue(parsed.reproducible());
        assertEquals(Optional.of(1_700_000_000L), parsed.epochSeconds());
    }

    @Test
    void zeroIsAValidReproducibleEpoch() {
        SourceDateEpoch parsed = SourceDateEpoch.parse(env("0"));

        assertTrue(parsed.reproducible());
        assertEquals(Optional.of(0L), parsed.epochSeconds());
    }

    @Test
    void surroundingWhitespaceIsToleratedForAnOtherwiseValidEpoch() {
        SourceDateEpoch parsed = SourceDateEpoch.parse(env("  1700000000  "));

        assertTrue(parsed.reproducible());
        assertEquals(Optional.of(1_700_000_000L), parsed.epochSeconds());
    }

    @Test
    void blankValueFailsLoudlyRatherThanSilentlyDisablingReproducibility() {
        PublishException exception = assertThrows(PublishException.class, () -> SourceDateEpoch.parse(env("")));

        assertActionable(exception, "blank");
    }

    @Test
    void whitespaceOnlyValueIsTreatedAsBlankAndFails() {
        PublishException exception = assertThrows(PublishException.class, () -> SourceDateEpoch.parse(env("   ")));

        assertActionable(exception, "blank");
    }

    @Test
    void nonIntegerValueFailsAndNamesTheOffendingValue() {
        PublishException exception =
                assertThrows(PublishException.class, () -> SourceDateEpoch.parse(env("not-an-epoch")));

        assertActionable(exception, "not an integer");
        assertTrue(exception.getMessage().contains("not-an-epoch"), exception.getMessage());
    }

    @Test
    void negativeValueFailsBecauseEpochSecondsAreNonNegative() {
        PublishException exception = assertThrows(PublishException.class, () -> SourceDateEpoch.parse(env("-1")));

        assertActionable(exception, "negative");
        assertTrue(exception.getMessage().contains("-1"), exception.getMessage());
    }

    private static void assertActionable(PublishException exception, String reason) {
        String message = exception.getMessage();
        assertTrue(message.contains(SourceDateEpoch.ENV_NAME), message);
        assertTrue(message.contains(reason), message);
        assertTrue(message.contains("Next:"), message);
    }

    private static Function<String, String> env(String value) {
        return name -> SourceDateEpoch.ENV_NAME.equals(name) ? value : null;
    }
}
