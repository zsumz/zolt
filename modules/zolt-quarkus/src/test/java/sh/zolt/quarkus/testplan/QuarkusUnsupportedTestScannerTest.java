package sh.zolt.quarkus.testplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusPlanException;
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
        writeClass("com/example/GammaTest.class", "constant-pool:Lio/quarkus/test/junit/main/QuarkusMainTest;");
        writeClass("com/example/OmegaTest.class", "constant-pool:Lio/quarkus/test/junit/main/QuarkusMainIntegrationTest;");
        writeClass("com/example/AlphaTest.class", """
                constant-pool:Lio/quarkus/test/junit/QuarkusTest;
                constant-pool:Lio/quarkus/test/common/QuarkusTestResource;
                constant-pool:Lio/quarkus/test/junit/TestProfile;
                """);

        List<QuarkusUnsupportedTest> unsupportedTests =
                new QuarkusUnsupportedTestScanner().scan(projectDir.resolve("target/test-classes"));

        assertEquals(6, unsupportedTests.size());
        assertEquals(Path.of("com/example/AlphaTest.class"), unsupportedTests.get(0).relativePath());
        assertEquals("@QuarkusTest", unsupportedTests.get(0).annotationName());
        assertTrue(unsupportedTests.get(0).annotationRunnerSupported());
        assertEquals(Path.of("com/example/AlphaTest.class"), unsupportedTests.get(1).relativePath());
        assertEquals("@QuarkusTestResource", unsupportedTests.get(1).annotationName());
        assertTrue(unsupportedTests.get(1).blocksAnnotationRunner());
        assertEquals(Path.of("com/example/AlphaTest.class"), unsupportedTests.get(2).relativePath());
        assertEquals("@TestProfile", unsupportedTests.get(2).annotationName());
        assertTrue(unsupportedTests.get(2).blocksAnnotationRunner());
        assertEquals(Path.of("com/example/BetaTest.class"), unsupportedTests.get(3).relativePath());
        assertEquals("@QuarkusIntegrationTest", unsupportedTests.get(3).annotationName());
        assertTrue(unsupportedTests.get(3).blocksAnnotationRunner());
        assertEquals(Path.of("com/example/GammaTest.class"), unsupportedTests.get(4).relativePath());
        assertEquals("@QuarkusMainTest", unsupportedTests.get(4).annotationName());
        assertTrue(unsupportedTests.get(4).blocksAnnotationRunner());
        assertEquals(Path.of("com/example/OmegaTest.class"), unsupportedTests.get(5).relativePath());
        assertEquals("@QuarkusMainIntegrationTest", unsupportedTests.get(5).annotationName());
        assertTrue(unsupportedTests.get(5).blocksAnnotationRunner());
    }

    @Test
    void keepsDirectQuarkusTestSupportedWhenNoBlockedModesArePresent() throws IOException {
        writeClass("com/example/HttpTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        List<QuarkusUnsupportedTest> unsupportedTests =
                new QuarkusUnsupportedTestScanner().scan(projectDir.resolve("target/test-classes"));

        assertEquals(1, unsupportedTests.size());
        assertEquals(Path.of("com/example/HttpTest.class"), unsupportedTests.getFirst().relativePath());
        assertEquals("@QuarkusTest", unsupportedTests.getFirst().annotationName());
        assertTrue(unsupportedTests.getFirst().annotationRunnerSupported());
    }

    @Test
    void detectsUnsupportedQuarkusTestResourceAnnotationFamily() throws IOException {
        writeClass("com/example/ResourceFamilyTest.class", """
                constant-pool:Lio/quarkus/test/common/QuarkusTestResource$List;
                constant-pool:Lio/quarkus/test/common/WithTestResource;
                constant-pool:Lio/quarkus/test/common/WithTestResource$List;
                """);

        List<QuarkusUnsupportedTest> unsupportedTests =
                new QuarkusUnsupportedTestScanner().scan(projectDir.resolve("target/test-classes"));

        assertEquals(3, unsupportedTests.size());
        assertEquals("@QuarkusTestResource.List", unsupportedTests.get(0).annotationName());
        assertTrue(unsupportedTests.get(0).blocksAnnotationRunner());
        assertEquals("@WithTestResource", unsupportedTests.get(1).annotationName());
        assertTrue(unsupportedTests.get(1).blocksAnnotationRunner());
        assertEquals("@WithTestResource.List", unsupportedTests.get(2).annotationName());
        assertTrue(unsupportedTests.get(2).blocksAnnotationRunner());
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
