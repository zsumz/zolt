package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JavaRunnerTest {
    @Test
    void passesRuntimeClasspathMainClassAndArguments() {
        List<List<String>> commands = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", (command, outputConsumer) -> {
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
    void placesJvmArgumentsBeforeClasspathAndMainClass() {
        List<List<String>> commands = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", (command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "hello\n");
        });

        runner.run(
                Path.of("java"),
                new Classpath(List.of(Path.of("target/classes"))),
                "com.example.Main",
                List.of("-Ddemo=true"),
                List.of("run"));

        assertEquals(List.of(
                "java",
                "-Ddemo=true",
                "-classpath",
                "target/classes",
                "com.example.Main",
                "run"), commands.getFirst());
    }

    @Test
    void passesEnvironmentToProcessRunner() {
        List<Map<String, String>> environments = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", new JavaRunner.ProcessRunner() {
            @Override
            public JavaRunner.ProcessResult run(List<String> command, java.util.function.Consumer<String> outputConsumer) {
                throw new AssertionError("Environment-aware runner should be used.");
            }

            @Override
            public JavaRunner.ProcessResult run(
                    List<String> command,
                    Map<String, String> environment,
                    java.util.function.Consumer<String> outputConsumer) {
                environments.add(environment);
                return new JavaRunner.ProcessResult(0, "hello\n");
            }
        });

        runner.run(
                Path.of("java"),
                new Classpath(List.of(Path.of("target/classes"))),
                "com.example.Main",
                List.of("-Ddemo=true"),
                List.of(),
                Map.of("TZ", "America/Chicago"));

        assertEquals(Map.of("TZ", "America/Chicago"), environments.getFirst());
    }

    @Test
    void streamsOutputAndStillReturnsCapturedOutput() {
        List<String> streamed = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", (command, outputConsumer) -> {
            outputConsumer.accept("starting\n");
            outputConsumer.accept("ready\n");
            return new JavaRunner.ProcessResult(0, "starting\nready\n");
        });

        JavaRunResult result = runner.run(
                Path.of("java"),
                new Classpath(List.of(Path.of("target/classes"))),
                "com.example.Main",
                List.of(),
                streamed::add);

        assertEquals(List.of("starting\n", "ready\n"), streamed);
        assertEquals("starting\nready\n", result.output());
    }

    @Test
    void runsJarWithArguments() {
        List<List<String>> commands = new ArrayList<>();
        JavaRunner runner = new JavaRunner(":", (command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "boot\n");
        });

        JavaRunResult result = runner.runJar(
                Path.of("java"),
                Path.of("target/demo.jar"),
                "com.example.Main",
                List.of("one", "two"));

        assertEquals("com.example.Main", result.mainClass());
        assertEquals("boot\n", result.output());
        assertEquals(List.of("java", "-jar", "target/demo.jar", "one", "two"), commands.getFirst());
    }

    @Test
    void nonZeroExitIncludesApplicationOutput() {
        JavaRunner runner = new JavaRunner(":", (command, outputConsumer) -> new JavaRunner.ProcessResult(7, "boom\n"));

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
