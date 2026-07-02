package sh.zolt.quarkus.annotation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class ZoltQuarkusApplicationClassPredicate {
    private ZoltQuarkusApplicationClassPredicate() {
    }

    static boolean test(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        String classFilePath = className.replace('.', '/') + ".class";
        for (Path outputDirectory : normalizedOutputDirectories()) {
            Path classFile = outputDirectory.resolve(classFilePath).normalize();
            if (classFile.startsWith(outputDirectory) && Files.isRegularFile(classFile)) {
                return true;
            }
        }
        return false;
    }

    static Optional<Path> normalizedMainOutputDirectory() {
        return normalizedOutputDirectory(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
    }

    static Optional<Path> normalizedTestOutputDirectory() {
        return normalizedOutputDirectory(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY);
    }

    static List<Path> normalizedOutputDirectories() {
        return java.util.stream.Stream.of(normalizedMainOutputDirectory(), normalizedTestOutputDirectory())
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private static Optional<Path> normalizedOutputDirectory(String property) {
        String outputDirectory = System.getProperty(
                property,
                "");
        if (outputDirectory.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(outputDirectory).toAbsolutePath().normalize());
    }
}
