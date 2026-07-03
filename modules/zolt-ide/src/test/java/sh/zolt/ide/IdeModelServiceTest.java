package sh.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.resolve.ResolveService;
import sh.zolt.toml.ZoltTomlParser;
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
    void exportsNullSafeModelWhenProjectConfigCannotBeRead() throws IOException {
        Path projectDir = tempDir.resolve("missing-config");
        Files.createDirectories(projectDir);

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"));

        assertNull(model.project().name());
        assertNull(model.java().version());
        assertEquals(List.of(), model.sourceRoots());
        assertEquals(List.of(), model.generatedSources());
        assertEquals(List.of(), model.resourceRoots());
        assertEquals(List.of(), model.dependencies().implementation());
        assertEquals(List.of(), model.classpaths().compile());
        assertEquals("CONFIG_UNREADABLE", model.diagnostics().getFirst().code());
        assertTrue(model.diagnostics().getFirst().message().contains("Could not read zolt.toml"));
        assertEquals("Fix zolt.toml and run zolt ide model --format json again.", model.diagnostics().getFirst().nextStep());
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
    void checkLockSucceedsForFreshEmptyLockfileWithoutDiagnostics() throws IOException {
        Path projectDir = tempDir.resolve("fresh-lock");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "fresh-lock"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);
        new ResolveService().resolve(
                projectDir,
                new ZoltTomlParser().parse(projectDir.resolve("zolt.toml")),
                tempDir.resolve("cache"),
                false,
                true);
        String freshLockfile = Files.readString(projectDir.resolve("zolt.lock"));

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"), true, true, null);

        assertEquals(List.of(), model.diagnostics());
        assertEquals(freshLockfile, Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void checkLockReportsPolicyFailureWithoutRefreshingLockfile() throws IOException {
        Path projectDir = tempDir.resolve("policy-failure");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "policy-failure"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:blocked" = "1.0.0"

                [dependencyPolicy]
                exclude = [{ group = "com.example", artifact = "blocked", reason = "fixture" }]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        IdeModel model = service.export(projectDir, tempDir.resolve("cache"), true, true);

        IdeModel.Diagnostic diagnostic = model.diagnostics().getFirst();
        assertEquals("LOCKFILE_CHECK_FAILED", diagnostic.code());
        assertTrue(diagnostic.message().contains("Dependency policy excludes direct dependency `com.example:blocked`"));
        assertEquals(projectDir.resolve("zolt.lock").toAbsolutePath().normalize(), diagnostic.path());
        assertEquals("Run zolt resolve.", diagnostic.nextStep());
        assertEquals("version = 1\n", Files.readString(projectDir.resolve("zolt.lock")));
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
