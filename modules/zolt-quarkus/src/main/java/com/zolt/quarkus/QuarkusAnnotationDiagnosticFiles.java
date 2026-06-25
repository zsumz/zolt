package com.zolt.quarkus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

final class QuarkusAnnotationDiagnosticFiles {
    private static final Set<String> DIAGNOSTIC_PROPERTIES = Set.of(
            "zolt.quarkus.test-class-bean-diagnostic-file",
            "quarkus.builder.graph-output");

    private QuarkusAnnotationDiagnosticFiles() {
    }

    static void reset(QuarkusAnnotationLaunchRequest request) {
        for (String argument : request.jvmArguments()) {
            diagnosticPath(argument).ifPresent(QuarkusAnnotationDiagnosticFiles::reset);
        }
    }

    private static Optional<Path> diagnosticPath(String argument) {
        if (argument == null || !argument.startsWith("-D")) {
            return Optional.empty();
        }
        int separator = argument.indexOf('=');
        if (separator <= 2) {
            return Optional.empty();
        }
        String property = argument.substring(2, separator);
        if (!DIAGNOSTIC_PROPERTIES.contains(property)) {
            return Optional.empty();
        }
        String value = argument.substring(separator + 1);
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value).toAbsolutePath().normalize());
    }

    private static void reset(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException exception) {
            throw new QuarkusAugmentationException(
                    "Could not reset Quarkus annotation diagnostic file "
                            + path
                            + " before test launch. Check that the target/quarkus/annotation-runner directory is writable.",
                    exception);
        }
    }
}
