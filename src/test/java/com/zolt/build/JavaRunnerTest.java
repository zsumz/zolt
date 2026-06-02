package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JavaRunnerTest {
    @Test
    void passesRuntimeClasspathMainClassAndArguments() {
        List<List<String>> commands = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", command -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "hello\n");
        });

        JavaRunResult result = runner.run(
                Path.of("java"),
                new Classpath(List.of(Path.of("target/classes"), Path.of("lib.jar"))),
                "com.example.Main",
                List.of("one", "two"));

        assertEquals("hello\n", result.output());
        assertEquals(List.of(
                "java",
                "-classpath",
                "target/classes:lib.jar",
                "com.example.Main",
                "one",
                "two"), commands.getFirst());
    }

    @Test
    void nonZeroExitIncludesApplicationOutput() {
        JavaRunner runner = new JavaRunner(":", command -> new JavaRunner.ProcessResult(7, "boom\n"));

        JavaRunException exception = assertThrows(
                JavaRunException.class,
                () -> runner.run(
                        Path.of("java"),
                        new Classpath(List.of(Path.of("target/classes"))),
                        "com.example.Main",
                        List.of()));

        assertTrue(exception.getMessage().contains("java exited with code 7"));
        assertTrue(exception.getMessage().contains("boom"));
    }
}
