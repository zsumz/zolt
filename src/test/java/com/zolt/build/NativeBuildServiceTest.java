package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.project.ProjectConfig;
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
    void explicitSpringBootNativeSettingRequiresAotOutputsBeforeInvokingNativeImage() {
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

                                [framework.springBoot.native]
                                enabled = true
                                """),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("[framework.springBoot.native] enabled = true"));
        assertTrue(exception.getMessage().contains("Spring Boot native AOT output is missing"));
        assertTrue(exception.getMessage().contains("target/spring-aot/main/classes"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void explicitSpringBootNativePassesAotOutputsToNativeImageClasspath() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        writeRuntimeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source("target/spring-aot/main/classes/com/example/Application__BeanDefinitions.class", "aot class");
        source("target/spring-aot/main/resources/META-INF/native-image/com.example/demo/reflect-config.json", "[]");
        List<List<String>> commands = new ArrayList<>();
        NativeBuildService service = service(command -> {
            commands.add(command);
            writeNativeBinary(Path.of(command.getLast()));
            return new NativeImageRunner.ProcessResult(0, "native ok\n");
        });

        NativeBuildResult result = service.buildNative(
                projectDir,
                springBootNativeConfig(),
                cacheRoot,
                Path.of("native-image"));

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Path aotClasses = projectDir.resolve("target/spring-aot/main/classes");
        Path aotResources = projectDir.resolve("target/spring-aot/main/resources");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        assertEquals(projectDir.resolve("target/native-custom/demo-native"), result.nativeImageResult().outputBinary());
        assertEquals(List.of(
                "native-image",
                "--no-fallback",
                "-cp",
                jarPath + ":" + aotClasses + ":" + aotResources + ":" + dependencyJar,
                "com.example.Main",
                "-o",
                projectDir.resolve("target/native-custom/demo-native").toString()), commands.getFirst());
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
    void quarkusSupportLibrariesDoNotBlockPlainNativeBuilds() throws IOException {
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
                new ZoltTomlParser().parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        main = "com.example.Main"

                        [provided.dependencies]
                        "io.quarkus:quarkus-builder" = "3.33.2"
                        "io.quarkus:quarkus-junit" = "3.33.2"
                        """),
                projectDir.resolve("cache"),
                Path.of("native-image"));

        assertEquals(projectDir.resolve("target/native/demo"), result.nativeImageResult().outputBinary());
        assertTrue(Files.exists(projectDir.resolve("target/native/demo")));
        assertEquals(1, commands.size());
    }

    @Test
    void explicitQuarkusNativeFailsBeforeInvokingNativeImage() {
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

                                [framework.quarkus]
                                enabled = true
                                """),
                        projectDir.resolve("cache"),
                        Path.of("native-image")));

        assertTrue(exception.getMessage().contains("Quarkus native images are not supported"));
        assertTrue(exception.getMessage().contains("Quarkus JVM build/test/package path"));
        assertTrue(exception.getMessage().contains("zolt package --mode quarkus"));
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

    private static ProjectConfig springBootNativeConfig() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [framework.springBoot.native]
                enabled = true

                [native]
                imageName = "demo-native"
                output = "target/native-custom"
                args = ["--no-fallback"]
                """);
    }
}
