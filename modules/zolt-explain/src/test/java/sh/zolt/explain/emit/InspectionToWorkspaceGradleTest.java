package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.gradle.GradleInspectionResult;
import sh.zolt.explain.gradle.GradleStaticProjectInspector;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** : a Gradle multi-project build emits a Zolt workspace, not just the root. */
final class InspectionToWorkspaceGradleTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    private DraftWorkspace emitMultiProject() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), """
                rootProject.name = 'sales'
                include 'app', 'core'
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.createDirectories(tempDir.resolve("gradle"));
        Files.writeString(tempDir.resolve("gradle/libs.versions.toml"), """
                [versions]
                guava = "33.4.8-jre"
                junit = "5.11.4"
                commonsLang = "3.17.0"

                [libraries]
                guava = { module = "com.google.guava:guava", version.ref = "guava" }
                junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit" }
                commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commonsLang" }
                """);
        writeModule("app", """
                plugins {
                    id 'java'
                    id 'application'
                }
                dependencies {
                    api project(':core')
                    implementation libs.guava
                    implementation project(':core')
                    testImplementation libs.junit.jupiter
                    testImplementation project(':core')
                }
                """);
        writeModule("core", """
                plugins { id 'java-library' }
                dependencies {
                    api libs.commons.lang3
                }
                """);
        GradleInspectionResult result = new GradleStaticProjectInspector().inspect(tempDir);
        DraftEmit emit = mapper.emitFromGradle(result);
        return assertInstanceOf(DraftWorkspace.class, emit, () -> "multi-project must emit a workspace, got " + emit);
    }

    private void writeModule(String name, String buildGradle) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("build.gradle"), buildGradle);
    }

    @Test
    void multiProjectEmitsWorkspaceRootWithMembers() throws IOException {
        DraftWorkspace workspace = emitMultiProject();

        assertEquals("sales", workspace.workspace().name());
        assertEquals(List.of("app", "core"), workspace.workspace().members());
        assertEquals(List.of("app", "core"), workspace.workspace().defaultMembers());
        assertEquals(2, workspace.members().size());
    }

    @Test
    void membersResolveCatalogRefsToConcreteVersions() throws IOException {
        DraftWorkspace workspace = emitMultiProject();

        ProjectConfig app = member(workspace, "app");
        assertEquals("33.4.8-jre", app.dependencies().get("com.google.guava:guava"),
                () -> "app catalog ref must resolve: " + app.dependencies());
        assertEquals("5.11.4", app.testDependencies().get("org.junit.jupiter:junit-jupiter"),
                () -> "app test catalog ref must resolve: " + app.testDependencies());

        ProjectConfig core = member(workspace, "core");
        assertEquals("3.17.0", core.apiDependencies().get("org.apache.commons:commons-lang3"),
                () -> "core api catalog ref must resolve: " + core.apiDependencies());
    }

    @Test
    void projectEdgeBecomesWorkspaceEdge() throws IOException {
        DraftWorkspace workspace = emitMultiProject();

        DraftZoltToml appDraft = memberDraft(workspace, "app");
        ProjectConfig app = appDraft.config();
        Map<String, String> workspaceDeps = app.workspaceDependencies();
        assertEquals("core", app.workspaceApiDependencies().get("com.example:core"),
                () -> "api project(':core') must key by the target member coordinate: "
                        + app.workspaceApiDependencies());
        assertEquals("core", workspaceDeps.get("com.example:core"),
                () -> "project(':core') must key by the target member coordinate: " + workspaceDeps);
        assertEquals("core", app.workspaceTestDependencies().get("com.example:core"),
                () -> "test project(':core') must key by the target member coordinate: "
                        + app.workspaceTestDependencies());
        assertFalse(
                app.dependencies().containsKey(":core")
                        || app.dependencies().containsKey("core")
                        || app.dependencies().containsKey("com.example:core"),
                () -> "project edge must not be emitted as an external coordinate: " + app.dependencies());
        assertTrue(
                appDraft.notes().stream().noneMatch(note -> note.contains("could not be resolved")),
                () -> "project edge must not leave an unresolved-notation note: " + appDraft.notes());
    }

    @Test
    void projectEdgeKeyUsesTargetMemberGroupWhenReadable() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), """
                rootProject.name = 'sales'
                include 'app', 'core'
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        writeModule("app", """
                plugins { id 'java-library' }
                dependencies {
                    implementation project(':core')
                }
                """);
        writeModule("core", """
                plugins { id 'java-library' }
                group = 'com.acme.sales'
                """);

        GradleInspectionResult result = new GradleStaticProjectInspector().inspect(tempDir);
        DraftWorkspace workspace = assertInstanceOf(DraftWorkspace.class, mapper.emitFromGradle(result));
        ProjectConfig app = member(workspace, "app");

        assertEquals("core", app.workspaceDependencies().get("com.acme.sales:core"),
                () -> "edge key must use the target member's emitted group:name: "
                        + app.workspaceDependencies());
        assertFalse(app.workspaceDependencies().containsKey("com.example:core"),
                () -> "real target group must not be replaced by the placeholder: "
                        + app.workspaceDependencies());
    }

    @Test
    void workspaceEmitNotesIncludedProjectsSkippedByUnresolvedBuildFileNameAssignment() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), """
                rootProject.name = "renamed"
                include("app", "core")

                rootProject.children.forEach { project ->
                    project.buildFileName = providers.gradleProperty(project.name).get()
                }
                """);
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { java }\n");
        Path app = tempDir.resolve("app");
        Files.createDirectories(app);
        Files.writeString(app.resolve("app.gradle.kts"), "plugins { java }\n");
        writeModule("core", "plugins { id 'java-library' }\n");

        GradleInspectionResult result = new GradleStaticProjectInspector().inspect(tempDir);
        DraftEmit emit = mapper.emitFromGradle(result);
        DraftWorkspace workspace = assertInstanceOf(DraftWorkspace.class, emit);

        assertEquals(List.of("core"), workspace.workspace().members());
        assertEquals(List.of("core"), workspace.members().stream().map(DraftWorkspace.Member::path).toList());
        assertTrue(workspace.notes().stream()
                .anyMatch(note -> note.contains("app")
                        && note.contains("not emitted")
                        && note.contains("explain signals")));
    }

    private static ProjectConfig member(DraftWorkspace workspace, String path) {
        return memberDraft(workspace, path).config();
    }

    private static DraftZoltToml memberDraft(DraftWorkspace workspace, String path) {
        return workspace.members().stream()
                .filter(member -> member.path().equals(path))
                .map(DraftWorkspace.Member::draft)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no member at " + path + " in " + workspace.members()));
    }
}
