package com.zolt.selfhost;

import java.nio.file.Path;

public record NativeSmokeResult(
        Path binary,
        Path workDirectory,
        Path archive,
        Path projectDirectory) {
}
