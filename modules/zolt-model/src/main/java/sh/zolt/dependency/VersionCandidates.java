package sh.zolt.dependency;

import java.util.Optional;

/**
 * The update targets discovered for one coordinate, relative to its current version.
 *
 * <p>The three class-scoped targets are cumulative ceilings ordered {@code latestPatch <=
 * latestMinor <= latestMajor}: each is the newest suggestable version reachable without exceeding
 * that change class. {@link #selectedInMajor()} is the default {@code zolt update} target (stay in
 * the current major); {@link #selectedLatest()} is the {@code --latest} target. Every value is
 * absent when no suggestable newer version exists at that ceiling.
 */
public record VersionCandidates(
        String current,
        Optional<String> latestPatch,
        Optional<String> latestMinor,
        Optional<String> latestMajor) {

    /** Default update target: the newest suggestable version within the current major. */
    public Optional<String> selectedInMajor() {
        return latestMinor;
    }

    /** {@code --latest} target: the newest suggestable version overall. */
    public Optional<String> selectedLatest() {
        return latestMajor;
    }

    /** True when any suggestable newer version was found. */
    public boolean updateAvailable() {
        return latestMajor.isPresent();
    }
}
