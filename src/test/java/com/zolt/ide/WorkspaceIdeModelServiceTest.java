package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceIdeModelServiceTest {
    private final WorkspaceIdeModelService service = new WorkspaceIdeModelService();

    @TempDir
    private Path tempDir;

    @Test
    void exportsWorkspaceAndMemberProjectModels() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core");

        WorkspaceIdeModel model = service.export(
                tempDir.resolve("apps/api/src/main/java"),
                tempDir.resolve("cache"),
                false,
                false);

        assertEquals(1, model.schemaVersion());
        assertEquals("acme-platform", model.workspace().name());
        assertEquals(tempDir.toAbsolutePath().normalize(), model.workspace().root());
        assertEquals(tempDir.resolve("zolt-workspace.toml").toAbsolutePath().normalize(), model.workspace().config());
        assertEquals(tempDir.resolve("zolt.lock").toAbsolutePath().normalize(), model.workspace().lockfile());
        assertEquals(List.of("apps/api", "modules/core"), model.workspace().members());
        assertEquals(List.of("apps/api"), model.workspace().defaultMembers());
        assertEquals(List.of("modules/core", "apps/api"), model.workspace().buildOrder());
        assertEquals(List.of("apps/api", "modules/core"), model.projects().stream()
                .map(WorkspaceIdeModel.ProjectModel::member)
                .toList());
        assertEquals(List.of("api", "core"), model.projects().stream()
                .map(project -> project.model().project().name())
                .toList());
        assertEquals(
                List.of(new WorkspaceIdeModel.ProjectEdge("apps/api", "modules/core", "compile", "com.acme:core")),
                model.edges());
        assertEquals(List.of(), model.diagnostics());
    }

    @Test
    void exportsMemberModelsWithWorkspaceRootLockfileAndClasspaths() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core");
        Files.writeString(tempDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.acme:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                members = ["apps/api"]
                dependencies = []

                [[package]]
                id = "org.projectlombok:lombok"
                version = "1.18.42"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar"
                members = ["apps/api"]
                dependencies = []

                [[package]]
                id = "com.example:test-processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor/1.0.0/test-processor-1.0.0.jar"
                members = ["apps/api"]
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.2"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar"
                members = ["apps/api"]
                dependencies = []
                """);

        WorkspaceIdeModel model = service.export(tempDir, tempDir.resolve("cache"), false, false);

        IdeModel apiModel = model.projects().stream()
                .filter(project -> project.member().equals("apps/api"))
                .findFirst()
                .orElseThrow()
                .model();
        assertEquals(tempDir.resolve("zolt.lock").toAbsolutePath().normalize(), apiModel.paths().lockfile());
        assertTrue(apiModel.classpaths().compile().contains(tempDir
                .resolve("modules/core/target/classes")
                .toAbsolutePath()
                .normalize()));
        assertEquals(
                List.of(tempDir.resolve("cache/org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar")
                        .toAbsolutePath()
                        .normalize()),
                apiModel.classpaths().processor());
        assertEquals(
                List.of(tempDir.resolve("cache/com/example/test-processor/1.0.0/test-processor-1.0.0.jar")
                        .toAbsolutePath()
                        .normalize()),
                apiModel.classpaths().testProcessor());
        assertEquals(
                List.of(tempDir.resolve(
                                "cache/io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar")
                        .toAbsolutePath()
                        .normalize()),
                apiModel.classpaths().quarkusDeployment());
        assertEquals(List.of(), apiModel.diagnostics());
        assertEquals(List.of(), model.diagnostics());
    }

    @Test
    void exportsVersionAliasesInMemberProjectModels() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]
                """);
        member("apps/api", "api", """

                [versions]
                guava = "33.4.8-jre"
                junit = "5.12.1"

                [dependencies]
                "com.google.guava:guava" = { versionRef = "guava" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = { versionRef = "junit" }
                """);

        WorkspaceIdeModel model = service.export(tempDir, tempDir.resolve("cache"), false, false);

        IdeModel apiModel = model.projects().getFirst().model();
        assertEquals(Map.of("guava", "33.4.8-jre", "junit", "5.12.1"), apiModel.dependencies().versionAliases());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration(
                        "com.google.guava:guava",
                        "33.4.8-jre",
                        "guava",
                        false,
                        null,
                        false,
                        false,
                        List.of())),
                apiModel.dependencies().implementation());
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
                apiModel.dependencies().test());

        String json = new WorkspaceIdeModelJsonWriter().write(model);
        assertTrue(json.contains("\"versionAliases\": {"));
        assertTrue(json.contains("\"guava\": \"33.4.8-jre\""));
        assertTrue(json.contains("\"versionRef\": \"guava\""));
        assertTrue(json.contains("\"versionRef\": \"junit\""));
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
        Files.writeString(tempDir.resolve("zolt.lock"), "version = 1\n");
    }

    private void member(String path, String name) throws IOException {
        member(path, name, "");
    }

    private void member(String path, String name, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.acme"
                java = "21"
                %s""".formatted(name, extraToml));
        Files.writeString(member.resolve("zolt.lock"), "version = 1\n");
    }
}
