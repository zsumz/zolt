package sh.zolt.release.update;

import java.util.List;

public record NativeReleaseListResult(
        String channel,
        List<NativeReleaseVersion> releases) {
    public NativeReleaseListResult {
        releases = List.copyOf(releases);
    }
}
