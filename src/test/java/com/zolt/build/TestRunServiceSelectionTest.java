package com.zolt.build;

import static com.zolt.build.TestRunServiceTestSupport.commandArgumentAfter;
import static com.zolt.build.TestRunServiceTestSupport.config;
import static com.zolt.build.TestRunServiceTestSupport.service;
import static com.zolt.build.TestRunServiceTestSupport.source;
import static com.zolt.build.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

        service.runTests(projectDir, config(), projectDir.resolve("cache"), selection);

        List<String> command = commands.getFirst();
        assertTrue(command.contains("--select-method"));
        assertEquals("com.example.MainTest#runs", commandArgumentAfter(command, "--select-method"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
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

        service.runTests(projectDir, config(), projectDir.resolve("cache"), selection);

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
}
