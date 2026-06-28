package com.zolt.release;

import java.nio.file.Path;

public record NativeUpdateResult(
        String channel,
        ReleaseTarget target,
        String previousVersion,
        String availableVersion,
        boolean updated,
        Path executable) {
}
