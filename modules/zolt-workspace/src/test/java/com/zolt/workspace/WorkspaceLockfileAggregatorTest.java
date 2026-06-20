package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorkspaceLockfileAggregatorTest {
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
}
