package com.zolt.quarkus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class QuarkusUnsupportedTestScanner {
    private static final List<QuarkusTestAnnotation> QUARKUS_TEST_ANNOTATIONS = List.of(
            new QuarkusTestAnnotation("@QuarkusTest", "Lio/quarkus/test/junit/QuarkusTest;", true),
            new QuarkusTestAnnotation("@QuarkusIntegrationTest", "Lio/quarkus/test/junit/QuarkusIntegrationTest;", false),
            new QuarkusTestAnnotation("@QuarkusMainTest", "Lio/quarkus/test/junit/main/QuarkusMainTest;", false),
            new QuarkusTestAnnotation(
                    "@QuarkusMainIntegrationTest",
                    "Lio/quarkus/test/junit/main/QuarkusMainIntegrationTest;",
                    false),
            new QuarkusTestAnnotation("@QuarkusTestResource", "Lio/quarkus/test/common/QuarkusTestResource;", false),
            new QuarkusTestAnnotation(
                    "@QuarkusTestResource.List",
                    "Lio/quarkus/test/common/QuarkusTestResource$List;",
                    false),
            new QuarkusTestAnnotation("@WithTestResource", "Lio/quarkus/test/common/WithTestResource;", false),
            new QuarkusTestAnnotation(
                    "@WithTestResource.List",
                    "Lio/quarkus/test/common/WithTestResource$List;",
                    false),
            new QuarkusTestAnnotation("@TestProfile", "Lio/quarkus/test/junit/TestProfile;", false));

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
                    .flatMap(path -> unsupportedTests(testOutputDirectory, path).stream())
                    .sorted(Comparator.comparing((QuarkusUnsupportedTest test) -> test.relativePath().toString())
                            .thenComparing(QuarkusUnsupportedTest::annotationName))
                    .toList();
        } catch (UncheckedIOException exception) {
            throw scanException(testOutputDirectory, exception.getCause());
        } catch (IOException exception) {
            throw scanException(testOutputDirectory, exception);
        }
    }

    static QuarkusPlanException scanException(Path testOutputDirectory, IOException exception) {
        Path output = testOutputDirectory.toAbsolutePath().normalize();
        return new QuarkusPlanException(
                "Could not inspect compiled test classes for Quarkus test annotations. "
                        + "Clean "
                        + output
                        + ", run `zolt test` again, and check that the configured test output directory is readable.",
                exception);
    }

    private static List<QuarkusUnsupportedTest> unsupportedTests(Path testOutputDirectory, Path classFile) {
        String contents = classFileContents(classFile);
        Path normalizedClassFile = classFile.toAbsolutePath().normalize();
        Path relativeClassFile = testOutputDirectory.toAbsolutePath().normalize().relativize(normalizedClassFile);
        return QUARKUS_TEST_ANNOTATIONS.stream()
                .filter(annotation -> contents.contains(annotation.descriptor()))
                .map(annotation -> new QuarkusUnsupportedTest(
                        normalizedClassFile,
                        relativeClassFile,
                        annotation.name(),
                        annotation.annotationRunnerSupported()))
                .toList();
    }

    private static String classFileContents(Path classFile) {
        try {
            return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record QuarkusTestAnnotation(String name, String descriptor, boolean annotationRunnerSupported) {
    }
}
