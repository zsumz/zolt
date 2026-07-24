package sh.zolt.publish;

import java.util.Optional;
import java.util.function.Function;

/**
 * The parsed {@code SOURCE_DATE_EPOCH} reproducible-build anchor, shared by the publish signer and
 * the Central readiness check so both interpret the variable identically. There are exactly three
 * outcomes, with no silent middle ground:
 *
 * <ul>
 *   <li><b>absent</b> (the variable is unset) &mdash; {@linkplain #reproducible() not reproducible};
 *       signing falls back to the wall clock.
 *   <li><b>a valid non-negative integer</b> &mdash; reproducible; {@link #epochSeconds()} carries the
 *       frozen instant (seconds since the Unix epoch, UTC).
 *   <li><b>blank, non-integer, or negative</b> &mdash; an actionable {@link PublishException}. A
 *       reproducible build hinges on this value, so a malformed one fails loudly rather than silently
 *       degrading to wall-clock signing while the environment still claims reproducibility.
 * </ul>
 *
 * <p>This is a standalone value type: both {@link PublishSigner} and
 * {@link PublishCentralReadinessService} call {@link #parse(Function)} so the "is this a
 * reproducible build?" decision and its failure modes stay in one place.
 */
public record SourceDateEpoch(Optional<Long> epochSeconds) {
    /** Environment variable naming the reproducible-build timestamp, in seconds since the Unix epoch. */
    public static final String ENV_NAME = "SOURCE_DATE_EPOCH";

    private static final SourceDateEpoch ABSENT = new SourceDateEpoch(Optional.empty());

    public SourceDateEpoch {
        epochSeconds = epochSeconds == null ? Optional.empty() : epochSeconds;
    }

    /**
     * Reads and interprets {@code SOURCE_DATE_EPOCH} from {@code environment}. Returns the absent
     * value when the variable is unset; throws {@link PublishException} for a blank, non-integer, or
     * negative value.
     */
    public static SourceDateEpoch parse(Function<String, String> environment) {
        String rawValue = environment.apply(ENV_NAME);
        if (rawValue == null) {
            return ABSENT;
        }
        if (rawValue.isBlank()) {
            throw invalid(rawValue, "it is blank");
        }
        long parsed;
        try {
            parsed = Long.parseLong(rawValue.trim());
        } catch (NumberFormatException notAnInteger) {
            throw invalid(rawValue, "it is not an integer");
        }
        if (parsed < 0) {
            throw invalid(rawValue, "it is negative");
        }
        return new SourceDateEpoch(Optional.of(parsed));
    }

    /**
     * Whether signing and readiness should run in reproducible mode &mdash; true only when a valid
     * epoch is pinned. Absent is the sole non-throwing not-reproducible outcome; blank, malformed, and
     * negative values never reach here because {@link #parse(Function)} rejects them.
     */
    public boolean reproducible() {
        return epochSeconds.isPresent();
    }

    private static PublishException invalid(String rawValue, String reason) {
        return new PublishException(
                ENV_NAME + " is set to \"" + rawValue + "\" but " + reason
                        + ". Expected a non-negative integer number of seconds since the Unix epoch"
                        + " (for example 1700000000). Next: correct " + ENV_NAME
                        + " or unset it to sign with the wall clock.");
    }
}
