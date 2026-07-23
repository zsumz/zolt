package sh.zolt.update;

import sh.zolt.dependency.UpdateClass;
import java.util.Optional;

/**
 * The per-class update targets for one surface. {@code patch}/{@code minor}/{@code major} are the
 * newest suggestable versions reachable at each cumulative change ceiling. {@code selectedInMajor}
 * is the default {@code zolt update} target and {@code selectedLatest} is the {@code --latest}
 * target, each paired with its change class. Every value is absent when no such version exists.
 */
public record OutdatedCandidates(
        Optional<String> patch,
        Optional<String> minor,
        Optional<String> major,
        Optional<String> selectedInMajor,
        Optional<UpdateClass> selectedInMajorClass,
        Optional<String> selectedLatest,
        Optional<UpdateClass> selectedLatestClass) {

    public static OutdatedCandidates none() {
        return new OutdatedCandidates(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
