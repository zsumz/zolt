package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelRootsServiceTest {
    private final IdeModelService service = new IdeModelService();

    @TempDir
    private Path tempDir;

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
    void exportsOpenApiToolVersionRefForGeneratedSourceEvidence() throws IOException {
        Path projectDir = tempDir.resolve("openapi-tool-version-ref");
        Path input = projectDir.resolve("src/main/openapi/public-api.yaml");
        Path output = projectDir.resolve("target/generated/sources/openapi/public-api/com/example/PublicApi.java");
        Files.createDirectories(input.getParent());
        Files.createDirectories(output.getParent());
        Files.writeString(input, "openapi: 3.1.0\n");
        Files.writeString(output, "package com.example; public final class PublicApi {}\n");
        Files.setLastModifiedTime(input, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(output, FileTime.fromMillis(2_000));
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "openapi-tool-version-ref"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                openapi = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        IdeModel.GeneratedSourceInfo generatedSource = model.generatedSources().getFirst();
        assertEquals("generated-main-public-api", generatedSource.id());
        assertEquals("zolt-owned-openapi", generatedSource.ownership());
        assertEquals("org.openapitools:openapi-generator-cli:7.11.0", generatedSource.toolArtifact());
        assertEquals("openapi", generatedSource.toolVersionRef());

        String json = new IdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"toolArtifact\": \"org.openapitools:openapi-generator-cli:7.11.0\""));
        assertTrue(json.contains("\"toolVersionRef\": \"openapi\""));
    }

}
