package com.zolt.quarkus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class ZoltQuarkusApplicationClassPredicate {
    private ZoltQuarkusApplicationClassPredicate() {
    }

    static boolean test(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        Optional<Path> outputDirectory = normalizedMainOutputDirectory();
        if (outputDirectory.isEmpty()) {
            return false;
        }
        Path classFile = outputDirectory.get().resolve(className.replace('.', '/') + ".class").normalize();
        return classFile.startsWith(outputDirectory.get()) && Files.isRegularFile(classFile);
    }

    static Optional<Path> normalizedMainOutputDirectory() {
        String mainOutputDirectory = System.getProperty(
                QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY,
                "");
        if (mainOutputDirectory.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(mainOutputDirectory).toAbsolutePath().normalize());
    }
}
