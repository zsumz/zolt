package com.zolt.quarkus;

import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class QuarkusTestPlanService {
    private static final List<UnsupportedAnnotation> UNSUPPORTED_ANNOTATIONS = List.of(
            new UnsupportedAnnotation("@QuarkusTest", "Lio/quarkus/test/junit/QuarkusTest;"),
            new UnsupportedAnnotation("@QuarkusIntegrationTest", "Lio/quarkus/test/junit/QuarkusIntegrationTest;"),
            new UnsupportedAnnotation("@QuarkusMainTest", "Lio/quarkus/test/junit/QuarkusMainTest;"),
            new UnsupportedAnnotation("@QuarkusMainIntegrationTest", "Lio/quarkus/test/junit/QuarkusMainIntegrationTest;"));

    public QuarkusTestPlan plan(Path projectDirectory, ProjectConfig config) {
        if (projectDirectory == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a project directory.");
        }
        if (config == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a project config.");
        }
        if (!config.frameworkSettings().quarkus().enabled()) {
            throw new QuarkusPlanException(
                    "Quarkus is not enabled for this project. "
                            + "Add `[framework.quarkus] enabled = true` to zolt.toml, "
                            + "run `zolt resolve`, then run `zolt quarkus test-plan` again.");
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path testOutputDirectory = root.resolve(config.build().testOutput()).normalize();
        return new QuarkusTestPlan(
                root,
                testOutputDirectory,
                Files.isDirectory(testOutputDirectory),
                root.resolve("target/quarkus/test-application-model.dat").normalize(),
                unsupportedTests(testOutputDirectory));
    }

    private static List<QuarkusUnsupportedTest> unsupportedTests(Path testOutputDirectory) {
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
            throw new QuarkusPlanException(
                    "Could not inspect compiled test classes for Quarkus test annotations. "
                            + "Clean target/test-classes, run `zolt test` again, and check that target/ is readable.",
                    exception.getCause());
        } catch (IOException exception) {
            throw new QuarkusPlanException(
                    "Could not inspect compiled test classes for Quarkus test annotations. "
                            + "Clean target/test-classes, run `zolt test` again, and check that target/ is readable.",
                    exception);
        }
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
