package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.doctor.JdkStatus;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoverageServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void runsTestsWithJacocoAgentAndGeneratesXmlAndHtmlReports() throws IOException {
        writeCoverageLockfile();
        List<TestJvmArguments> testJvmArguments = new ArrayList<>();
        List<TestReportSettings> testReportSettings = new ArrayList<>();
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents) -> {
                    testJvmArguments.add(jvmArguments);
                    testReportSettings.add(reportSettings);
                    return new TestRunResult(null, "Tests passed\n", "junit-console", 3, 1, 1);
                },
                reportCommands);

        CoverageResult result = service.runCoverage(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                CoverageReportSettings.defaults(),
                List.of("failed"));

        Path execFile = projectDir.resolve("target/coverage/jacoco.exec").toAbsolutePath().normalize();
        assertEquals(execFile, result.execFile());
        assertEquals(Optional.of(projectDir.resolve("target/coverage/jacoco.xml").toAbsolutePath().normalize()), result.xmlReport());
        assertEquals(Optional.of(projectDir.resolve("target/coverage/html").toAbsolutePath().normalize()), result.htmlDirectory());
        assertEquals("Tests passed\n", result.testRunResult().output());
        assertTrue(testJvmArguments.getFirst().values().getFirst().startsWith("-javaagent:"));
        assertTrue(testJvmArguments.getFirst().values().getFirst().contains("org.jacoco.agent-0.8.14-runtime.jar"));
        assertTrue(testJvmArguments.getFirst().values().getFirst().contains("destfile=" + execFile));
        assertEquals(Optional.of(Path.of("target/coverage/test-reports")), testReportSettings.getFirst().reportsDirectory());

        List<String> command = reportCommands.getFirst();
        assertTrue(command.contains("org.jacoco.cli.internal.Main"));
        assertTrue(command.contains("report"));
        assertTrue(command.contains(execFile.toString()));
        assertEquals(projectDir.resolve("target/classes").toAbsolutePath().normalize().toString(), argumentAfter(command, "--classfiles"));
        assertEquals(projectDir.resolve("src/main/java").toAbsolutePath().normalize().toString(), argumentAfter(command, "--sourcefiles"));
        assertEquals(projectDir.resolve("target/coverage/jacoco.xml").toAbsolutePath().normalize().toString(), argumentAfter(command, "--xml"));
        assertEquals(projectDir.resolve("target/coverage/html").toAbsolutePath().normalize().toString(), argumentAfter(command, "--html"));
        assertTrue(command.stream().anyMatch(value -> value.contains("org.jacoco.cli-0.8.14.jar")));
    }

    @Test
    void canDisableHtmlReport() throws IOException {
        writeCoverageLockfile();
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents) ->
                        new TestRunResult(null, "Tests passed\n"),
                reportCommands);

        CoverageResult result = service.runCoverage(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                new CoverageReportSettings(
                        true,
                        false,
                        Path.of("target/coverage/jacoco.exec"),
                        Path.of("target/coverage/jacoco.xml"),
                        Path.of("target/coverage/html"),
                        TestReportSettings.reportsDirectory(Path.of("target/coverage/test-reports"))),
                List.of());

        assertTrue(result.htmlDirectory().isEmpty());
        assertTrue(reportCommands.getFirst().contains("--xml"));
        assertTrue(!reportCommands.getFirst().contains("--html"));
    }

    @Test
    void rejectsCoverageWhenAllReportsAreDisabled() {
        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> new CoverageReportSettings(
                        false,
                        false,
                        Path.of("target/coverage/jacoco.exec"),
                        Path.of("target/coverage/jacoco.xml"),
                        Path.of("target/coverage/html"),
                        TestReportSettings.reportsDirectory(Path.of("target/coverage/test-reports"))));

        assertTrue(exception.getMessage().contains("at least one report format"));
    }

    @Test
    void rejectsCoverageOutputSymlinkThatEscapesProject() throws IOException {
        Files.createDirectories(projectDir.resolve("target"));
        createSymlink(projectDir.resolve("target/coverage"), Files.createTempDirectory("zolt-coverage-"));

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> CoverageReportSettings.defaults().absoluteExecFile(projectDir));

        assertTrue(exception.getMessage().contains("coverage output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void missingCoverageToolingExplainsHowToRefreshLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents) ->
                        new TestRunResult(null, ""),
                new ArrayList<>());

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> service.runCoverage(
                        projectDir,
                        config(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        CoverageReportSettings.defaults(),
                        List.of()));

        assertTrue(exception.getMessage().contains("tool-coverage"));
        assertTrue(exception.getMessage().contains("zolt resolve"));
    }

    private CoverageService service(
            CoverageService.CoverageTestRunner testRunner,
            List<List<String>> reportCommands) {
        return new CoverageService(
                testRunner,
                new com.zolt.lockfile.ZoltLockfileReader(),
                requiredVersion -> new JdkStatus(
                        Optional.of(projectDir),
                        Optional.of(Path.of("/java")),
                        Optional.of(Path.of("/javac")),
                        Optional.of(Path.of("/jar")),
                        Optional.of(requiredVersion),
                        requiredVersion),
                new JavaRunner(":", new JavaRunner.ProcessRunner() {
                    @Override
                    public JavaRunner.ProcessResult run(
                            List<String> command,
                            java.util.function.Consumer<String> outputConsumer) {
                        reportCommands.add(command);
                        return new JavaRunner.ProcessResult(0, "Wrote coverage reports\n");
                    }

                    @Override
                    public JavaRunner.ProcessResult run(
                            List<String> command,
                            Map<String, String> environment,
                            java.util.function.Consumer<String> outputConsumer) {
                        reportCommands.add(command);
                        return new JavaRunner.ProcessResult(0, "Wrote coverage reports\n");
                    }
                }),
                (projectDirectory, config, cacheRoot) -> {
                });
    }

    private void writeCoverageLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.jacoco:org.jacoco.agent"
                version = "0.8.14"
                source = "maven-central"
                scope = "tool-coverage"
                direct = false
                jar = "org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar"
                dependencies = []

                [[package]]
                id = "org.jacoco:org.jacoco.cli"
                version = "0.8.14"
                source = "maven-central"
                scope = "tool-coverage"
                direct = false
                jar = "org/jacoco/org.jacoco.cli/0.8.14/org.jacoco.cli-0.8.14.jar"
                dependencies = []
                """);
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.jupiter:junit-jupiter", "5.11.4"),
                BuildSettings.defaults());
    }

    private static String argumentAfter(List<String> command, String argument) {
        int index = command.indexOf(argument);
        if (index < 0 || index + 1 >= command.size()) {
            throw new AssertionError("Missing argument " + argument + " in " + command);
        }
        return command.get(index + 1);
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
