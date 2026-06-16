package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.ProjectPathException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeBuildServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void packagesJarThenBuildsNativeBinaryWithConfiguredSettings() throws IOException {
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
        NativeBuildService service = service(command -> {
            commands.add(command);
            writeNativeBinary(Path.of(command.getLast()));
            return new NativeImageRunner.ProcessResult(0, "native ok\n");
        });

        NativeBuildResult result = service.buildNative(
                projectDir,
                config(Optional.of("com.example.Main"))
                        .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                cacheRoot,
                Path.of("custom-native-image"));

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path outputBinary = projectDir.resolve("target/native-custom/demo-native");
        Path logFile = projectDir.resolve("target/native-custom/native-image.log");
        assertEquals(jarPath, result.packageResult().jarPath());
        assertEquals(PackageMode.THIN, result.packageResult().mode());
        assertEquals(outputBinary, result.nativeImageResult().outputBinary());
        assertEquals(logFile, result.nativeImageResult().logFile());
        assertTrue(Files.exists(jarPath));
        assertTrue(Files.exists(outputBinary));
        assertEquals("native ok\n", Files.readString(logFile));
        assertEquals(List.of(
                "custom-native-image",
                "--no-fallback",
                "--native-image-info",
                "-cp",
                jarPath + ":" + dependencyJar,
                "com.example.Main",
                "-o",
                outputBinary.toString()), commands.getFirst());
    }

    @Test
    void seriousNativeImageWarningsAreActionable() throws IOException {
        writeRuntimeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        NativeBuildService service = service(command -> {
            writeNativeBinary(Path.of(command.getLast()));
            return new NativeImageRunner.ProcessResult(
                    0,
                    "Warning: unsupported reflection configuration\n");
        });

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> service.buildNative(
                        projectDir,
                        config(Optional.of("com.example.Main")),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("serious warning terms"));
        assertTrue(exception.getMessage().contains(projectDir.resolve("target/native-custom/native-image.log").toString()));
        assertTrue(exception.getMessage().contains("Warning: unsupported reflection configuration"));
        assertTrue(Files.exists(projectDir.resolve("target/native-custom/native-image.log")));
    }

    @Test
    void missingMainClassFailsBeforePackaging() {
        NativeBuildService service = service(command -> {
            throw new AssertionError("native-image should not run");
        });

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> service.buildNative(
                        projectDir,
                        config(Optional.empty()),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("[project].main"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void rejectsNativeOutputThatEscapesProject() throws IOException {
        writeRuntimeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        NativeBuildService service = service(command -> {
            throw new AssertionError("native-image should not run");
        });

        ProjectPathException exception = assertThrows(
                ProjectPathException.class,
                () -> service.buildNative(
                        projectDir,
                        config(
                                Optional.of("com.example.Main"),
                                new NativeSettings("demo-native", "../native-out", List.of())),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("[native].output"));
        assertTrue(exception.getMessage().contains("../native-out"));
    }

    @Test
    void rejectsNativeImageNameThatUsesPathSeparator() throws IOException {
        writeRuntimeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        NativeBuildService service = service(command -> {
            throw new AssertionError("native-image should not run");
        });

        ProjectPathException exception = assertThrows(
                ProjectPathException.class,
                () -> service.buildNative(
                        projectDir,
                        config(
                                Optional.of("com.example.Main"),
                                new NativeSettings("bin/demo", "target/native-custom", List.of())),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("[native].imageName"));
        assertTrue(exception.getMessage().contains("bin/demo"));
    }

    private NativeBuildService service(NativeImageRunner.ProcessRunner processRunner) {
        return new NativeBuildService(
                new PackageService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new NativeImageRunner(":", processRunner));
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
                """);
    }

    private void source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static void writeNativeBinary(Path outputBinary) {
        try {
            Files.writeString(outputBinary, "native");
        } catch (IOException exception) {
            throw new AssertionError("Could not write fake native binary", exception);
        }
    }

    private static ProjectConfig config(Optional<String> mainClass) {
        return config(
                mainClass,
                new NativeSettings(
                        "demo-native",
                        "target/native-custom",
                        List.of("--no-fallback", "--native-image-info")));
    }

    private static ProjectConfig config(Optional<String> mainClass, NativeSettings nativeSettings) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), mainClass),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                nativeSettings);
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
