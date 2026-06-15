package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

}
