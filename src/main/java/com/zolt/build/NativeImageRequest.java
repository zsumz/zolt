package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

public record NativeImageRequest(
        Path executable,
        Path jarPath,
        List<Path> runtimeClasspath,
        String mainClass,
        Path outputBinary,
        Path logFile,
        List<String> arguments) {
    public NativeImageRequest {
        if (executable == null) {
            executable = Path.of("native-image");
        }
        runtimeClasspath = runtimeClasspath == null ? List.of() : List.copyOf(runtimeClasspath);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
