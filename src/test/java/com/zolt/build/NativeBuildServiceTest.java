package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class NativeBuildServiceTest extends NativeBuildServiceTestSupport {
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
                        .withPackageSettings(new com.zolt.project.PackageSettings(com.zolt.project.PackageMode.UBER)),
                cacheRoot,
                Path.of("custom-native-image"));

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path outputBinary = projectDir.resolve("target/native-custom/demo-native");
        Path logFile = projectDir.resolve("target/native-custom/native-image.log");
        assertEquals(jarPath, result.packageResult().jarPath());
        assertEquals(com.zolt.project.PackageMode.THIN, result.packageResult().mode());
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
    void springBootNativeFailsBeforeInvokingNativeImage() {
        List<List<String>> commands = new ArrayList<>();
        NativeBuildService service = service(command -> {
            commands.add(command);
            return new NativeImageRunner.ProcessResult(0, "native ok\n");
        });

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> service.buildNative(
                        projectDir,
                        config(Optional.of("com.example.Main"))
                                .withPackageSettings(new com.zolt.project.PackageSettings(com.zolt.project.PackageMode.SPRING_BOOT)),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("Spring Boot native images are not supported"));
        assertTrue(exception.getMessage().contains("Spring Boot JVM build, test, run, and executable packaging"));
        assertTrue(exception.getMessage().contains("zolt package --mode spring-boot"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void micronautNativeFailsBeforeInvokingNativeImage() {
        List<List<String>> commands = new ArrayList<>();
        NativeBuildService service = service(command -> {
            commands.add(command);
            return new NativeImageRunner.ProcessResult(0, "native ok\n");
        });

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> service.buildNative(
                        projectDir,
                        new ZoltTomlParser().parse("""
                                [project]
                                name = "demo"
                                version = "0.1.0"
                                group = "com.example"
                                java = "21"
                                main = "com.example.Main"

                                [dependencies]
                                "io.micronaut:micronaut-http-server-netty" = "4.9.4"
                                """),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("Micronaut native images are not supported"));
        assertTrue(exception.getMessage().contains("Micronaut JVM build/test flows"));
        assertTrue(exception.getMessage().contains("zolt package --mode thin"));
        assertTrue(commands.isEmpty());
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
}
