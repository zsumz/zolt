package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusAugmentationMetadataWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
    void exportsCompilerSettingsForEditors() throws IOException {
        Path projectDir = tempDir.resolve("compiler-settings");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "compiler-settings"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                generatedSources = "build/generated/main"
                generatedTestSources = "build/generated/test"
                release = "17"
                encoding = "UTF-8"
                args = ["-Xlint:deprecation", "-parameters"]
                testArgs = ["-Xlint:unchecked"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(new IdeModel.CompilerInfo(
                "17",
                "17",
                "UTF-8",
                List.of("-Xlint:deprecation", "-parameters"),
                List.of("-Xlint:unchecked"),
                root.resolve("build/generated/main"),
                root.resolve("build/generated/test")),
                model.compiler());
        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"compiler\": {"));
        assertTrue(json.contains("\"effectiveRelease\": \"17\""));
        assertTrue(json.contains("\"args\": ["));
        assertTrue(json.contains("\"testArgs\": ["));
    }

    @Test
    void exportsDeclaredGeneratedSourceRootsForEditors() throws IOException {
        Path projectDir = tempDir.resolve("generated-sources");
        Path mainInput = projectDir.resolve("src/main/openapi/api.yaml");
        Path mainOutput = projectDir.resolve("target/generated/sources/openapi/com/example/GeneratedApi.java");
        Path testInput = projectDir.resolve("src/test/fixtures/schema.json");
        Path testOutput = projectDir.resolve("target/generated/test-sources/fixtures/com/example/GeneratedFixture.java");
        Files.createDirectories(mainInput.getParent());
        Files.createDirectories(mainOutput.getParent());
        Files.createDirectories(testInput.getParent());
        Files.createDirectories(testOutput.getParent());
        Files.writeString(mainInput, "openapi: 3.1.0\n");
        Files.writeString(mainOutput, "package com.example; public final class GeneratedApi {}\n");
        Files.writeString(testInput, "{}\n");
        Files.writeString(testOutput, "package com.example; public final class GeneratedFixture {}\n");
        Files.setLastModifiedTime(mainInput, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(mainOutput, FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(testInput, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(testOutput, FileTime.fromMillis(2_000));
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "generated-sources"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]

                [generated.test.fixtures]
                kind = "declared-root"
                language = "java"
                output = "target/generated/test-sources/fixtures"
                inputs = ["src/test/fixtures/schema.json"]
                required = false
                clean = true
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertTrue(model.sourceRoots().contains(new IdeModel.SourceRoot(
                "generated-main-openapi",
                "main",
                "java",
                root.resolve("target/generated/sources/openapi"),
                true)));
        assertTrue(model.sourceRoots().contains(new IdeModel.SourceRoot(
                "generated-test-fixtures",
                "test",
                "java",
                root.resolve("target/generated/test-sources/fixtures"),
                true)));
        assertEquals(List.of(
                new IdeModel.GeneratedSourceInfo(
                        "generated-main-openapi",
                        "generated-main-openapi",
                        "main",
                        "declared-root",
                        "java",
                        root.resolve("target/generated/sources/openapi"),
                        List.of(root.resolve("src/main/openapi/api.yaml")),
                        true,
                        false,
                        "external-declared-root",
                        "main-compile",
                        "fresh",
                        true,
                        true,
                        "",
                        "",
                        ""),
                new IdeModel.GeneratedSourceInfo(
                        "generated-test-fixtures",
                        "generated-test-fixtures",
                        "test",
                        "declared-root",
                        "java",
                        root.resolve("target/generated/test-sources/fixtures"),
                        List.of(root.resolve("src/test/fixtures/schema.json")),
                        false,
                        true,
                        "zolt-owned-clean",
                        "test-compile",
                        "fresh",
                        true,
                        true,
                        "",
                        "",
                        "")),
                model.generatedSources());
        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"generatedSources\": ["));
        assertTrue(json.contains("\"id\": \"generated-main-openapi\""));
        assertTrue(json.contains("\"id\": \"generated-test-fixtures\""));
        assertTrue(json.contains("\"ownership\": \"external-declared-root\""));
        assertTrue(json.contains("\"compileLane\": \"test-compile\""));
        assertTrue(json.contains("\"freshness\": \"fresh\""));
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
                "com.example:impl" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }

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
                List.of(
                        new IdeModel.DependencyDeclaration(
                                "com.example:impl",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of("com.example:legacy-logging")),
                        new IdeModel.DependencyDeclaration(
                                "com.example:publish-helper",
                                "2.0.0",
                                false,
                                null,
                                false,
                                true,
                                List.of())),
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
        assertTrue(json.contains("\"versionAliases\": {}"));
        assertTrue(json.contains("\"api\": ["));
        assertTrue(json.contains("\"implementation\": ["));
        assertTrue(json.contains("\"runtime\": ["));
        assertTrue(json.contains("\"provided\": ["));
        assertTrue(json.contains("\"dev\": ["));
        assertTrue(json.contains("\"coordinate\": \"com.example:workspace-api\""));
        assertTrue(json.contains("\"publishOnly\": true"));
        assertTrue(json.contains("\"exclusions\": ["));
        assertTrue(json.contains("\"workspace\": \"modules/api\""));
        assertTrue(json.contains("\"versionRef\": null"));
        assertTrue(json.contains("\"managed\": true"));
    }

    @Test
    void exportsVersionAliasesAndDependencyVersionRefs() throws IOException {
        Path projectDir = tempDir.resolve("alias-dependencies");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "alias-dependencies"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                junit = "5.12.1"

                [dependencies]
                "com.google.guava:guava" = { versionRef = "guava", optional = true }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = { versionRef = "junit" }
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        assertEquals(Map.of("guava", "33.4.8-jre", "junit", "5.12.1"), model.dependencies().versionAliases());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration(
                        "com.google.guava:guava",
                        "33.4.8-jre",
                        "guava",
                        false,
                        null,
                        true,
                        false,
                        List.of())),
                model.dependencies().implementation());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration(
                        "org.junit.jupiter:junit-jupiter",
                        "5.12.1",
                        "junit",
                        false,
                        null,
                        false,
                        false,
                        List.of())),
                model.dependencies().test());

        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"versionAliases\": {"));
        assertTrue(json.contains("\"guava\": \"33.4.8-jre\""));
        assertTrue(json.contains("\"versionRef\": \"guava\""));
        assertTrue(json.contains("\"versionRef\": \"junit\""));
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
