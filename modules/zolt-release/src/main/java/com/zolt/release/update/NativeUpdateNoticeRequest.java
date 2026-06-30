package com.zolt.release.update;

import com.zolt.release.ReleaseTarget;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record NativeUpdateNoticeRequest(
        Path installRoot,
        Path currentExecutable,
        URI channelUri,
        ReleaseTarget target,
        Path stateDirectory,
        Instant now,
        Duration checkInterval,
        boolean disabled,
        boolean offline,
        boolean ci,
        boolean interactive) {
}
