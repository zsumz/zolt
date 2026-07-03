package sh.zolt.build.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.build.CoverageException;
import sh.zolt.build.run.JavaRunResult;
import sh.zolt.build.run.JavaRunner;
import sh.zolt.build.testruntime.TestReportSettings;
import sh.zolt.build.testruntime.TestRunResult;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoverageServiceValidationTest {
    @TempDir
    private Path projectDir;

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
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
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

    @Test
    void nonRuntimeJacocoAgentExplainsHowToRefreshLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.jacoco:org.jacoco.agent"
                version = "0.8.14"
                source = "maven-central"
                scope = "tool-coverage"
                direct = false
                jar = "org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14.jar"
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
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
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

        assertTrue(exception.getMessage().contains("org.jacoco:org.jacoco.agent:runtime"));
        assertTrue(exception.getMessage().contains("zolt resolve"));
    }

    @Test
    void missingCoverageCliExplainsHowToRefreshLockfile() throws IOException {
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
                """);
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
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

        assertTrue(exception.getMessage().contains("org.jacoco:org.jacoco.cli"));
        assertTrue(exception.getMessage().contains("tool-coverage"));
        assertTrue(exception.getMessage().contains("zolt resolve"));
    }

    @Test
    void rejectsCoverageOutputThatEscapesProject() {
        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> new CoverageReportSettings(
                        true,
                        true,
                        Path.of("../coverage/jacoco.exec"),
                        Path.of("target/coverage/jacoco.xml"),
                        Path.of("target/coverage/html"),
                        TestReportSettings.reportsDirectory(Path.of("target/coverage/test-reports"))));

        assertTrue(exception.getMessage().contains("coverage exec file"));
        assertTrue(exception.getMessage().contains("must stay inside the project"));
        assertTrue(exception.getMessage().contains("target/coverage/jacoco.exec"));
    }

    @Test
    void rejectsAbsoluteCoverageXmlReportWithRemediation() {
        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> new CoverageReportSettings(
                        true,
                        true,
                        Path.of("target/coverage/jacoco.exec"),
                        projectDir.resolve("target/coverage/jacoco.xml"),
                        Path.of("target/coverage/html"),
                        TestReportSettings.reportsDirectory(Path.of("target/coverage/test-reports"))));

        assertTrue(exception.getMessage().contains("coverage XML report"));
        assertTrue(exception.getMessage().contains("must be project-relative"));
        assertTrue(exception.getMessage().contains("target/coverage/jacoco.xml"));
    }

    @Test
    void mergeExecFilesSortsInputsForDeterministicCommand() {
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                reportCommands);

        Path workerB = projectDir.resolve("target/coverage/workers/wave-2-worker-1/jacoco.exec");
        Path workerA = projectDir.resolve("target/coverage/workers/wave-1-worker-1/jacoco.exec");
        service.mergeExecFiles(
                projectDir,
                config(),
                projectDir.resolve("target/coverage/jacoco.exec"),
                List.of(workerB, workerA),
                List.of(Path.of("/tools/org.jacoco.cli.jar")));

        List<String> command = reportCommands.getFirst();
        int workerAIndex = command.indexOf(workerA.toAbsolutePath().normalize().toString());
        int workerBIndex = command.indexOf(workerB.toAbsolutePath().normalize().toString());
        assertTrue(workerAIndex >= 0);
        assertTrue(workerBIndex >= 0);
        assertTrue(workerAIndex < workerBIndex);
    }

    @Test
    void mergeExecFilesRejectsEmptyInputWithActionableMessage() {
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                new ArrayList<>());

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> service.mergeExecFiles(
                        projectDir,
                        config(),
                        projectDir.resolve("target/coverage/jacoco.exec"),
                        List.of(),
                        List.of(Path.of("/tools/org.jacoco.cli.jar"))));

        assertTrue(exception.getMessage().contains("Coverage merge requires at least one Jacoco execution data file"));
    }

    @Test
    void mergeExecFilesCopiesSingleInputWithoutLaunchingJacocoCli() throws IOException {
        Path sourceExec = projectDir.resolve("target/coverage/workers/wave-1-worker-1/jacoco.exec");
        Files.createDirectories(sourceExec.getParent());
        Files.writeString(sourceExec, "single worker coverage\n");
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                reportCommands);

        JavaRunResult result = service.mergeExecFiles(
                projectDir,
                config(),
                projectDir.resolve("target/coverage/jacoco.exec"),
                List.of(sourceExec),
                List.of(Path.of("/tools/org.jacoco.cli.jar")));

        assertEquals("Copied split coverage execution data\n", result.output());
        assertEquals("single worker coverage\n", Files.readString(projectDir.resolve("target/coverage/jacoco.exec")));
        assertTrue(reportCommands.isEmpty());
    }

    @Test
    void runReportRejectsBadJdkBeforeLaunchingJacocoCli() {
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                reportCommands,
                badJdkStatus(),
                successfulRunner(reportCommands));

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> service.runReport(
                        projectDir,
                        config(),
                        CoverageReportSettings.defaults(),
                        projectDir.resolve("target/coverage/jacoco.exec"),
                        List.of(Path.of("/tools/org.jacoco.cli.jar"))));

        assertTrue(exception.getMessage().contains("JDK check failed"));
        assertTrue(exception.getMessage().contains("Missing `java`"));
        assertTrue(exception.getMessage().contains("Install a JDK"));
        assertTrue(reportCommands.isEmpty());
    }

    @Test
    void runReportWrapsJacocoFailureWithRetryGuidance() {
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                reportCommands,
                okJdkStatus(),
                failingRunner(reportCommands, "Jacoco could not read classes\n"));

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> service.runReport(
                        projectDir,
                        config(),
                        CoverageReportSettings.defaults(),
                        projectDir.resolve("target/coverage/jacoco.exec"),
                        List.of(Path.of("/tools/org.jacoco.cli.jar"))));

        assertTrue(exception.getMessage().contains("Coverage report generation failed"));
        assertTrue(exception.getMessage().contains("test classes"));
        assertTrue(exception.getMessage().contains("zolt coverage"));
        assertTrue(exception.getMessage().contains("java exited with code 2"));
        assertTrue(exception.getMessage().contains("Jacoco could not read classes"));
        assertTrue(reportCommands.getFirst().contains("report"));
    }

    @Test
    void mergeExecFilesWrapsJacocoFailureWithRetryGuidance() {
        List<List<String>> reportCommands = new ArrayList<>();
        CoverageService service = service(
                (projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, shard) ->
                        new TestRunResult(null, ""),
                reportCommands,
                okJdkStatus(),
                failingRunner(reportCommands, "Jacoco merge failed\n"));

        CoverageException exception = assertThrows(
                CoverageException.class,
                () -> service.mergeExecFiles(
                        projectDir,
                        config(),
                        projectDir.resolve("target/coverage/jacoco.exec"),
                        List.of(
                                projectDir.resolve("target/coverage/workers/wave-2-worker-1/jacoco.exec"),
                                projectDir.resolve("target/coverage/workers/wave-1-worker-1/jacoco.exec")),
                        List.of(Path.of("/tools/org.jacoco.cli.jar"))));

        assertTrue(exception.getMessage().contains("Coverage merge failed"));
        assertTrue(exception.getMessage().contains("split Jacoco execution data"));
        assertTrue(exception.getMessage().contains("zolt coverage"));
        assertTrue(exception.getMessage().contains("java exited with code 2"));
        assertTrue(exception.getMessage().contains("Jacoco merge failed"));
        assertTrue(reportCommands.getFirst().contains("merge"));
    }

    private static CoverageService service(
            CoverageTestRunner testRunner,
            List<List<String>> reportCommands) {
        return service(testRunner, reportCommands, okJdkStatus(), successfulRunner(reportCommands));
    }

    private static CoverageService service(
            CoverageTestRunner testRunner,
            List<List<String>> reportCommands,
            JdkStatus jdkStatus,
            JavaRunner.ProcessRunner processRunner) {
        return new CoverageService(
                testRunner,
                new sh.zolt.lockfile.toml.ZoltLockfileReader(),
                requiredVersion -> jdkStatus,
                new JavaRunner(":", processRunner),
                (projectDirectory, config, cacheRoot) -> {
                });
    }

    private static JavaRunner.ProcessRunner successfulRunner(List<List<String>> reportCommands) {
        return runner(reportCommands, 0, "Wrote coverage reports\n");
    }

    private static JavaRunner.ProcessRunner failingRunner(List<List<String>> reportCommands, String output) {
        return runner(reportCommands, 2, output);
    }

    private static JavaRunner.ProcessRunner runner(
            List<List<String>> reportCommands,
            int exitCode,
            String output) {
        return new JavaRunner.ProcessRunner() {
            @Override
            public JavaRunner.ProcessResult run(
                    List<String> command,
                    java.util.function.Consumer<String> outputConsumer) {
                reportCommands.add(command);
                return new JavaRunner.ProcessResult(exitCode, output);
            }

            @Override
            public JavaRunner.ProcessResult run(
                    List<String> command,
                    Map<String, String> environment,
                    java.util.function.Consumer<String> outputConsumer) {
                reportCommands.add(command);
                return new JavaRunner.ProcessResult(exitCode, output);
            }
        };
    }

    private static JdkStatus okJdkStatus() {
        return new JdkStatus(
                Optional.of(Path.of(".")),
                Optional.of(Path.of("/java")),
                Optional.of(Path.of("/javac")),
                Optional.of(Path.of("/jar")),
                Optional.of("21"),
                "21");
    }

    private static JdkStatus badJdkStatus() {
        return new JdkStatus(
                Optional.empty(),
                Optional.empty(),
                Optional.of(Path.of("/javac")),
                Optional.of(Path.of("/jar")),
                Optional.of("21"),
                "21");
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.jupiter:junit-jupiter", "5.11.4"),
                BuildSettings.defaults());
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
