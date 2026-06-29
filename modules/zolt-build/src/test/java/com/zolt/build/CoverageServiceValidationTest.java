package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
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

    private static CoverageService service(
            CoverageService.CoverageTestRunner testRunner,
            List<List<String>> reportCommands) {
        return new CoverageService(
                testRunner,
                new com.zolt.lockfile.ZoltLockfileReader(),
                requiredVersion -> new com.zolt.doctor.JdkStatus(
                        Optional.of(Path.of(".")),
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
