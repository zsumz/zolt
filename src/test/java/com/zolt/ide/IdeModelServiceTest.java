package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusAugmentationMetadataWriter;
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
    void exportsGroovyTestRootsDeterministically() throws IOException {
        Path projectDir = tempDir.resolve("groovy-tests");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "groovy-tests"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java"]
                groovy = ["src/test/groovy", "src/integrationTest/groovy"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertTrue(model.sourceRoots().contains(new IdeModel.SourceRoot(
                "test-groovy-1",
                "test",
                "groovy",
                root.resolve("src/test/groovy"),
                false)));
        assertTrue(model.sourceRoots().contains(new IdeModel.SourceRoot(
                "test-groovy-2",
                "test",
                "groovy",
                root.resolve("src/integrationTest/groovy"),
                false)));
    }

    @Test
    void exportsConfiguredResourceRootsDeterministically() throws IOException {
        Path projectDir = tempDir.resolve("resource-roots");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "resource-roots"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources]
                main = ["src/main/resources", "target/generated/resources"]
                test = ["src/test/resources", "target/generated/test-resources"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(List.of(
                new IdeModel.ResourceRoot("main-resources", "main", root.resolve("src/main/resources")),
                new IdeModel.ResourceRoot("main-resources-2", "main", root.resolve("target/generated/resources")),
                new IdeModel.ResourceRoot("test-resources", "test", root.resolve("src/test/resources")),
                new IdeModel.ResourceRoot("test-resources-2", "test", root.resolve("target/generated/test-resources"))),
                model.resourceRoots());
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

                [dev.dependencies]
                "org.springframework.boot:spring-boot-devtools" = {}

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
                List.of(new IdeModel.DependencyDeclaration("org.springframework.boot:spring-boot-devtools", null, true, null)),
                model.dependencies().dev());
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
        assertTrue(json.contains("\"dev\": ["));
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

    @Test
    void exportsQuarkusFrameworkStateWithMissingAugmentationDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("quarkus-missing");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "quarkus-missing"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        IdeModel.QuarkusInfo quarkus = model.frameworks().quarkus();
        assertTrue(quarkus.enabled());
        assertEquals("fast-jar", quarkus.packageMode());
        assertEquals("missing", quarkus.augmentationStatus());
        assertEquals(root.resolve("target/quarkus"), quarkus.augmentationDirectory());
        assertEquals(root.resolve("target/quarkus-app"), quarkus.packageDirectory());
        assertEquals(root.resolve("target/quarkus-app/quarkus-run.jar"), quarkus.runnerJar());
        assertEquals(root.resolve("target/quarkus-app/quarkus/generated-bytecode.jar"), quarkus.generatedBytecodeJar());
        assertEquals(root.resolve("target/quarkus-app/quarkus/transformed-bytecode.jar"), quarkus.transformedBytecodeJar());
        assertEquals(List.of(), quarkus.deploymentClasspath());
        assertEquals("QUARKUS_AUGMENTATION_MISSING", model.diagnostics().getFirst().code());

        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"frameworks\": {"));
        assertTrue(json.contains("\"quarkus\": {"));
        assertTrue(json.contains("\"augmentationStatus\": \"missing\""));
        assertTrue(json.contains("\"generatedBytecodeJar\": \""));
        assertTrue(json.contains("\"deploymentClasspath\": []"));
    }

    @Test
    void exportsCurrentQuarkusFrameworkStateWithoutAugmentationDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("quarkus-current");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "quarkus-current"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        IdeModel missingModel = service.export(projectDir, tempDir.resolve("cache"));
        new QuarkusAugmentationMetadataWriter().write(
                projectDir.toAbsolutePath().normalize(),
                missingModel.frameworks().quarkus().inputFingerprint());

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        assertEquals("current", model.frameworks().quarkus().augmentationStatus());
        assertEquals(
                model.frameworks().quarkus().inputFingerprint(),
                model.frameworks().quarkus().recordedInputFingerprint());
        assertEquals(List.of(), model.diagnostics());
    }
}
