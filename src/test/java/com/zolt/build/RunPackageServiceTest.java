package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RunPackageServiceTest extends RunPackageServiceTestSupport {
    @Test
    void runsPackagedJarWithRuntimeClasspathAndArguments() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        writeRuntimeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        List<List<String>> commands = new ArrayList<>();
        RunPackageService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "hello\n");
        });

        RunPackageResult result = service.runPackage(
                projectDir,
                config(Optional.of("com.example.Main")),
                cacheRoot,
                List.of("one", "two"));

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        assertEquals(jarPath, result.packageResult().jarPath());
        assertEquals("hello\n", result.javaRunResult().output());
        assertTrue(Files.exists(jarPath));
        assertFalse(commands.getFirst().get(2).contains(processorJar.toString()));
        assertEquals(List.of(
                commands.getFirst().get(0),
                "-classpath",
                jarPath + ":" + dependencyJar,
                "com.example.Main",
                "one",
                "two"), commands.getFirst());
    }
}
