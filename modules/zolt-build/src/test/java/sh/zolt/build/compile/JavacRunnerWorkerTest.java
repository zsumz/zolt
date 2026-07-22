package sh.zolt.build.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.classpath.Classpath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacRunnerWorkerTest {
    @TempDir
    Path tempDir;

    @Test
    void usesWorkerForExternalCompilerWithoutProcessorsOrJvmArguments() throws Exception {
        List<List<String>> externalCommands = new ArrayList<>();
        List<List<String>> workerArguments = new ArrayList<>();
        JavacRunner runner = runner(externalCommands, (javac, kind, arguments) -> {
            workerArguments.add(arguments);
            return Optional.of(new JavacRunner.ProcessResult(0, "worker output"));
        });

        JavacResult result = runner.compile(
                Path.of("/jdk/bin/javac"),
                List.of(source()),
                new Classpath(List.of()),
                tempDir.resolve("classes"));

        assertTrue(externalCommands.isEmpty());
        assertEquals(1, workerArguments.size());
        assertEquals("worker output", result.output());
        assertEquals("-d", workerArguments.getFirst().getFirst());
    }

    @Test
    void fallsBackToExternalCompilerWhenWorkerIsUnavailable() throws Exception {
        List<List<String>> externalCommands = new ArrayList<>();
        JavacRunner runner = runner(externalCommands, (javac, kind, arguments) -> Optional.empty());

        runner.compile(
                Path.of("/jdk/bin/javac"),
                List.of(source()),
                new Classpath(List.of()),
                tempDir.resolve("classes"));

        assertEquals(1, externalCommands.size());
        assertEquals("/jdk/bin/javac", externalCommands.getFirst().getFirst());
    }

    @Test
    void keepsJvmArgumentsOnDirectJavacInvocation() throws Exception {
        List<List<String>> externalCommands = new ArrayList<>();
        List<List<String>> workerArguments = new ArrayList<>();
        JavacRunner runner = runner(externalCommands, (javac, kind, arguments) -> {
            workerArguments.add(arguments);
            return Optional.of(new JavacRunner.ProcessResult(0, ""));
        });

        runner.compile(
                Path.of("/jdk/bin/javac"),
                List.of(source()),
                new Classpath(List.of()),
                tempDir.resolve("classes"),
                new Classpath(List.of()),
                null,
                new JavacOptions("", "", List.of("-J-Xmx256m")));

        assertTrue(workerArguments.isEmpty());
        assertTrue(externalCommands.getFirst().contains("-J-Xmx256m"));
    }

    private JavacRunner runner(
            List<List<String>> externalCommands,
            JavacRunner.WorkerRunner workerRunner) {
        return new JavacRunner(
                ":",
                command -> {
                    externalCommands.add(command);
                    return new JavacRunner.ProcessResult(0, "");
                },
                null,
                workerRunner,
                null);
    }

    private Path source() throws Exception {
        Path source = tempDir.resolve("Demo.java");
        Files.writeString(source, "public class Demo {}\n");
        return source;
    }
}
