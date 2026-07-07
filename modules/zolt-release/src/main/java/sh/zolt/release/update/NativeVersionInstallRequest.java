package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
import java.net.URI;
import java.nio.file.Path;

public record NativeVersionInstallRequest(
        Path installRoot,
        Path currentExecutable,
        URI releaseIndexUri,
        String version,
        ReleaseTarget target,
        Path workDirectory) {
}
