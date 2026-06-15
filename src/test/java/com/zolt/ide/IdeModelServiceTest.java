package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusAugmentationMetadataWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelServiceTest {
    private final IdeModelService service = new IdeModelService();

    @TempDir
    private Path tempDir;

    @Test
    void exportsRedactedTestRuntimeSettings() throws IOException {
        Path projectDir = tempDir.resolve("test-runtime");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "test-runtime"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.runtime]
                jvmArgs = ["-Dconfigured=true"]
                systemProperties = { "logs.dir" = "${project.root}/test-logs" }
                environment = { TZ = "America/Chicago", SECRET_TOKEN = "do-not-export" }
                events = ["failed"]
                """);

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        assertEquals(List.of("-Dconfigured=true"), model.testRuntime().jvmArgs());
        assertEquals(
                Map.of("logs.dir", "${project.root}/test-logs"),
                model.testRuntime().systemProperties());
        assertEquals(
                Map.of("SECRET_TOKEN", "<redacted>", "TZ", "<redacted>"),
                model.testRuntime().environment());
        assertEquals(List.of("failed"), model.testRuntime().events());
        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"testRuntime\": {"));
        assertTrue(json.contains("\"SECRET_TOKEN\": \"<redacted>\""));
        assertTrue(json.contains("\"events\": ["));
    }

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

                [[package]]
                id = "org.projectlombok:lombok"
                version = "1.18.42"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor/1.0.0/test-processor-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.2"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar"
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
        assertEquals(
                List.of(absoluteCache.resolve("org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar")),
                model.classpaths().processor());
        assertEquals(
                List.of(absoluteCache.resolve("com/example/test-processor/1.0.0/test-processor-1.0.0.jar")),
                model.classpaths().testProcessor());
        assertEquals(
                List.of(absoluteCache.resolve(
                        "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar")),
                model.classpaths().quarkusDeployment());
        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"processor\": ["));
        assertTrue(json.contains("\"testProcessor\": ["));
        assertTrue(json.contains("\"quarkusDeployment\": ["));
        assertEquals(List.of(), model.diagnostics());
    }

    @Test
    void reportsCacheIntegrityFailureInsteadOfExportingPoisonedClasspaths() throws IOException {
        Path projectDir = tempDir.resolve("corrupted-cache-model");
        Path cacheRoot = tempDir.resolve("cache-corrupted");
        Files.createDirectories(projectDir);
        Path jar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "corrupted runtime jar bytes");
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "corrupted-cache-model"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);

        IdeModel model = service.export(projectDir, cacheRoot);

        assertEquals(List.of(), model.classpaths().runtime());
        IdeModel.Diagnostic diagnostic = model.diagnostics().getFirst();
        assertEquals("LOCKFILE_INTEGRITY_FAILED", diagnostic.code());
        assertTrue(diagnostic.message().contains(
                "Cached jar integrity check failed for com.example:runtime-lib:1.0.0"));
    }


    @Test
    void exportsPackageArtifactSettingsAndPublicationMetadataForEditors() throws IOException {
        Path projectDir = tempDir.resolve("library-package");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "library-package"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                sources = true
                javadoc = true
                tests = true

                [package.metadata]
                name = "Library Package"
                description = "Library packaging fixture"
                url = "https://example.com/library"
                license = "Apache-2.0"
                developers = ["Zolt Team"]
                scm = "https://example.com/library.git"
                issues = "https://example.com/library/issues"

                [package.manifest]
                "Automatic-Module-Name" = "com.example.library"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(new IdeModel.PackageInfo(
                        "thin",
                        true,
                        true,
                        true,
                        root.resolve("target/library-package-0.1.0.jar"),
                        root.resolve("target/library-package-0.1.0-sources.jar"),
                        root.resolve("target/library-package-0.1.0-javadoc.jar"),
                        root.resolve("target/library-package-0.1.0-tests.jar"),
                        new IdeModel.PublicationInfo(
                                "Library Package",
                                "Library packaging fixture",
                                "https://example.com/library",
                                "Apache-2.0",
                                List.of("Zolt Team"),
                                "https://example.com/library.git",
                                "https://example.com/library/issues"),
                        Map.of("Automatic-Module-Name", "com.example.library")),
                model.packageInfo());
        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"package\": {"));
        assertTrue(json.contains("\"sourcesJar\": \""));
        assertTrue(json.contains("\"metadata\": {"));
        assertTrue(json.contains("\"developers\": ["));
        assertTrue(json.contains("\"manifestAttributes\": {"));
        assertTrue(json.contains("\"Automatic-Module-Name\": \"com.example.library\""));
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
    void unsafeConfiguredPathsBecomeDiagnosticsInsteadOfIdeModelPaths() throws IOException {
        Path projectDir = tempDir.resolve("unsafe-paths");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "../outside-artifact"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [build]
                source = "../outside-source"
                output = "../outside-classes"

                [resources]
                main = ["../outside-resources"]

                [generated.main.api]
                kind = "declared-root"
                language = "java"
                output = "../outside-generated"
                inputs = ["../outside-openapi.yaml"]
                required = false
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));
        String json = new IdeModelJsonWriter().write(model);

        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[build].source")));
        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[build].output")));
        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[resources].main")));
        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[generated.main.api].output")));
        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[generated.main.api].inputs")));
        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PROJECT_PATH_INVALID")
                        && diagnostic.message().contains("[project].name")));
        assertFalse(model.sourceRoots().stream()
                .anyMatch(root -> root.path().startsWith(tempDir.resolve("outside-source"))));
        assertFalse(model.generatedSources().stream()
                .anyMatch(source -> source.output().startsWith(tempDir.resolve("outside-generated"))));
        assertFalse(model.resourceRoots().stream()
                .anyMatch(root -> root.path().startsWith(tempDir.resolve("outside-resources"))));
        assertEquals(null, model.outputs().mainClasses());
        assertEquals(null, model.outputs().packagePath());
        assertEquals(null, model.packageInfo().mainJar());
        assertTrue(json.contains("\"mainClasses\": null"));
        assertTrue(json.contains("\"package\": null"));
        assertTrue(json.contains("\"mainJar\": null"));
        assertTrue(json.contains("\"code\": \"PROJECT_PATH_INVALID\""));
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
