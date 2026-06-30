package com.zolt.release.update;

import com.zolt.release.ReleaseTarget;
import java.nio.file.Path;

public record NativeUpdateResult(
        String channel,
        ReleaseTarget target,
        String previousVersion,
        String availableVersion,
        boolean updated,
        Path executable) {
}
