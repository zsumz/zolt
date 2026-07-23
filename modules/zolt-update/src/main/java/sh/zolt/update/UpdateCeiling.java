package sh.zolt.update;

import sh.zolt.dependency.VersionCandidates;
import java.util.Optional;

/**
 * The change ceiling a {@code zolt update} run respects. The default and {@code --minor} stay within
 * the current major; {@code --patch} caps at patch; {@code --major}/{@code --latest} allow a major
 * bump. The ceiling picks which class-scoped candidate becomes the update target.
 */
public enum UpdateCeiling {
    DEFAULT,
    PATCH,
    MINOR,
    MAJOR,
    LATEST;

    Optional<String> target(VersionCandidates candidates) {
        return switch (this) {
            case PATCH -> candidates.latestPatch();
            case MINOR, DEFAULT -> candidates.latestMinor();
            case MAJOR, LATEST -> candidates.latestMajor();
        };
    }
}
