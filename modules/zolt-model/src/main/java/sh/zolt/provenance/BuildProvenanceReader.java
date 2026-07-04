package sh.zolt.provenance;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Assembles a {@link BuildProvenance} from injected inputs so that {@code zolt-model} stays the leaf
 * module (it does not read {@code ZoltCli.version()} or the lockfile — those live in other modules and are
 * passed in by the caller, ).
 *
 * <p>Sources:
 *
 * <ul>
 *   <li>git — {@link GitProvenanceReader#read(Path)} against {@code projectRoot}.
 *   <li>timestamp — {@code SOURCE_DATE_EPOCH}-aware: if {@code env.get("SOURCE_DATE_EPOCH")} parses as
 *       epoch-seconds, use {@link Instant#ofEpochSecond(long)}; otherwise {@code clock.instant()}.
 *       This is the reproducible-build anchor.
 *   <li>JDK — {@code java.version} / {@code java.vendor} from an injected {@link Properties} source
 *       (defaults to {@link System#getProperties()}).
 * </ul>
 */
public final class BuildProvenanceReader {

    static final String SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";

    private final GitProvenanceReader gitReader;
    private final Properties systemProperties;

    public BuildProvenanceReader() {
        this(new GitProvenanceReader(), System.getProperties());
    }

    BuildProvenanceReader(GitProvenanceReader gitReader, Properties systemProperties) {
        this.gitReader = gitReader;
        this.systemProperties = systemProperties;
    }

    /**
     * Assembles build provenance for {@code projectRoot}.
     *
     * @param projectRoot the project root to read git state from
     * @param zoltVersion the Zolt version (an input; the caller supplies {@code ZoltCli.version()})
     * @param resolutionFingerprint the lockfile fingerprint, empty when there is no lockfile
     * @param env the process environment, consulted for {@code SOURCE_DATE_EPOCH}
     * @param clock the clock used when {@code SOURCE_DATE_EPOCH} is absent
     */
    public BuildProvenance read(
            Path projectRoot,
            String zoltVersion,
            Optional<String> resolutionFingerprint,
            Map<String, String> env,
            Clock clock) {
        GitProvenance git = gitReader.read(projectRoot).orElseGet(GitProvenance::none);
        Instant buildTimestamp = resolveTimestamp(env, clock);
        String jdkVersion = systemProperties.getProperty("java.version", "");
        String jdkVendor = systemProperties.getProperty("java.vendor", "");
        return new BuildProvenance(git, buildTimestamp, zoltVersion, jdkVersion, jdkVendor, resolutionFingerprint);
    }

    /** {@code SOURCE_DATE_EPOCH} (epoch-seconds) wins over the clock for reproducible builds. */
    private static Instant resolveTimestamp(Map<String, String> env, Clock clock) {
        if (env != null) {
            String epoch = env.get(SOURCE_DATE_EPOCH);
            if (epoch != null && !epoch.isBlank()) {
                try {
                    return Instant.ofEpochSecond(Long.parseLong(epoch.trim()));
                } catch (NumberFormatException ignored) {
                    // Malformed override: fall back to the clock rather than failing.
                }
            }
        }
        return clock.instant();
    }
}
