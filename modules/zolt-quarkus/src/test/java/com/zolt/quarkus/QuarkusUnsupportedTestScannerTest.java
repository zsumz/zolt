package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusUnsupportedTestScannerTest {
    @TempDir
    private Path projectDir;

    @Test
    void returnsEmptyWhenTestOutputIsMissing() {
        assertTrue(new QuarkusUnsupportedTestScanner()
                .scan(projectDir.resolve("target/test-classes"))
                .isEmpty());
    }

    @Test
    void detectsUnsupportedQuarkusTestAnnotationsDeterministically() throws IOException {
        writeClass("com/example/BetaTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusIntegrationTest;");
        writeClass("com/example/AlphaTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        List<QuarkusUnsupportedTest> unsupportedTests =
                new QuarkusUnsupportedTestScanner().scan(projectDir.resolve("target/test-classes"));

        assertEquals(2, unsupportedTests.size());
        assertEquals(Path.of("com/example/AlphaTest.class"), unsupportedTests.get(0).relativePath());
        assertEquals("@QuarkusTest", unsupportedTests.get(0).annotationName());
        assertEquals(Path.of("com/example/BetaTest.class"), unsupportedTests.get(1).relativePath());
        assertEquals("@QuarkusIntegrationTest", unsupportedTests.get(1).annotationName());
    }

    @Test
    void rejectsMissingScanDirectoryArgument() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusUnsupportedTestScanner().scan(null));

        assertTrue(exception.getMessage().contains("test output directory"));
    }

    @Test
    void scanFailureMessageUsesConfiguredTestOutputDirectory() {
        Path testOutput = projectDir.resolve(".zolt/build/test-classes");

        QuarkusPlanException exception = QuarkusUnsupportedTestScanner.scanException(
                testOutput,
                new IOException("denied"));

        assertTrue(exception.getMessage().contains(testOutput.toAbsolutePath().normalize().toString()));
        assertTrue(exception.getMessage().contains("configured test output directory"));
    }

    private void writeClass(String relativePath, String content) throws IOException {
        Path classFile = projectDir.resolve("target/test-classes").resolve(relativePath);
        Files.createDirectories(classFile.getParent());
        Files.writeString(classFile, content);
    }
}
