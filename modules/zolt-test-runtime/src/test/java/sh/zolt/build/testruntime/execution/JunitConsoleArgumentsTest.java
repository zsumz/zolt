package sh.zolt.build.testruntime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JunitConsoleArgumentsTest {
    @TempDir
    private Path tempDir;

    @Test
    void defaultSelectionScansTestOutputWithSummaryDetails() {
        List<String> arguments = new JunitConsoleArguments("::").arguments(
                List.of(Path.of("target/test-classes"), Path.of("target/classes")),
                tempDir.resolve("target/test-classes"),
                TestSelection.empty(),
                Optional.empty(),
                List.of());

        assertEquals("execute", arguments.getFirst());
        assertTrue(arguments.contains("--disable-banner"));
        assertEquals("target/test-classes::target/classes", valueAfter(arguments, "--class-path"));
        assertTrue(arguments.contains("--scan-class-path=" + tempDir.resolve("target/test-classes").toAbsolutePath().normalize()));
        assertEquals("summary", valueAfter(arguments, "--details"));
        assertTrue(arguments.contains("--include-classname"));
        assertTrue(arguments.contains("^(Test.*|.+[.$]Test.*|.*Tests?)$"));
        assertTrue(arguments.contains(".*Spec"));
    }

    @Test
    void explicitSelectionAvoidsClasspathScanAndAddsReportsAndEvents() {
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.MainTest#runs"),
                List.of(".*IT"),
                List.of("fast"),
                List.of("slow"));

        List<String> arguments = new JunitConsoleArguments(":").arguments(
                List.of(Path.of("tests")),
                tempDir.resolve("tests"),
                selection,
                Optional.of(tempDir.resolve("reports")),
                List.of("started"));

        assertFalse(arguments.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
        assertEquals("com.example.MainTest#runs", valueAfter(arguments, "--select-method"));
        assertEquals("\\..*IT", valueAfter(arguments, "--include-classname"));
        assertEquals("fast", valueAfter(arguments, "--include-tag"));
        assertEquals("slow", valueAfter(arguments, "--exclude-tag"));
        assertEquals(tempDir.resolve("reports").toString(), valueAfter(arguments, "--reports-dir"));
        assertEquals("tree", valueAfter(arguments, "--details"));
        assertEquals("ascii", valueAfter(arguments, "--details-theme"));
    }

    private static String valueAfter(List<String> arguments, String key) {
        return arguments.get(arguments.indexOf(key) + 1);
    }
}
