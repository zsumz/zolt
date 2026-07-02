package sh.zolt.build.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.AnnotationProcessorFixture;
import sh.zolt.classpath.Classpath;
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
                "-proc:none",
                source.normalize().toString()), commands.getFirst());
    }

    @Test
    void passesProcessorPathAndGeneratedSourcesToJavacCommand() throws IOException {
        Path source = source("src/main/java/com/example/Main.java", "final class Main {}\n");
        Path generatedSources = tempDir.resolve("target/generated/sources/annotations");
        List<List<String>> commands = new ArrayList<>();
        JavacRunner runner = new JavacRunner(":", command -> {
            commands.add(command);
            return new JavacRunner.ProcessResult(0, "");
        });

        runner.compile(
                Path.of("javac"),
                List.of(source),
                new Classpath(List.of(Path.of("zeta.jar"), Path.of("alpha.jar"))),
                tempDir.resolve("target/classes"),
                new Classpath(List.of(Path.of("processor-z.jar"), Path.of("processor-a.jar"))),
                generatedSources);

        assertEquals(List.of(
                "javac",
                "-d",
                tempDir.resolve("target/classes").toString(),
                "-classpath",
                "alpha.jar:zeta.jar",
                "-processorpath",
                "processor-a.jar:processor-z.jar:alpha.jar:zeta.jar",
                "-s",
                generatedSources.toString(),
                source.normalize().toString()), commands.getFirst());
    }

    @Test
    void passesCompilerOptionsToJavacCommand() throws IOException {
        Path source = source("src/main/java/com/example/Main.java", "final class Main {}\n");
        List<List<String>> commands = new ArrayList<>();
        JavacRunner runner = new JavacRunner(":", command -> {
            commands.add(command);
            return new JavacRunner.ProcessResult(0, "");
        });

        runner.compile(
                Path.of("javac"),
                List.of(source),
                new Classpath(List.of()),
                tempDir.resolve("target/classes"),
                new Classpath(List.of()),
                null,
                new JavacOptions("17", "UTF-8", List.of("-Xlint:deprecation", "-parameters")));

        assertEquals(List.of(
                "javac",
                "-d",
                tempDir.resolve("target/classes").toString(),
                "--release",
                "17",
                "-encoding",
                "UTF-8",
                "-proc:none",
                "-Xlint:deprecation",
                "-parameters",
                source.normalize().toString()), commands.getFirst());
    }

    @Test
    void runsAnnotationProcessorAndCompilesGeneratedSource() throws IOException {
        Path processorJar = AnnotationProcessorFixture.processorJar(tempDir);
        Path source = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return GeneratedMessage.value();
                    }
                }
                """);
        Path output = tempDir.resolve("target/classes");
        Path generatedSources = tempDir.resolve("target/generated/sources/annotations");

        JavacResult result = new JavacRunner().compile(
                currentJavac(),
                List.of(source),
                new Classpath(List.of()),
                output,
                new Classpath(List.of(processorJar)),
                generatedSources);

        assertEquals(1, result.sourceCount());
        assertTrue(Files.exists(generatedSources.resolve("com/example/GeneratedMessage.java")));
        assertTrue(Files.exists(output.resolve("com/example/Main.class")));
        assertTrue(Files.exists(output.resolve("com/example/GeneratedMessage.class")));
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
