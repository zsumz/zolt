package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.policy.DependencyPolicyReportService;
import sh.zolt.project.ProjectConfig;
import sh.zolt.workspace.WorkspaceConfig;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceProjectEdge;
import sh.zolt.workspace.service.WorkspaceSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DependencyQualityCheckWorkspaceMetadataTest extends QualityCheckServiceTestSupport {
    private final DependencyQualityCheck check = new DependencyQualityCheck(
            new ZoltLockfileReader(),
            new DependencyPolicyReportService());

    @TempDir
    private Path tempDir;

    @Test
    void workspaceMetadataRequiresWorkspaceLockfileBeforeMemberChecks() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("");

        QualityCheckResult result = check.checkWorkspaceMetadata(
                workspace(fixture.members(), List.of()),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath()).getFirst();

        assertResult(
                result,
                QualityCheckService.DEPENDENCY_METADATA,
                QualityCheckStatus.FAILED,
                "zolt.lock",
                "Workspace zolt.lock is missing.",
                "Run `zolt resolve --workspace`.");
        assertEquals(Optional.empty(), result.member());
    }

    @Test
    void workspaceMetadataReportsMalformedLockfileWithWorkspaceResolveAction() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("");
        writeLockfile(tempDir, """

                [[package]]
                id = 42
                """);

        QualityCheckResult result = check.checkWorkspaceMetadata(
                workspace(fixture.members(), List.of()),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath()).getFirst();

        assertEquals(QualityCheckService.DEPENDENCY_METADATA, result.id());
        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("zolt.lock", result.subject());
        assertTrue(result.message().contains("Invalid value type in zolt.lock"));
        assertEquals("Run `zolt resolve --workspace`.", result.nextStep());
        assertEquals(Optional.empty(), result.member());
    }

    @Test
    void workspaceMetadataFiltersLockPackagesByMemberOwnership() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("""

                [dependencies]
                "com.example:helper" = { version = "1.0.0", optional = true }
                """);
        writeLockfile(tempDir, packageEntry(
                "com.example:helper",
                "1.0.0",
                "compile",
                true,
                "members = [\"modules/core\"]"));

        QualityCheckResult result = check.checkWorkspaceMetadata(
                workspace(fixture.members(), List.of()),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath()).getFirst();

        assertResult(
                result,
                QualityCheckService.DEPENDENCY_METADATA,
                QualityCheckStatus.FAILED,
                "com.example:helper",
                "Dependency metadata for `com.example:helper` is not represented in zolt.lock.",
                "Run `zolt resolve --workspace`.");
        assertEquals(Optional.of("apps/api"), result.member());
    }

    @Test
    void workspaceMetadataPassesForExportedApiEdgesAndLockOwnership() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("""

                [api.dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        writeLockfile(tempDir, workspacePackageEntry("com.example:core", true, "exportedBy = [\"apps/api\"]"));

        List<QualityCheckResult> results = check.checkWorkspaceMetadata(
                workspace(
                        fixture.members(),
                        List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "api", "com.example:core", true))),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath());

        assertEquals(List.of(
                        "api|No dependency metadata declarations require validation.",
                        "com.example:core|Workspace API dependency `com.example:core` is exported through zolt.lock."),
                results.stream()
                        .map(result -> result.subject() + "|" + result.message())
                        .toList());
        assertEquals(QualityCheckStatus.PASSED, results.get(1).status());
        assertEquals(Optional.of("apps/api"), results.get(1).member());
    }

    @Test
    void workspaceMetadataRejectsApiDependencyWhenWorkspaceEdgeIsNotExported() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("""

                [api.dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        writeLockfile(tempDir, workspacePackageEntry("com.example:core", true, "exportedBy = [\"apps/api\"]"));

        List<QualityCheckResult> results = check.checkWorkspaceMetadata(
                workspace(
                        fixture.members(),
                        List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "api", "com.example:core", false))),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath());
        QualityCheckResult result = results.stream()
                .filter(candidate -> candidate.status() == QualityCheckStatus.FAILED)
                .findFirst()
                .orElseThrow();

        assertResult(
                result,
                QualityCheckService.DEPENDENCY_METADATA,
                QualityCheckStatus.FAILED,
                "com.example:core",
                "Workspace API dependency `com.example:core` is not represented as an exported workspace edge.",
                "Keep public workspace dependencies in [api.dependencies] and run `zolt resolve --workspace`.");
        assertEquals(Optional.of("apps/api"), result.member());
    }

    @Test
    void workspaceMetadataRejectsApiDependencyWhenLockfileExportedByIsMissing() throws IOException {
        WorkspaceFixture fixture = workspaceFixture("""

                [api.dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        writeLockfile(tempDir, workspacePackageEntry("com.example:core", true, ""));

        List<QualityCheckResult> results = check.checkWorkspaceMetadata(
                workspace(
                        fixture.members(),
                        List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "api", "com.example:core", true))),
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                fixture.membersByPath());
        QualityCheckResult result = results.stream()
                .filter(candidate -> candidate.status() == QualityCheckStatus.FAILED)
                .findFirst()
                .orElseThrow();

        assertResult(
                result,
                QualityCheckService.DEPENDENCY_METADATA,
                QualityCheckStatus.FAILED,
                "com.example:core",
                "Workspace API dependency `com.example:core` is missing exportedBy ownership in zolt.lock.",
                "Run `zolt resolve --workspace`.");
        assertEquals(Optional.of("apps/api"), result.member());
    }

    private static String packageEntry(
            String coordinate,
            String version,
            String scope,
            boolean direct,
            String extra) {
        String jarName = coordinate.replace(':', '-') + "-" + version + ".jar";
        String dependencies = extra.contains("dependencies") ? "" : "dependencies = []";
        return """

                [[package]]
                id = "%s"
                version = "%s"
                source = "maven-central"
                scope = "%s"
                direct = %s
                jar = "com/example/%s/%s/%s"
                %s
                %s
                """.formatted(
                coordinate,
                version,
                scope,
                direct,
                coordinate.substring(coordinate.indexOf(':') + 1),
                version,
                jarName,
                extra,
                dependencies);
    }

    private static String workspacePackageEntry(
            String coordinate,
            boolean direct,
            String extra) {
        String jarName = coordinate.replace(':', '-') + "-0.1.0.jar";
        return """

                [[package]]
                id = "%s"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = %s
                jar = "modules/core/target/%s"
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                members = ["apps/api"]
                dependencies = []
                %s
                """.formatted(coordinate, direct, jarName, extra);
    }

    private WorkspaceFixture workspaceFixture(String apiBody) throws IOException {
        Path apiDir = tempDir.resolve("apps/api");
        Path coreDir = tempDir.resolve("modules/core");
        ProjectConfig api = parseProject(apiDir, apiBody);
        ProjectConfig core = parseProject(coreDir, "");
        List<WorkspaceMember> members = List.of(
                new WorkspaceMember("apps/api", apiDir, api),
                new WorkspaceMember("modules/core", coreDir, core));
        return new WorkspaceFixture(members, Map.of(
                "apps/api", members.get(0),
                "modules/core", members.get(1)));
    }

    private Workspace workspace(List<WorkspaceMember> members, List<WorkspaceProjectEdge> edges) {
        return new Workspace(
                tempDir,
                tempDir.resolve("zolt-workspace.toml"),
                new WorkspaceConfig("demo", List.of("apps/api", "modules/core"), List.of(), Map.of(), Map.of()),
                members,
                edges);
    }

    private static void assertResult(
            QualityCheckResult result,
            String id,
            QualityCheckStatus status,
            String subject,
            String message,
            String nextStep) {
        assertEquals(id, result.id());
        assertEquals(status, result.status());
        assertEquals(subject, result.subject());
        assertEquals(message, result.message());
        assertEquals(nextStep, result.nextStep());
    }

    private record WorkspaceFixture(
            List<WorkspaceMember> members,
            Map<String, WorkspaceMember> membersByPath) {
    }
}
