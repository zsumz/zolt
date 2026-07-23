package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.workspace.WorkspaceConfig;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceRepositoryUploaderTest {
    private final WorkspaceRepositoryUploader uploader = new WorkspaceRepositoryUploader();

    @Test
    void writesArtifactPomAndChecksumsToAFileRepositoryInMavenLayout(@TempDir Path tempDir) throws IOException {
        Path workspaceRoot = tempDir.resolve("ws");
        Path memberRoot = workspaceRoot.resolve("acme-core");
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(memberRoot.resolve("target/acme-core-1.0.0.jar"), "jar-bytes");
        Files.writeString(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        Path repository = tempDir.resolve("repo");
        Files.createDirectories(repository);

        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.acme:acme-core:1.0.0",
                "release",
                "local",
                repository.toUri().toString(),
                "main",
                Path.of("target/acme-core-1.0.0.jar"),
                "sha256:jar",
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar",
                List.of(),
                Path.of("target/publish/acme-core-1.0.0.pom"),
                Path.of("target/publish/acme-core-1.0.0.pom"),
                "sha256:pom",
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.pom",
                List.of(),
                "",
                List.of(),
                false);
        Workspace workspace = new Workspace(
                workspaceRoot,
                workspaceRoot.resolve("zolt.toml"),
                new WorkspaceConfig("ws", List.of("acme-core"), List.of(), Map.of(), Map.of()),
                List.of(new WorkspaceMember("acme-core", memberRoot, null)));

        WorkspacePublishReport report = uploader.upload(
                workspace,
                List.of(new WorkspacePublishReport.Member("acme-core", "com.acme:acme-core:1.0.0", false, plan)),
                new WorkspacePublishService.Options(false, false, false, false));

        assertTrue(report.ok());
        assertTrue(report.uploaded());
        Path base = repository.resolve("com/acme/acme-core/1.0.0");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.pom")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar.sha1")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.pom.sha256")));
        assertEquals(40, Files.readString(base.resolve("acme-core-1.0.0.jar.sha1")).length());
        assertFalse(report.resumeCommand().isPresent());
    }

    @Test
    void pomOnlyMemberWritesOnlyThePom(@TempDir Path tempDir) throws IOException {
        Path workspaceRoot = tempDir.resolve("ws");
        Path memberRoot = workspaceRoot.resolve("acme-bom");
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(memberRoot.resolve("target/publish/acme-bom-1.0.0.pom"), "<project><packaging>pom</packaging></project>");
        Path repository = tempDir.resolve("repo");

        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.acme.platform:acme-bom:1.0.0",
                "release",
                "local",
                repository.toUri().toString(),
                "bom",
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                "sha256:pom",
                "",
                List.of(),
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                "sha256:pom",
                "com/acme/platform/acme-bom/1.0.0/acme-bom-1.0.0.pom",
                List.of(),
                "",
                List.of(),
                true);
        Workspace workspace = new Workspace(
                workspaceRoot,
                workspaceRoot.resolve("zolt.toml"),
                new WorkspaceConfig("ws", List.of("acme-bom"), List.of(), Map.of(), Map.of()),
                List.of(new WorkspaceMember("acme-bom", memberRoot, null)));

        WorkspacePublishReport report = uploader.upload(
                workspace,
                List.of(new WorkspacePublishReport.Member("acme-bom", "com.acme.platform:acme-bom:1.0.0", true, plan)),
                new WorkspacePublishService.Options(false, false, false, false));

        assertTrue(report.ok());
        Path base = repository.resolve("com/acme/platform/acme-bom/1.0.0");
        assertTrue(Files.exists(base.resolve("acme-bom-1.0.0.pom")));
        assertFalse(Files.exists(base.resolve("acme-bom-1.0.0.jar")));
    }
}
