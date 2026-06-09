package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildServiceTest {
    private final BuildService buildService = new BuildService();

    @TempDir
    private Path projectDir;

    @Test
    void mainCompilationUsesProcessorClasspathAndGeneratedSources() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        Files.createDirectories(processorJar.getParent());
        Files.copy(
                AnnotationProcessorFixture.processorJar(projectDir.resolve("processor-work")),
                processorJar,
                StandardCopyOption.REPLACE_EXISTING);
        writeLockfile("""
                version = 1

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                """);
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return GeneratedMessage.value();
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), cacheRoot);

        assertEquals(1, result.sourceCount());
        assertTrue(Files.exists(projectDir.resolve(
                "target/generated/sources/annotations/com/example/GeneratedMessage.java")));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/GeneratedMessage.class")));
    }

    @Test
    void generatesReproducibleBuildInfoMetadata() throws IOException {
        writeLockfile("version = 1\n");
        ProjectConfig config = config().withBuildSettings(new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                new BuildMetadataSettings(true, false, true)));

        BuildResult result = buildService.build(projectDir, config, projectDir.resolve("cache"));

        assertEquals(0, result.sourceCount());
        assertEquals(1, result.resourceCount());
        assertEquals("""
                build.artifact=demo
                build.group=com.example
                build.name=demo
                build.time=1970-01-01T00:00:00Z
                build.version=0.1.0
                """, Files.readString(projectDir.resolve("target/classes/META-INF/build-info.properties")));
    }

    @Test
    void repeatedMainBuildSkipsCompilationWhenInputsAreCurrent() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);

        BuildResult first = buildService.build(projectDir, config(), projectDir.resolve("cache"));
        BuildResult second = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(first.mainCompilationSkipped());
        assertTrue(second.mainCompilationSkipped());
        assertEquals(1, second.sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void sourceChangeInvalidatesMainBuildFingerprint() throws IOException {
        writeLockfile("version = 1\n");
        Path source = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(source, """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "changed";
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.mainCompilationSkipped());
    }

    @Test
    void resourceChangeInvalidatesMainBuildFingerprintButStillCopiesResource() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        source("src/main/resources/application.properties", "message=hello\n");
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(projectDir.resolve("src/main/resources/application.properties"), "message=changed\n");

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.mainCompilationSkipped());
        assertEquals("message=changed\n", Files.readString(projectDir.resolve("target/classes/application.properties")));
    }

    @Test
    void missingExpectedClassFilePreventsMainBuildSkip() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.delete(projectDir.resolve("target/classes/com/example/Main.class"));

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.mainCompilationSkipped());
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata(
                        "demo",
                        "0.1.0",
                        "com.example",
                        currentJavaMajorVersion(),
                        Optional.of("com.example.Main")),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private Path source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    private void writeLockfile(String content) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), content);
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
