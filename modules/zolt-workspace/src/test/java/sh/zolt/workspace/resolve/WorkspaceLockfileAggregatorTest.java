package sh.zolt.workspace.resolve;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockConflict;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.LockPolicyEffect;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.resolve.ResolveException;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.WorkspaceConfig;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceProjectEdge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceLockfileAggregatorTest {
    @TempDir
    private Path tempDir;

    @Test
    void preservesSingleProjectLockfileForTransitionalRootWorkspace() {
        ZoltLockfile memberLockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                Optional.empty(),
                Optional.of("sha256:project"),
                List.of("repositories=sha256:repo"),
                List.of(new LockPackage(
                        new PackageId("com.example", "app"),
                        "1.0.0",
                        "central",
                        DependencyScope.COMPILE,
                        true,
                        Optional.of("com/example/app/1.0.0/app-1.0.0.jar"),
                        Optional.of("com/example/app/1.0.0/app-1.0.0.pom"),
                        Optional.of("jar-sha"),
                        Optional.of("pom-sha"),
                        List.of())),
                List.of(),
                List.of());
        Workspace workspace = new Workspace(
                Path.of("/repo"),
                Path.of("/repo/zolt-workspace.toml"),
                new WorkspaceConfig("zolt", List.of("."), List.of("."), Map.of(), Map.of()),
                List.of(new WorkspaceMember(".", Path.of("/repo"), null)));

        ZoltLockfile aggregated = new WorkspaceLockfileAggregator().aggregate(
                workspace,
                List.of(new WorkspaceMemberResolveOutput(".", memberLockfile, Set.of())));

        assertSame(memberLockfile, aggregated);
    }

    @Test
    void aggregatesWorkspaceAndExternalPackagesDeterministically() throws IOException {
        Workspace workspace = workspace(
                List.of(
                        new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core", true),
                        new WorkspaceProjectEdge("apps/worker", "modules/core", "compile", "com.acme:core"),
                        new WorkspaceProjectEdge("apps/api", "modules/processor", "processor", "com.acme:processor")));
        PackageId library = new PackageId("com.example", "library");
        LockPolicyEffect policyEffect = new LockPolicyEffect(
                "allow",
                library,
                Optional.of("1.0.0"),
                Optional.of("central"),
                "enterprise-baseline");

        ZoltLockfile aggregated = new WorkspaceLockfileAggregator().aggregate(
                workspace,
                List.of(
                        new WorkspaceMemberResolveOutput(
                                "apps/api",
                                lockfile(
                                        List.of(externalPackage(
                                                library,
                                                "1.0.0",
                                                true,
                                                List.of("com.example:transitive:0.9.0"),
                                                List.of("api-policy"))),
                                        List.of(new LockConflict(
                                                library,
                                                "1.0.0",
                                                List.of("1.0.0", "2.0.0"),
                                                ConflictSelectionReason.DIRECT_DEPENDENCY)),
                                        List.of(policyEffect)),
                                Set.of(library)),
                        new WorkspaceMemberResolveOutput(
                                "apps/worker",
                                lockfile(
                                        List.of(externalPackage(
                                                library,
                                                "2.0.0",
                                                true,
                                                List.of("com.example:transitive:1.0.0"),
                                                List.of("worker-policy"))),
                                        List.of(),
                                        List.of(policyEffect)),
                                Set.of())));

        assertEquals(
                List.of(
                        "com.acme:core:workspace:compile",
                        "com.acme:processor:workspace:processor",
                        "com.example:library:central:compile"),
                aggregated.packages().stream()
                        .map(WorkspaceLockfileAggregatorTest::packageSummary)
                        .toList());
        LockPackage core = packageById(aggregated, "com.acme", "core");
        assertEquals(List.of("apps/api", "apps/worker"), core.members());
        assertEquals(List.of("apps/api"), core.exportedBy());
        assertEquals("modules/core", core.workspace().orElseThrow());
        assertEquals("target/classes", core.workspaceOutput().orElseThrow());
        LockPackage processor = packageById(aggregated, "com.acme", "processor");
        assertEquals(DependencyScope.PROCESSOR, processor.scope());
        assertEquals(List.of("apps/api"), processor.members());
        LockPackage external = packageById(aggregated, "com.example", "library");
        assertEquals("2.0.0", external.version());
        assertEquals(List.of("com.example:transitive:1.0.0"), external.dependencies());
        assertEquals(List.of("apps/api", "apps/worker"), external.members());
        assertEquals(List.of("apps/api"), external.exportedBy());
        assertEquals(List.of("worker-policy"), external.policies());
        assertEquals(
                List.of(
                        new LockConflict(
                                library,
                                "1.0.0",
                                List.of("1.0.0", "2.0.0"),
                                ConflictSelectionReason.DIRECT_DEPENDENCY),
                        new LockConflict(
                                library,
                                "2.0.0",
                                List.of("1.0.0", "2.0.0"),
                                ConflictSelectionReason.DIRECT_DEPENDENCY)),
                aggregated.conflicts());
        assertEquals(List.of(policyEffect), aggregated.policyEffects());
    }

    @Test
    void rejectsUnsupportedWorkspaceDependencyScope() throws IOException {
        Workspace workspace = workspace(List.of(
                new WorkspaceProjectEdge("apps/api", "modules/core", "custom", "com.acme:core")));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> new WorkspaceLockfileAggregator().aggregate(
                        workspace,
                        List.of(new WorkspaceMemberResolveOutput(
                                "apps/api",
                                new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.of(), List.of()),
                                Set.of()))));

        assertEquals("Unsupported workspace dependency scope `custom`.", exception.getMessage());
    }

    private Workspace workspace(List<WorkspaceProjectEdge> edges) throws IOException {
        Files.createDirectories(tempDir.resolve("apps/api"));
        Files.createDirectories(tempDir.resolve("apps/worker"));
        Files.createDirectories(tempDir.resolve("modules/core"));
        Files.createDirectories(tempDir.resolve("modules/processor"));
        List<WorkspaceMember> members = List.of(
                new WorkspaceMember("apps/api", tempDir.resolve("apps/api"), config("api")),
                new WorkspaceMember("apps/worker", tempDir.resolve("apps/worker"), config("worker")),
                new WorkspaceMember("modules/core", tempDir.resolve("modules/core"), config("core")),
                new WorkspaceMember("modules/processor", tempDir.resolve("modules/processor"), config("processor")));
        return new Workspace(
                tempDir,
                tempDir.resolve("zolt-workspace.toml"),
                new WorkspaceConfig(
                        "acme-platform",
                        members.stream().map(WorkspaceMember::path).toList(),
                        List.of(),
                        Map.of(),
                        Map.of()),
                members,
                edges,
                List.of("modules/core", "modules/processor", "apps/api", "apps/worker"));
    }

    private static ZoltLockfile lockfile(
            List<LockPackage> packages,
            List<LockConflict> conflicts,
            List<LockPolicyEffect> policyEffects) {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                Optional.empty(),
                Optional.of("sha256:project"),
                List.of("repositories=sha256:repo"),
                packages,
                conflicts,
                policyEffects);
    }

    private static LockPackage externalPackage(
            PackageId packageId,
            String version,
            boolean direct,
            List<String> dependencies,
            List<String> policies) {
        return new LockPackage(
                packageId,
                version,
                "central",
                DependencyScope.COMPILE,
                direct,
                Optional.of(packageId.groupId() + "/" + packageId.artifactId() + "/" + version + ".jar"),
                Optional.of(packageId.groupId() + "/" + packageId.artifactId() + "/" + version + ".pom"),
                Optional.of("jar-" + version),
                Optional.of("pom-" + version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies,
                policies);
    }

    private static LockPackage packageById(ZoltLockfile lockfile, String group, String artifact) {
        PackageId packageId = new PackageId(group, artifact);
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .findFirst()
                .orElseThrow();
    }

    private static String packageSummary(LockPackage lockPackage) {
        return lockPackage.packageId()
                + ":"
                + lockPackage.source()
                + ":"
                + lockPackage.scope().lockfileName();
    }

    private static ProjectConfig config(String name) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata(name, "0.1.0", "com.acme", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }
}
