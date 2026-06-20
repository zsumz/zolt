package io.quarkus.bootstrap.workspace;

import java.nio.file.Path;

public record SourceDir(
        Path sourceDirectory,
        Path outputDirectory) {
    public static SourceDir of(Path sourceDirectory, Path outputDirectory) {
        return new SourceDir(sourceDirectory, outputDirectory);
    }
}
