package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
import java.nio.file.Path;

public record NativeUpdateResult(
        String channel,
        ReleaseTarget target,
        String previousVersion,
        String availableVersion,
        boolean updated,
        Path executable) {
}
