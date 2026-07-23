package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorkspaceMemberPomLockProjectionTest {
    private final WorkspaceMemberPomLockProjection projection = new WorkspaceMemberPomLockProjection();

    @Test
    void projectsDirectnessFromConfigAndVersionsFromAggregatedLock() {
        // acme-http declares slf4j directly (at a stale 2.0.9) and a workspace dep on acme-core.
        ProjectConfig memberConfig = ProjectConfigs.withWorkspaceDependencySections(
                new ProjectMetadata("acme-http", "1.0.0", "com.acme", "21", Optional.empty()),
                Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                Map.of(),
                Map.of("org.slf4j:slf4j-api", "2.0.9"),
                Set.of(),
                Map.of("com.acme:acme-core", "acme-core"),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());

        // The aggregated lock: acme-core workspace package, slf4j resolved to 2.0.13, and guava which is
        // direct for SOME OTHER member (its direct flag is OR'd in) but never declared by acme-http.
        ZoltLockfile aggregated = new ZoltLockfile(
                1,
                List.of(
                        workspacePackage("com.acme", "acme-core", "1.0.0"),
                        external("org.slf4j", "slf4j-api", "2.0.13", true),
                        external("com.google.guava", "guava", "33.0.0-jre", true)),
                List.of());

        ZoltLockfile projected = projection.project(memberConfig, aggregated);

        Map<String, LockPackage> byCoordinate = index(projected);
        // Directness from config: guava is direct in the aggregated lock but NOT declared by acme-http.
        assertFalse(byCoordinate.containsKey("com.google.guava:guava"));
        assertEquals(2, projected.packages().size());
        // Versions from lock: slf4j is 2.0.13 (aggregated), not the stale 2.0.9 declared in config.
        assertEquals("2.0.13", byCoordinate.get("org.slf4j:slf4j-api").version());
        // Inter-member: acme-core carries the provider GAV, provider version, and workspace marker.
        LockPackage core = byCoordinate.get("com.acme:acme-core");
        assertEquals("1.0.0", core.version());
        assertTrue(core.workspace().isPresent());
        // Every projected coordinate is direct.
        assertTrue(projected.packages().stream().allMatch(LockPackage::direct));
    }

    private static Map<String, LockPackage> index(ZoltLockfile lockfile) {
        java.util.LinkedHashMap<String, LockPackage> byCoordinate = new java.util.LinkedHashMap<>();
        for (LockPackage lockPackage : lockfile.packages()) {
            byCoordinate.put(
                    lockPackage.packageId().groupId() + ":" + lockPackage.packageId().artifactId(), lockPackage);
        }
        return byCoordinate;
    }

    private static LockPackage external(String group, String artifact, String version, boolean direct) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                ProjectConfig.MAVEN_CENTRAL,
                DependencyScope.COMPILE,
                direct,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static LockPackage workspacePackage(String group, String artifact, String version) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "workspace",
                DependencyScope.COMPILE,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(artifact),
                Optional.of("target/classes"),
                List.of());
    }
}
