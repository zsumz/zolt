package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelServiceTest {
    private final IdeModelService service = new IdeModelService();

    @TempDir
    private Path tempDir;

    @Test
    void exportsSpringBootWebmvcExampleWithPlatformManagedClasspath() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-webmvc");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.copy(Path.of("examples/spring-boot-webmvc/zolt.toml"), projectDir.resolve("zolt.toml"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot-starter-webmvc"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/springframework/boot/spring-boot-starter-webmvc/4.0.6/spring-boot-starter-webmvc-4.0.6.jar"
                dependencies = ["org.springframework:spring-webmvc"]

                [[package]]
                id = "org.springframework:spring-webmvc"
                version = "7.0.5"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/spring-webmvc/7.0.5/spring-webmvc-7.0.5.jar"
                dependencies = []
                """);

        IdeModel model = service.export(projectDir, cacheRoot);

        Path root = projectDir.toAbsolutePath().normalize();
        Path absoluteCache = cacheRoot.toAbsolutePath().normalize();
        assertEquals("spring-boot-webmvc", model.project().name());
        assertEquals("com.example.springboot.DemoApplication", model.project().mainClass());
        assertEquals(List.of(
                new IdeModel.SourceRoot("main-java", "main", "java", root.resolve("src/main/java"), false),
                new IdeModel.SourceRoot(
                        "main-generated-java",
                        "main",
                        "java",
                        root.resolve("target/generated/sources/annotations"),
                        true),
                new IdeModel.SourceRoot("test-java-1", "test", "java", root.resolve("src/test/java"), false),
                new IdeModel.SourceRoot(
                        "test-generated-java",
                        "test",
                        "java",
                        root.resolve("target/generated/test-sources/annotations"),
                        true)), model.sourceRoots());
        assertEquals(List.of(
                absoluteCache.resolve(
                        "org/springframework/boot/spring-boot-starter-webmvc/4.0.6/spring-boot-starter-webmvc-4.0.6.jar"),
                absoluteCache.resolve("org/springframework/spring-webmvc/7.0.5/spring-webmvc-7.0.5.jar")),
                model.classpaths().compile());
        assertEquals(List.of(
                root.resolve("target/classes"),
                absoluteCache.resolve(
                        "org/springframework/boot/spring-boot-starter-webmvc/4.0.6/spring-boot-starter-webmvc-4.0.6.jar"),
                absoluteCache.resolve("org/springframework/spring-webmvc/7.0.5/spring-webmvc-7.0.5.jar")),
                model.classpaths().runtime());
        assertEquals(List.of(), model.diagnostics());
    }

    @Test
    void exportsMultipleJavaTestRootsDeterministically() throws IOException {
        Path projectDir = tempDir.resolve("multi-root-tests");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "multi-root-tests"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java", "src/integrationTest/java", "src/contractTest/java"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(List.of(
                new IdeModel.SourceRoot("main-java", "main", "java", root.resolve("src/main/java"), false),
                new IdeModel.SourceRoot(
                        "main-generated-java",
                        "main",
                        "java",
                        root.resolve("target/generated/sources/annotations"),
                        true),
                new IdeModel.SourceRoot("test-java-1", "test", "java", root.resolve("src/test/java"), false),
                new IdeModel.SourceRoot(
                        "test-java-2",
                        "test",
                        "java",
                        root.resolve("src/integrationTest/java"),
                        false),
                new IdeModel.SourceRoot(
                        "test-java-3",
                        "test",
                        "java",
                        root.resolve("src/contractTest/java"),
                        false),
                new IdeModel.SourceRoot(
                        "test-generated-java",
                        "test",
                        "java",
                        root.resolve("target/generated/test-sources/annotations"),
                        true)), model.sourceRoots());
    }

    @Test
    void exportsDependencyDeclarationsWithVisibility() throws IOException {
        Path projectDir = tempDir.resolve("dependencies");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "dependencies"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "com.example:platform" = "1.0.0"

                [api.dependencies]
                "com.example:api-contract" = "1.0.0"
                "com.example:managed-api" = {}
                "com.example:workspace-api" = { workspace = "modules/api" }

                [dependencies]
                "com.example:impl" = "1.0.0"

                [runtime.dependencies]
                "com.example:runtime-only" = {}

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = {}

                [annotationProcessors]
                "com.example:processor" = "1.0.0"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        assertEquals(List.of(
                        new IdeModel.DependencyDeclaration("com.example:api-contract", "1.0.0", false, null),
                        new IdeModel.DependencyDeclaration("com.example:managed-api", null, true, null),
                        new IdeModel.DependencyDeclaration("com.example:workspace-api", null, false, "modules/api")),
                model.dependencies().api());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("com.example:impl", "1.0.0", false, null)),
                model.dependencies().implementation());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("com.example:runtime-only", null, true, null)),
                model.dependencies().runtime());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("jakarta.servlet:jakarta.servlet-api", "6.1.0", false, null)),
                model.dependencies().provided());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("org.junit.jupiter:junit-jupiter", null, true, null)),
                model.dependencies().test());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("com.example:processor", "1.0.0", false, null)),
                model.dependencies().annotationProcessors());

        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"dependencies\": {"));
        assertTrue(json.contains("\"api\": ["));
        assertTrue(json.contains("\"implementation\": ["));
        assertTrue(json.contains("\"runtime\": ["));
        assertTrue(json.contains("\"provided\": ["));
        assertTrue(json.contains("\"coordinate\": \"com.example:workspace-api\""));
        assertTrue(json.contains("\"workspace\": \"modules/api\""));
        assertTrue(json.contains("\"managed\": true"));
    }

    @Test
    void writesStableDiagnosticsForEditorImportSnapshots() throws IOException {
        Path projectDir = tempDir.resolve("diagnostics");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "diagnostics"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        String json = new IdeModelJsonWriter().write(service.export(projectDir, tempDir.resolve("cache")));

        assertTrue(json.contains("\"code\": \"LOCKFILE_MISSING\""));
        assertTrue(json.contains("\"message\": \"Could not find zolt.lock.\""));
        assertTrue(json.contains("\"nextStep\": \"Run zolt resolve.\""));
        assertTrue(json.contains("\"compile\": []"));
        assertTrue(json.contains("\"runtime\": []"));
        assertTrue(json.contains("\"test\": []"));
    }
}
