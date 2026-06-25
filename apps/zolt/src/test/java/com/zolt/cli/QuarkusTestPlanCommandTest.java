package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestPlanCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void quarkusTestPlanReportsPlainJUnitStatus() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, true);
        writeCompiledTest(projectDir, "com/example/PlainTest.class", "constant-pool:plain-junit");

        CommandResult result = testPlan(projectDir, "--directory");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Quarkus test plan"));
        assertTrue(result.stdout().contains("Status: plain JUnit tests can run through the current Zolt test runner"));
        assertTrue(result.stdout().contains("Compiled test output: present"));
        assertTrue(result.stdout().contains("Quarkus annotation runner tests: 0"));
        assertTrue(result.stdout().contains("Unsupported Quarkus tests: 0"));
    }

    @Test
    void quarkusTestPlanReportsSupportedQuarkusTests() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, true);
        writeCompiledTest(projectDir, "com/example/HttpTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        CommandResult result = testPlan(projectDir, "--cwd");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Status: Quarkus annotation runner selected"));
        assertTrue(result.stdout().contains("Quarkus annotation runner tests: 1"));
        assertTrue(result.stdout().contains("Unsupported Quarkus tests: 0"));
        assertTrue(result.stdout().contains("com/example/HttpTest.class (@QuarkusTest)"));
    }

    @Test
    void quarkusTestPlanReportsUnsupportedQuarkusTestModes() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, true);
        writeCompiledTest(projectDir, "com/example/NativeHttpIT.class", "constant-pool:Lio/quarkus/test/junit/QuarkusIntegrationTest;");

        CommandResult result = testPlan(projectDir, "--cwd");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Status: blocked by unsupported Quarkus test annotations"));
        assertTrue(result.stdout().contains("Quarkus annotation runner tests: 0"));
        assertTrue(result.stdout().contains("Unsupported Quarkus tests: 1"));
        assertTrue(result.stdout().contains("com/example/NativeHttpIT.class (@QuarkusIntegrationTest)"));
    }

    @Test
    void quarkusTestPlanFailsWhenFrameworkIsNotEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, false);

        CommandResult result = testPlan(projectDir, "--cwd");

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("Quarkus is not enabled for this project"));
        assertTrue(result.stderr().contains("[framework.quarkus] enabled = true"));
    }

    private static CommandResult testPlan(Path projectDir, String directoryOption) {
        return execute("quarkus", "test-plan", directoryOption, projectDir.toString());
    }

    private static void writeCompiledTest(Path projectDir, String relativePath, String content) throws IOException {
        Path testClass = projectDir.resolve("target/test-classes").resolve(relativePath);
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, content);
    }

    private static void writeProjectConfig(Path projectDir, boolean quarkus) throws IOException {
        Files.createDirectories(projectDir);
        String framework = quarkus
                ? "\n[framework.quarkus]\nenabled = true\npackage = \"fast-jar\"\n"
                : "";
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo")
                + "main = \"com.example.Main\"\n\n[repositories]\ntest = \"https://repo.maven.apache.org/maven2\"\n\n"
                + "[dependencies]\n\n[test.dependencies]\n\n[build]\nsource = \"src/main/java\"\ntest = \"src/test/java\"\n"
                + "output = \"target/classes\"\ntestOutput = \"target/test-classes\"\n"
                + framework);
    }
}
