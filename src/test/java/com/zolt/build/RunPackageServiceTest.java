package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.doctor.JdkDetector;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RunPackageServiceTest {
    @TempDir
    private Path projectDir;

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

    @Test
    void missingMainClassProducesActionableErrorBeforePackaging() {
        RunPackageService service = service((command, outputConsumer) -> new JavaRunner.ProcessResult(0, ""));

        RunPackageException exception = assertThrows(
                RunPackageException.class,
                () -> service.runPackage(
                        projectDir,
                        config(Optional.empty()),
                        projectDir.resolve("cache"),
                        List.of()));

        assertTrue(exception.getMessage().contains("No main class is configured"));
        assertTrue(exception.getMessage().contains("[project].main"));
    }

    private RunPackageService service(JavaRunner.ProcessRunner processRunner) {
        return new RunPackageService(
                new PackageService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(":", processRunner));
    }

    private void writeRuntimeLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                """);
    }

    private void source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static ProjectConfig config(Optional<String> mainClass) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), mainClass),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
