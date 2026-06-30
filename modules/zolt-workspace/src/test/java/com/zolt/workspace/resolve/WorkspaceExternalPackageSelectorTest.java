package com.zolt.workspace.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WorkspaceExternalPackageSelectorTest {
    private final WorkspaceExternalPackageSelector selector = new WorkspaceExternalPackageSelector();

    @Test
    void directDependencyVersionWinsOverNewerTransitiveRequest() {
        PackageId packageId = new PackageId("com.example", "lib");

        WorkspaceExternalSelection selection = selector.select(List.of(
                jarPackage(packageId, "2.0.0", DependencyScope.COMPILE, false, List.of(), List.of("worker"), List.of()),
                jarPackage(packageId, "1.0.0", DependencyScope.COMPILE, true, List.of(), List.of("api"), List.of())));

        LockPackage selectedPackage = selection.packages().getFirst();
        assertEquals(packageId, selectedPackage.packageId());
        assertEquals("1.0.0", selectedPackage.version());
        assertTrue(selectedPackage.direct());
        assertEquals(List.of("api", "worker"), selectedPackage.members());

        LockConflict conflict = selection.conflicts().getFirst();
        assertEquals(packageId, conflict.packageId());
        assertEquals("1.0.0", conflict.selectedVersion());
        assertEquals(List.of("1.0.0", "2.0.0"), conflict.requestedVersions());
        assertEquals(ConflictSelectionReason.DIRECT_DEPENDENCY, conflict.reason());
    }

    @Test
    void newestVersionWinsAndDependenciesAreRewritten() {
        PackageId app = new PackageId("com.example", "app");
        PackageId lib = new PackageId("com.example", "lib");

        WorkspaceExternalSelection selection = selector.select(List.of(
                jarPackage(
                        app,
                        "1.0.0",
                        DependencyScope.COMPILE,
                        true,
                        List.of("com.example:lib:1.0.0", "bad-coordinate"),
                        List.of("api"),
                        List.of("api")),
                jarPackage(lib, "1.0.0", DependencyScope.COMPILE, false, List.of(), List.of("api"), List.of()),
                jarPackage(lib, "2.0.0", DependencyScope.COMPILE, false, List.of(), List.of("worker"), List.of())));

        LockPackage appPackage = packageById(selection, app);
        assertEquals(List.of("bad-coordinate", "com.example:lib:2.0.0"), appPackage.dependencies());

        LockPackage libPackage = packageById(selection, lib);
        assertEquals("2.0.0", libPackage.version());
        assertEquals(List.of("api", "worker"), libPackage.members());

        LockConflict conflict = selection.conflicts().getFirst();
        assertEquals(lib, conflict.packageId());
        assertEquals(ConflictSelectionReason.NEWEST_VERSION, conflict.reason());
    }

    @Test
    void preservesNonJarArtifactFieldsAndPolicies() {
        PackageId packageId = new PackageId("io.quarkus.platform", "platform-properties");

        WorkspaceExternalSelection selection = selector.select(List.of(new LockPackage(
                packageId,
                "3.33.0",
                "maven-central",
                DependencyScope.QUARKUS_DEPLOYMENT,
                false,
                Optional.empty(),
                Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.pom"),
                Optional.empty(),
                Optional.of("pom-sha"),
                Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.properties"),
                Optional.of("properties"),
                Optional.of("artifact-sha"),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of("api"),
                List.of(),
                List.of("managed"))));

        LockPackage selectedPackage = selection.packages().getFirst();
        assertEquals(Optional.empty(), selectedPackage.jar());
        assertEquals(Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.properties"), selectedPackage.artifact());
        assertEquals(Optional.of("properties"), selectedPackage.artifactType());
        assertEquals(Optional.of("artifact-sha"), selectedPackage.artifactSha256());
        assertEquals(List.of("managed"), selectedPackage.policies());
    }

    private static LockPackage packageById(WorkspaceExternalSelection selection, PackageId packageId) {
        return selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .findFirst()
                .orElseThrow();
    }

    private static LockPackage jarPackage(
            PackageId packageId,
            String version,
            DependencyScope scope,
            boolean direct,
            List<String> dependencies,
            List<String> members,
            List<String> exportedBy) {
        String base = packageId.groupId().replace('.', '/')
                + "/"
                + packageId.artifactId()
                + "/"
                + version
                + "/"
                + packageId.artifactId()
                + "-"
                + version;
        return new LockPackage(
                packageId,
                version,
                "maven-central",
                scope,
                direct,
                Optional.of(base + ".jar"),
                Optional.of(base + ".pom"),
                Optional.of("jar-" + version),
                Optional.of("pom-" + version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies,
                members,
                exportedBy,
                List.of());
    }
}
