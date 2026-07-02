package sh.zolt.test.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TestJvmArgumentsTest {
    @Test
    void preservesDeterministicJvmArgumentOrder() {
        TestJvmArguments arguments = TestJvmArguments.fromCli(List.of(
                "-Dlibrary.mode=true",
                "--add-opens=java.base/java.lang=ALL-UNNAMED"));

        assertEquals(
                List.of("-Dlibrary.mode=true", "--add-opens=java.base/java.lang=ALL-UNNAMED"),
                arguments.values());
    }

    @Test
    void rejectsClasspathOwnershipFlags() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> TestJvmArguments.fromCli(List.of("-classpath")));

        assertTrue(exception.getMessage().contains("Zolt owns the test classpath"));
    }

    @Test
    void rejectsBlankArguments() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> TestJvmArguments.fromCli(List.of(" ")));

        assertTrue(exception.getMessage().contains("--jvm-arg requires a non-empty value"));
    }
}
