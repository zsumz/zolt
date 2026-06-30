package com.zolt.release.update;

import com.zolt.release.ReleaseTarget;
import java.net.URI;
import java.nio.file.Path;

public record NativeUpdateRequest(
        Path installRoot,
        Path currentExecutable,
        URI channelUri,
        ReleaseTarget target,
        Path workDirectory) {
}
