package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CleanCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void cleanDeletesBuildOutputWithoutDeletingCache() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.createDirectories(projectDir.resolve("target/classes"));
        Files.writeString(projectDir.resolve("target/classes/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve(".zolt/cache"));
        Files.writeString(projectDir.resolve(".zolt/cache/artifact.jar"), "cached");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 1 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("target")));
        assertTrue(Files.exists(projectDir.resolve(".zolt/cache/artifact.jar")));
    }

    @Test
    void cleanDeletesQuarkusOutputLayoutWhenEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("output = \"target/classes\"", "output = \"out/main\"")
                        .replace("testOutput = \"target/test-classes\"", "testOutput = \"out/test\""));
        enableQuarkus(projectDir);
        Files.createDirectories(projectDir.resolve("out/main"));
        Files.writeString(projectDir.resolve("out/main/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve("target/quarkus"));
        Files.writeString(projectDir.resolve("target/quarkus/zolt-augmentation.properties"), "metadata");
        Files.createDirectories(projectDir.resolve("target/quarkus-app"));
        Files.writeString(projectDir.resolve("target/quarkus-app/quarkus-run.jar"), "jar");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 3 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus-app")));
    }

    @Test
    void cleanDeletesSpringBootAotOutputLayoutWhenNativeIsEnabled() throws IOException {
        Path projectDir = tempDir.resolve("spring-aot-clean");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("output = \"target/classes\"", "output = \"out/main\"")
                        .replace("testOutput = \"target/test-classes\"", "testOutput = \"out/test\""));
        enableSpringBootNative(projectDir);
        Files.createDirectories(projectDir.resolve("target/spring-aot/main/sources/com/example"));
        Files.writeString(projectDir.resolve("target/spring-aot/main/sources/com/example/Application__BeanDefinitions.java"), "aot");
        Files.createDirectories(projectDir.resolve("target/spring-aot/main/resources/META-INF/native-image"));
        Files.writeString(projectDir.resolve("target/spring-aot/main/resources/META-INF/native-image/reflect-config.json"), "[]");
        Files.createDirectories(projectDir.resolve("target/spring-aot/main/classes"));
        Files.writeString(projectDir.resolve("target/spring-aot/main/classes/Application__BeanDefinitions.class"), "class");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 1 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("target/spring-aot")));
    }

    @Test
    void cleanDeletesProtobufGeneratedOutputs() throws IOException {
        Path projectDir = tempDir.resolve("protobuf-clean");
        Files.createDirectories(projectDir.resolve("target/generated/sources/protobuf/com/example"));
        Files.writeString(projectDir.resolve("target/generated/sources/protobuf/com/example/HelloRequest.java"), "generated");
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "protobuf-clean"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "out/classes"
                testOutput = "out/test-classes"

                [generated.main.greeter]
                kind = "protobuf"
                language = "java"
                output = "target/generated/sources/protobuf"
                inputs = ["src/main/proto/greeter.proto"]
                """.formatted(currentJavaMajorVersion()));

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 1 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("target/generated/sources/protobuf")));
    }

    @Test
    void cleanHandlesMissingTargetCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("Nothing to clean\n", result.stdout());
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void enableQuarkus(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);
    }

    private static void enableSpringBootNative(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [framework.springBoot.native]
                enabled = true
                """);
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
