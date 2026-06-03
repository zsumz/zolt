package com.zolt.build;

import java.nio.file.Path;

public record NativeImageResult(
        Path outputBinary,
        Path logFile,
        String output) {
}
