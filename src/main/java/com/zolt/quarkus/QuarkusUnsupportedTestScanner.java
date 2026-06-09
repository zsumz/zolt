package com.zolt.quarkus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class QuarkusUnsupportedTestScanner {
    private static final List<UnsupportedAnnotation> UNSUPPORTED_ANNOTATIONS = List.of(
            new UnsupportedAnnotation("@QuarkusTest", "Lio/quarkus/test/junit/QuarkusTest;"),
            new UnsupportedAnnotation("@QuarkusIntegrationTest", "Lio/quarkus/test/junit/QuarkusIntegrationTest;"),
            new UnsupportedAnnotation("@QuarkusMainTest", "Lio/quarkus/test/junit/QuarkusMainTest;"),
            new UnsupportedAnnotation("@QuarkusMainIntegrationTest", "Lio/quarkus/test/junit/QuarkusMainIntegrationTest;"));

    public List<QuarkusUnsupportedTest> scan(Path testOutputDirectory) {
        if (testOutputDirectory == null) {
            throw new QuarkusPlanException("Quarkus unsupported test scan requires a test output directory.");
        }
        if (!Files.isDirectory(testOutputDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(testOutputDirectory)) {
            return files
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .flatMap(path -> unsupportedTest(testOutputDirectory, path).stream())
                    .sorted(Comparator.comparing(test -> test.relativePath().toString()))
                    .toList();
        } catch (UncheckedIOException exception) {
            throw scanException(exception.getCause());
        } catch (IOException exception) {
            throw scanException(exception);
        }
    }

    private static QuarkusPlanException scanException(IOException exception) {
        return new QuarkusPlanException(
                "Could not inspect compiled test classes for Quarkus test annotations. "
                        + "Clean target/test-classes, run `zolt test` again, and check that target/ is readable.",
                exception);
    }

    private static Optional<QuarkusUnsupportedTest> unsupportedTest(Path testOutputDirectory, Path classFile) {
        String contents = classFileContents(classFile);
        for (UnsupportedAnnotation annotation : UNSUPPORTED_ANNOTATIONS) {
            if (contents.contains(annotation.descriptor())) {
                Path normalizedClassFile = classFile.toAbsolutePath().normalize();
                return Optional.of(new QuarkusUnsupportedTest(
                        normalizedClassFile,
                        testOutputDirectory.toAbsolutePath().normalize().relativize(normalizedClassFile),
                        annotation.name()));
            }
        }
        return Optional.empty();
    }

    private static String classFileContents(Path classFile) {
        try {
            return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record UnsupportedAnnotation(String name, String descriptor) {
    }
}
