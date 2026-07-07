package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
import java.nio.file.Path;

public record NativeVersionInstallResult(
        String channel,
        String version,
        ReleaseTarget target,
        boolean installed,
        Path executable) {
}
