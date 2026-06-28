package com.zolt.release;

import java.net.URI;
import java.nio.file.Path;

public record NativeUpdateRequest(
        Path installRoot,
        Path currentExecutable,
        URI channelUri,
        ReleaseTarget target,
        Path workDirectory) {
}
