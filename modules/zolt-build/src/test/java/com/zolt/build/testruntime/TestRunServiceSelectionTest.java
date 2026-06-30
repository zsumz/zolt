package com.zolt.build.testruntime;

import static com.zolt.build.testruntime.TestRunServiceTestSupport.commandArgumentAfter;
import static com.zolt.build.testruntime.TestRunServiceTestSupport.config;
import static com.zolt.build.testruntime.TestRunServiceTestSupport.service;
import static com.zolt.build.testruntime.TestRunServiceTestSupport.source;
import static com.zolt.build.testruntime.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.run.JavaRunner;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestSuiteSettings;
import com.zolt.test.TestPlanException;
import com.zolt.test.runtime.TestJvmArguments;
import com.zolt.test.runtime.TestRunException;
import com.zolt.test.shard.TestShardSpec;
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

final class TestRunServiceSelectionTest {
    @TempDir
    private Path projectDir;

    @Test
    void appliesClassSelectionToJUnitConsoleWithoutClasspathScan() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });
        TestSelection selection = TestSelection.fromCli(List.of("com.example.MainTest"), List.of(), List.of(), List.of());

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"), selection);

        assertEquals(selection, result.testSelection());
        List<String> command = commands.getFirst();
        assertTrue(command.contains("--select-class"));
        assertEquals("com.example.MainTest", commandArgumentAfter(command, "--select-class"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
        assertFalse(command.contains("--select-method"));
        assertFalse(command.contains("--include-classname"));
    }

    @Test
    void appliesMethodSelectionToJUnitConsoleWithoutClasspathScan() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.MainTest#runs"),
                List.of(),
                List.of(),
                List.of());

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"), selection);

        List<String> command = commands.getFirst();
        assertTrue(command.contains("--select-method"));
        assertEquals("com.example.MainTest#runs", commandArgumentAfter(command, "--select-method"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
        assertTrue(result.testRunnerRequestNanos() >= 0L);
    }

    @Test
    void appliesPatternAndTagSelectionToJUnitConsoleScan() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });
        TestSelection selection = TestSelection.fromCli(
                List.of(),
                List.of("*MainTest", "com.example.?therTest"),
                List.of("fast"),
                List.of("slow"));

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"), selection);

        List<String> command = commands.getFirst();
        assertTrue(command.stream().anyMatch(argument -> argument.equals("--scan-class-path="
                + projectDir.resolve("target/test-classes").toAbsolutePath().normalize())));
        assertTrue(command.contains("--include-classname"));
        assertTrue(command.contains(".*MainTest"));
        assertTrue(command.contains("com\\.example\\..therTest"));
        assertFalse(command.contains(".*Spec"));
        assertEquals("fast", commandArgumentAfter(command, "--include-tag"));
        assertEquals("slow", commandArgumentAfter(command, "--exclude-tag"));
        assertFalse(command.contains("--select-method"));
        assertTrue(result.testRunnerRequestNanos() >= 0L);
    }

    @Test
    void explicitSelectionNoMatchProducesActionableTestRunError() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) ->
                new JavaRunner.ProcessResult(2, "No tests found for request\n"));
        TestSelection selection = TestSelection.fromCli(List.of(), List.of("*MissingTest"), List.of(), List.of());

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache"), selection));

        assertTrue(exception.getMessage().contains("Selected tests did not match any tests"));
        assertTrue(exception.getMessage().contains("Check --test, --tests, --include-tag, and --exclude-tag"));
        assertTrue(exception.getMessage().contains("No tests found"));
    }

    @Test
    void explicitSelectionZeroTestsWithSuccessfulConsoleExitStillFails() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) ->
                new JavaRunner.ProcessResult(0, """
                        Test run finished after 13 ms
                        [         0 tests found           ]
                        [         0 tests started         ]
                        [         0 tests successful      ]
                        """));
        TestSelection selection = TestSelection.fromCli(List.of(), List.of("*MissingTest"), List.of(), List.of());

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache"), selection));

        assertTrue(exception.getMessage().contains("Selected tests did not match any tests"));
        assertTrue(exception.getMessage().contains("0 tests found"));
    }

    @Test
    void suiteSelectionRunsOnlyMatchingCompiledClasses() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/FastServiceTest.java", "package com.example; public final class FastServiceTest {}\n");
        source(projectDir, "src/test/java/com/example/SlowServiceTest.java", "package com.example; public final class SlowServiceTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(
                projectDir,
                configWithSuite("fast", new TestSuiteSettings(List.of("*Fast*"), List.of(), List.of("fast"), List.of())),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.reportsDirectory(Path.of("target/test-reports")),
                List.of(),
                "fast");

        assertEquals(List.of("com.example.FastServiceTest"), result.testSelection().classSelectors());
        assertEquals(List.of("fast"), result.testSelection().includedTags());
        List<String> command = commands.getFirst();
        assertTrue(command.contains("--select-class"));
        assertEquals("com.example.FastServiceTest", commandArgumentAfter(command, "--select-class"));
        assertFalse(command.contains("com.example.SlowServiceTest"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
        assertEquals("fast", commandArgumentAfter(command, "--include-tag"));
        assertEquals(
                projectDir.resolve("target/test-reports").toAbsolutePath().normalize().toString(),
                commandArgumentAfter(command, "--reports-dir"));
    }

    @Test
    void suiteSelectionCombinesWithCliPatternBeforeRunnerLaunch() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/FastApiTest.java", "package com.example; public final class FastApiTest {}\n");
        source(projectDir, "src/test/java/com/example/FastRepositoryTest.java", "package com.example; public final class FastRepositoryTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });
        TestSelection selection = TestSelection.fromCli(List.of(), List.of("*ApiTest"), List.of(), List.of("slow"));

        TestRunResult result = service.runTests(
                projectDir,
                configWithSuite("fast", new TestSuiteSettings(List.of("*Fast*"), List.of(), List.of(), List.of())),
                projectDir.resolve("cache"),
                selection,
                TestJvmArguments.empty(),
                TestReportSettings.disabled(),
                List.of(),
                "fast");

        assertEquals(List.of("com.example.FastApiTest"), result.testSelection().classSelectors());
        assertEquals(List.of(), result.testSelection().classNamePatterns());
        assertEquals(List.of("slow"), result.testSelection().excludedTags());
        List<String> command = commands.getFirst();
        assertEquals("com.example.FastApiTest", commandArgumentAfter(command, "--select-class"));
        assertFalse(command.contains("com.example.FastRepositoryTest"));
        assertEquals("slow", commandArgumentAfter(command, "--exclude-tag"));
    }

    @Test
    void shardSelectionRunsOnlyShardClassesAndWritesManifest() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/AlphaTest.java", "package com.example; public final class AlphaTest {}\n");
        source(projectDir, "src/test/java/com/example/BetaTest.java", "package com.example; public final class BetaTest {}\n");
        source(projectDir, "src/test/java/com/example/GammaTest.java", "package com.example; public final class GammaTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(
                projectDir,
                configWithSuite("fast", new TestSuiteSettings(List.of("*Test"), List.of(), List.of(), List.of())),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.reportsDirectory(Path.of("target/test-reports")),
                List.of(),
                "fast",
                new TestShardSpec(2, 2));

        assertEquals(List.of("com.example.BetaTest"), result.testSelection().classSelectors());
        assertEquals(
                Optional.of(projectDir.resolve("target/test-reports/shards/fast/shard-2-of-2").toAbsolutePath().normalize()),
                result.reportsDirectory());
        List<String> command = commands.getFirst();
        assertEquals("com.example.BetaTest", commandArgumentAfter(command, "--select-class"));
        assertEquals(
                projectDir.resolve("target/test-reports/shards/fast/shard-2-of-2").toAbsolutePath().normalize().toString(),
                commandArgumentAfter(command, "--reports-dir"));
        assertFalse(command.contains("com.example.AlphaTest"));
        assertFalse(command.contains("com.example.GammaTest"));
        String manifest = Files.readString(projectDir.resolve("target/test-shards/fast/shard-2-of-2.json"));
        assertTrue(manifest.contains("\"selectedEntries\": 1"));
        assertTrue(manifest.contains("\"com.example.BetaTest\""));
    }

    @Test
    void shardSelectionSanitizesSuiteNameInEvidencePaths() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/AlphaTest.java", "package com.example; public final class AlphaTest {}\n");
        source(projectDir, "src/test/java/com/example/BetaTest.java", "package com.example; public final class BetaTest {}\n");
        TestRunService service = service((command, outputConsumer) -> new JavaRunner.ProcessResult(0, "Tests successful\n"));

        TestRunResult result = service.runTests(
                projectDir,
                configWithSuite("fast suite!", new TestSuiteSettings(List.of("*Test"), List.of(), List.of(), List.of())),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.reportsDirectory(Path.of("target/test-reports")),
                List.of(),
                "fast suite!",
                new TestShardSpec(1, 2));

        assertEquals(
                Optional.of(projectDir.resolve("target/test-reports/shards/fast_suite_/shard-1-of-2").toAbsolutePath().normalize()),
                result.reportsDirectory());
        assertTrue(Files.exists(projectDir.resolve("target/test-shards/fast_suite_/shard-1-of-2.json")));
    }

    @Test
    void emptyShardWritesManifestAndFailsBeforeLaunchingRunner() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/AlphaTest.java", "package com.example; public final class AlphaTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> service.runTests(
                        projectDir,
                        configWithSuite("fast", new TestSuiteSettings(List.of("*Test"), List.of(), List.of(), List.of())),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "fast",
                        new TestShardSpec(2, 2)));

        assertTrue(exception.getMessage().contains("Test shard `2/2` for suite `fast` did not match any compiled test classes"));
        assertTrue(exception.getMessage().contains("target/test-shards/fast/shard-2-of-2.json"));
        assertTrue(commands.isEmpty());
        String manifest = Files.readString(projectDir.resolve("target/test-shards/fast/shard-2-of-2.json"));
        assertTrue(manifest.contains("\"selectedEntries\": 0"));
        assertTrue(manifest.contains("\"empty\": true"));
    }

    @Test
    void unknownSuiteFailsBeforeLaunchingRunner() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/FastServiceTest.java", "package com.example; public final class FastServiceTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> service.runTests(
                        projectDir,
                        config(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "missing"));

        assertTrue(exception.getMessage().contains("Unknown test suite `missing`"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void emptySuiteFailsBeforeLaunchingRunner() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/FastServiceTest.java", "package com.example; public final class FastServiceTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> service.runTests(
                        projectDir,
                        configWithSuite("empty", new TestSuiteSettings(List.of("*MissingTest"), List.of(), List.of(), List.of())),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "empty"));

        assertTrue(exception.getMessage().contains("Test suite `empty` did not match any compiled test classes"));
        assertTrue(commands.isEmpty());
    }

    private static ProjectConfig configWithSuite(String name, TestSuiteSettings settings) {
        return config().withBuildSettings(config().build().withTestSuites(Map.of(name, settings)));
    }
}
