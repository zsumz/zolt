package sh.zolt.workspace.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.lockfile.LockConflict;
import sh.zolt.lockfile.LockPackage;
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
                List.of("managed"),
                List.of())));

        LockPackage selectedPackage = selection.packages().getFirst();
        assertEquals(Optional.empty(), selectedPackage.jar());
        assertEquals(Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.properties"), selectedPackage.artifact());
        assertEquals(Optional.of("properties"), selectedPackage.artifactType());
        assertEquals(Optional.of("artifact-sha"), selectedPackage.artifactSha256());
        assertEquals(List.of("managed"), selectedPackage.policies());
    }

    @Test
    void keepsConflictingExecToolVersionsSeparateAndUnionsSameVersionToolGroups() {
        PackageId shared = new PackageId("com.example", "shared-lib");
        PackageId common = new PackageId("com.example", "common-lib");

        WorkspaceExternalSelection selection = selector.select(List.of(
                execJarPackage(shared, "1.0.0", List.of("alpha"), List.of("apps/api")),
                execJarPackage(shared, "2.0.0", List.of("beta"), List.of("apps/worker")),
                execJarPackage(common, "1.0.0", List.of("alpha"), List.of("apps/api")),
                execJarPackage(common, "1.0.0", List.of("beta"), List.of("apps/worker"))));

        // Conflicting versions of the shared library stay distinct, each keyed to its own tool group.
        assertEquals(List.of("alpha"), execEntry(selection, shared, "1.0.0").toolGroups());
        assertEquals(List.of("beta"), execEntry(selection, shared, "2.0.0").toolGroups());
        // A jar shared at the same version collapses into one entry unioning both tool groups.
        assertEquals(List.of("alpha", "beta"), execEntry(selection, common, "1.0.0").toolGroups());
        assertEquals(List.of("apps/api", "apps/worker"), execEntry(selection, common, "1.0.0").members());
        // Divergent tool-exec versions are never mediated into a LockConflict.
        assertEquals(List.of(), selection.conflicts());
    }

    @Test
    void keepsClassifierVariantsOfOneGavAsDistinctLanesWithPerVariantMembers() {
        PackageId epoll = new PackageId("io.netty", "netty-transport-native-epoll");

        WorkspaceExternalSelection selection = selector.select(List.of(
                classifiedJarPackage(epoll, "4.1.100.Final", "linux-x86_64", DependencyScope.RUNTIME, List.of("apps/api")),
                classifiedJarPackage(epoll, "4.1.100.Final", "osx-aarch_64", DependencyScope.RUNTIME, List.of("apps/worker"))));

        List<LockPackage> epollEntries = selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(epoll))
                .toList();
        assertEquals(2, epollEntries.size());

        LockPackage linux = variantByClassifier(selection, epoll, "linux-x86_64");
        assertEquals("4.1.100.Final", linux.version());
        assertEquals(List.of("apps/api"), linux.members());
        assertTrue(linux.jar().orElseThrow().endsWith("netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));

        LockPackage osx = variantByClassifier(selection, epoll, "osx-aarch_64");
        assertEquals(List.of("apps/worker"), osx.members());
        assertTrue(osx.jar().orElseThrow().endsWith("netty-transport-native-epoll-4.1.100.Final-osx-aarch_64.jar"));

        // Same version across the two variants: no spurious version conflict is manufactured.
        assertEquals(List.of(), selection.conflicts());
    }

    @Test
    void keepsPlainAndClassifiedVariantsOfOneGavDistinct() {
        PackageId epoll = new PackageId("io.netty", "netty-transport-native-epoll");

        WorkspaceExternalSelection selection = selector.select(List.of(
                jarPackage(epoll, "4.1.100.Final", DependencyScope.RUNTIME, false, List.of(), List.of("apps/api"), List.of()),
                classifiedJarPackage(epoll, "4.1.100.Final", "linux-x86_64", DependencyScope.RUNTIME, List.of("apps/worker"))));

        assertEquals(2, selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(epoll))
                .count());
        LockPackage plain = selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(epoll))
                .filter(lockPackage -> LockArtifactVariant.of(lockPackage).classifier().isEmpty())
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("apps/api"), plain.members());
        assertTrue(plain.jar().orElseThrow().endsWith("netty-transport-native-epoll-4.1.100.Final.jar"));
        assertEquals(List.of("apps/worker"), variantByClassifier(selection, epoll, "linux-x86_64").members());
        assertEquals(List.of(), selection.conflicts());
    }

    @Test
    void mediatesVersionsWithinAVariantAcrossMembers() {
        PackageId epoll = new PackageId("io.netty", "netty-transport-native-epoll");

        WorkspaceExternalSelection selection = selector.select(List.of(
                classifiedJarPackage(epoll, "4.1.90.Final", "linux-x86_64", DependencyScope.RUNTIME, List.of("apps/api")),
                classifiedJarPackage(epoll, "4.1.100.Final", "linux-x86_64", DependencyScope.RUNTIME, List.of("apps/worker"))));

        List<LockPackage> epollEntries = selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(epoll))
                .toList();
        assertEquals(1, epollEntries.size());
        LockPackage linux = epollEntries.getFirst();
        assertEquals("4.1.100.Final", linux.version());
        assertEquals(List.of("apps/api", "apps/worker"), linux.members().stream().sorted().toList());
        assertTrue(linux.jar().orElseThrow().endsWith("netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));

        LockConflict conflict = selection.conflicts().getFirst();
        assertEquals(epoll, conflict.packageId());
        assertEquals("4.1.100.Final", conflict.selectedVersion());
        assertEquals(List.of("4.1.90.Final", "4.1.100.Final"), conflict.requestedVersions());
    }

    private static LockPackage variantByClassifier(
            WorkspaceExternalSelection selection, PackageId packageId, String classifier) {
        return selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .filter(lockPackage -> LockArtifactVariant.of(lockPackage).classifier().equals(Optional.of(classifier)))
                .findFirst()
                .orElseThrow();
    }

    private static LockPackage classifiedJarPackage(
            PackageId packageId,
            String version,
            String classifier,
            DependencyScope scope,
            List<String> members) {
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
                false,
                Optional.of(base + "-" + classifier + ".jar"),
                Optional.of(base + ".pom"),
                Optional.of("jar-" + classifier + "-" + version),
                Optional.of("pom-" + version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                members,
                List.of(),
                List.of(),
                List.of());
    }

    private static LockPackage execEntry(
            WorkspaceExternalSelection selection, PackageId packageId, String version) {
        return selection.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .filter(lockPackage -> lockPackage.version().equals(version))
                .findFirst()
                .orElseThrow();
    }

    private static LockPackage execJarPackage(
            PackageId packageId,
            String version,
            List<String> toolGroups,
            List<String> members) {
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
                DependencyScope.TOOL_EXEC,
                false,
                Optional.of(base + ".jar"),
                Optional.of(base + ".pom"),
                Optional.of("jar-" + version),
                Optional.of("pom-" + version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                members,
                List.of(),
                List.of(),
                toolGroups);
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
                List.of(),
                List.of());
    }
}
