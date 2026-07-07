package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
import java.util.List;

public record NativeReleaseVersion(
        String version,
        String commit,
        String createdAt,
        List<ReleaseTarget> targets) {
    public NativeReleaseVersion {
        targets = List.copyOf(targets);
    }
}
