package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacRunnerTest {
    @TempDir
    private Path tempDir;

    @Test
    void compilesMainSourcesAndCreatesOutputDirectory() throws IOException {
        Path source = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        Path output = tempDir.resolve("target/classes");

        JavacResult result = new JavacRunner().compile(
                currentJavac(),
                List.of(source),
                new Classpath(List.of()),
                output);

        assertEquals(1, result.sourceCount());
        assertEquals(output, result.outputDirectory());
        assertTrue(Files.exists(output.resolve("com/example/Main.class")));
    }

    @Test
    void returnsWithoutRunningJavacWhenThereAreNoSources() {
        List<List<String>> commands = new ArrayList<>();
        JavacRunner runner = new JavacRunner(":", command -> {
            commands.add(command);
            return new JavacRunner.ProcessResult(0, "");
        });

        JavacResult result = runner.compile(
                Path.of("javac"),
                List.of(),
                new Classpath(List.of(Path.of("lib.jar"))),
                tempDir.resolve("target/classes"));

        assertEquals(0, result.sourceCount());
        assertTrue(commands.isEmpty());
        assertTrue(Files.exists(tempDir.resolve("target/classes")));
    }

    @Test
    void passesCompileClasspathToJavacCommand() throws IOException {
        Path source = source("src/main/java/com/example/Main.java", "final class Main {}\n");
        List<List<String>> commands = new ArrayList<>();
        JavacRunner runner = new JavacRunner(":", command -> {
            commands.add(command);
            return new JavacRunner.ProcessResult(0, "");
        });

        runner.compile(
                Path.of("javac"),
                List.of(source),
                new Classpath(List.of(Path.of("zeta.jar"), Path.of("alpha.jar"))),
                tempDir.resolve("target/classes"));

        assertEquals(List.of(
                "javac",
                "-d",
                tempDir.resolve("target/classes").toString(),
                "-classpath",
                "alpha.jar:zeta.jar",
                source.normalize().toString()), commands.getFirst());
    }

    @Test
    void surfacesJavacErrorsWithCompilerOutput() throws IOException {
        Path source = source("src/main/java/com/example/Broken.java", """
                package com.example;

                public final class Broken {
                    missing
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> new JavacRunner().compile(
                        currentJavac(),
                        List.of(source),
                        new Classpath(List.of()),
                        tempDir.resolve("target/classes")));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("Broken.java"));
        assertFalse(exception.getMessage().isBlank());
    }

    private Path source(String path, String content) throws IOException {
        Path source = tempDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }
}
