package com.zolt.build.nativeimage;

import java.nio.file.Path;

public record NativeImageResult(
        Path outputBinary,
        Path logFile,
        String output) {
}
