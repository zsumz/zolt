package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildServiceIncrementalMainCompileTest {
    private final BuildService buildService = new BuildService();

    @TempDir
    private Path projectDir;

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
        assertEquals("full", first.mainCompilationMode());
        assertEquals("missing-state", first.mainIncrementalFallbackReason());
        assertTrue(second.mainCompilationSkipped());
        assertEquals(1, second.sourceCount());
        assertTrue(second.mainFingerprintCheckNanos() > 0);
        assertTrue(first.mainFingerprintWriteNanos() > 0);
        assertEquals(0L, second.mainFingerprintWriteNanos());
        assertTrue(Files.exists(projectDir.resolve("target/classes/.zolt-build-main.fingerprint.state")));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void fullMainCompileWritesIncrementalOwnershipState() throws IOException {
        writeLockfile("version = 1\n");
        Path mainSource = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return Helper.message();
                    }

                    static final class Nested {
                    }
                }
                """);
        source("src/main/java/com/example/Helper.java", """
                package com.example;

                public final class Helper {
                    public static String message() {
                        return "hello";
                    }
                }
                """);

        buildService.build(projectDir, config(), projectDir.resolve("cache"));

        IncrementalCompileState state = new IncrementalCompileStateCodec()
                .read(projectDir.resolve("target/classes/.zolt-incremental-main.state"))
                .orElseThrow();
        IncrementalCompileState.SourceRecord mainRecord = state.sources().stream()
                .filter(source -> source.path().equals(mainSource.toAbsolutePath().normalize()))
                .findFirst()
                .orElseThrow();
        assertEquals("main", state.scope());
        assertTrue(state.fallbackReasons().isEmpty());
        assertTrue(mainRecord.declaredTypes().contains("com.example.Main"));
        assertTrue(mainRecord.classOutputs().contains(projectDir
                .resolve("target/classes/com/example/Main.class")
                .toAbsolutePath()
                .normalize()));
        assertTrue(mainRecord.classOutputs().contains(projectDir
                .resolve("target/classes/com/example/Main$Nested.class")
                .toAbsolutePath()
                .normalize()));
        assertTrue(state.classes().stream().anyMatch(classRecord -> classRecord.binaryName().equals("com.example.Main")));
        assertTrue(state.reverseDependencies().containsKey("com.example.Helper"));
    }

    @Test
    void failedMainCompileDeletesIncrementalOwnershipState() throws IOException {
        writeLockfile("version = 1\n");
        Path mainSource = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        assertTrue(Files.exists(projectDir.resolve("target/classes/.zolt-incremental-main.state")));
        Files.writeString(mainSource, """
                package com.example;

                public final class Main {
                    broken
                }
                """);

        assertThrows(
                JavacException.class,
                () -> buildService.build(projectDir, config(), projectDir.resolve("cache")));

        assertFalse(Files.exists(projectDir.resolve("target/classes/.zolt-incremental-main.state")));
        Files.writeString(mainSource, """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "fixed";
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("full", result.mainCompilationMode());
        assertEquals("missing-state", result.mainIncrementalFallbackReason());
    }

    @Test
    void staleMainFingerprintStateFallsBackToFullFingerprint() throws IOException {
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
        Files.writeString(projectDir.resolve("target/classes/.zolt-build-main.fingerprint.state"), "version=stale\n");

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertTrue(result.mainCompilationSkipped());
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
    void bodyOnlySourceChangeUsesIncrementalMainCompilation() throws IOException {
        writeLockfile("version = 1\n");
        Path source = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return Helper.message() + " one";
                    }
                }
                """);
        source("src/main/java/com/example/Helper.java", """
                package com.example;

                public final class Helper {
                    public static String message() {
                        return "helper";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(source, """
                package com.example;

                public final class Main {
                    public static String message() {
                        return Helper.message() + " two";
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.mainCompilationSkipped());
        assertEquals("incremental", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void abiChangingSourceWithoutDependentsStaysIncrementalMainCompilation() throws IOException {
        writeLockfile("version = 1\n");
        Path source = source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "one";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(source, """
                package com.example;

                public final class Main {
                    public static int message() {
                        return 2;
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void publicAbiChangeRecompilesMainSourcesThatReferenceChangedClass() throws IOException {
        writeLockfile("version = 1\n");
        Path apiSource = source("src/main/java/com/example/api/Api.java", """
                package com.example.api;

                public final class Api {
                    public static String message() {
                        return "one";
                    }
                }
                """);
        source("src/main/java/com/example/app/UseApi.java", """
                package com.example.app;

                import com.example.api.Api;

                public final class UseApi {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example.api;

                public final class Api {
                    public static String message() {
                        return "two";
                    }

                    public static String extra() {
                        return "extra";
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(2, result.sourceCount());
    }

    @Test
    void privateOnlySourceChangeDoesNotRecompileMainDependents() throws IOException {
        writeLockfile("version = 1\n");
        Path apiSource = source("src/main/java/com/example/api/Api.java", """
                package com.example.api;

                public final class Api {
                    public static String message() {
                        return hidden();
                    }

                    private static String hidden() {
                        return "one";
                    }
                }
                """);
        source("src/main/java/com/example/app/UseApi.java", """
                package com.example.app;

                import com.example.api.Api;

                public final class UseApi {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example.api;

                public final class Api {
                    public static String message() {
                        return hidden();
                    }

                    private static String hidden() {
                        return "two";
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void packagePrivateAbiChangeRecompilesSamePackageMainSources() throws IOException {
        writeLockfile("version = 1\n");
        Path apiSource = source("src/main/java/com/example/Api.java", """
                package com.example;

                public final class Api {
                    static String packageMessage() {
                        return "one";
                    }
                }
                """);
        source("src/main/java/com/example/Neighbor.java", """
                package com.example;

                public final class Neighbor {
                    public String message() {
                        return "neighbor";
                    }
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example;

                public final class Api {
                    static int packageMessage() {
                        return 2;
                    }
                }
                """);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(2, result.sourceCount());
    }

    @Test
    void deletedSourceFallsBackToFullMainCompilationAndDeletesOwnedClass() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                }
                """);
        Path deletedSource = source("src/main/java/com/example/Deleted.java", """
                package com.example;

                public final class Deleted {
                }
                """);
        buildService.build(projectDir, config(), projectDir.resolve("cache"));
        Files.delete(deletedSource);

        BuildResult result = buildService.build(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("full", result.mainCompilationMode());
        assertEquals("source-deleted", result.mainIncrementalFallbackReason());
        assertFalse(Files.exists(projectDir.resolve("target/classes/com/example/Deleted.class")));
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
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
