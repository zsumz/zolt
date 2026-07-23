package sh.zolt.sbom;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import sh.zolt.error.ActionableError;
import sh.zolt.error.ActionableException;

/**
 * Resolves {@code metadata.timestamp}. Omitted by default so the SBOM is byte-reproducible.
 *
 * <p>Precedence (first match wins): an explicit ISO-8601 {@code --timestamp} value; else
 * {@code SOURCE_DATE_EPOCH} (the reproducible-build anchor); else {@code --timestamp now} (an
 * explicit opt-in to wall-clock time); else omitted.
 */
public final class SbomTimestamp {
    static final String SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";
    static final String NOW = "now";

    private SbomTimestamp() {
    }

    public static Optional<String> resolve(Optional<String> timestampOption, Map<String, String> env, Clock clock) {
        if (timestampOption.isPresent() && !NOW.equals(timestampOption.get())) {
            return Optional.of(format(parseIso(timestampOption.get())));
        }
        Optional<Instant> sourceDateEpoch = sourceDateEpoch(env);
        if (sourceDateEpoch.isPresent()) {
            return sourceDateEpoch.map(SbomTimestamp::format);
        }
        if (timestampOption.isPresent() && NOW.equals(timestampOption.get())) {
            return Optional.of(format(clock.instant()));
        }
        return Optional.empty();
    }

    private static Optional<Instant> sourceDateEpoch(Map<String, String> env) {
        if (env == null) {
            return Optional.empty();
        }
        String epoch = env.get(SOURCE_DATE_EPOCH);
        if (epoch == null || epoch.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(epoch.trim())));
        } catch (NumberFormatException ignored) {
            // Malformed override: fall through to the next source rather than failing the SBOM.
            return Optional.empty();
        }
    }

    private static Instant parseIso(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ActionableException(ActionableError.of(
                    "Could not parse --timestamp value `" + value + "` as an ISO-8601 instant.",
                    "Pass an instant like 2026-07-23T00:00:00Z, or `now` for wall-clock time.",
                    exception));
        }
    }

    private static String format(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}
