package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.publish.PublishException;
import sh.zolt.publish.PublishPomGenerator;
import sh.zolt.toml.ZoltTomlParser;
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

        ZoltLockfile projected = projection.project("acme-http", memberConfig, aggregated);

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

    @Test
    void takesTheMembersOwnClassifierVariantVersionNotASiblingVariant() {
        // acme-worker depends on the osx-classified netty (resolved 4.1.100); a sibling uses the linux
        // variant at 4.1.90. The projection must take the member's OWN variant version, never the sibling's.
        String coordinate = "io.netty:netty-transport-native-epoll";
        ProjectConfig memberConfig = ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("acme-worker", "1.0.0", "com.acme", "21", Optional.empty()),
                Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                Map.of(coordinate, "4.1.100.Final"),
                Map.of(),
                BuildSettings.defaults())
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", coordinate),
                        new DependencyMetadata("dependencies", coordinate, null, null, false, null, false, false,
                                List.of(), "osx-aarch_64", null)));

        ZoltLockfile aggregated = new ZoltLockfile(
                1,
                List.of(
                        classifiedExternal("io.netty", "netty-transport-native-epoll", "4.1.90.Final",
                                "linux-x86_64", "sibling"),
                        classifiedExternal("io.netty", "netty-transport-native-epoll", "4.1.100.Final",
                                "osx-aarch_64", "acme-worker")),
                List.of());

        ZoltLockfile projected = projection.project("acme-worker", memberConfig, aggregated);

        LockPackage netty = index(projected).get(coordinate);
        assertEquals("4.1.100.Final", netty.version());
    }

    @Test
    void classifiedApiDependencyProjectsExactlyAndPublishesItsMetadata() {
        String coordinate = "io.netty:netty-transport-native-epoll";
        ProjectConfig memberConfig = new ZoltTomlParser().parse("""
                [project]
                name = "acme-core"
                version = "1.0.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "io.netty:netty-transport-native-epoll" = { version = "4.1.100.Final", classifier = "linux-x86_64", optional = true, exclusions = [{ group = "io.netty", artifact = "netty-common" }] }
                """);
        ZoltLockfile aggregated = new ZoltLockfile(
                1,
                List.of(classifiedExternal(
                        "io.netty",
                        "netty-transport-native-epoll",
                        "4.1.100.Final",
                        "linux-x86_64",
                        "acme-core")),
                List.of());

        ZoltLockfile projected = projection.project("acme-core", memberConfig, aggregated);
        LockPackage netty = projected.packages().getFirst();
        String pom = new PublishPomGenerator().generate(memberConfig, projected);

        assertEquals("4.1.100.Final", netty.version());
        assertTrue(netty.jar().orElseThrow().endsWith(
                "netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));
        assertTrue(pom.contains("<classifier>linux-x86_64</classifier>"));
        assertTrue(pom.contains("<optional>true</optional>"));
        assertTrue(pom.contains("<artifactId>netty-common</artifactId>"));
    }

    @Test
    void missingDeclaredNonDefaultVariantRequiresLockRegeneration() {
        String coordinate = "io.netty:netty-transport-native-epoll";
        ProjectConfig memberConfig = ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("acme-worker", "1.0.0", "com.acme", "21", Optional.empty()),
                        Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                        Map.of(coordinate, "4.1.100.Final"),
                        Map.of(),
                        BuildSettings.defaults())
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", coordinate),
                        new DependencyMetadata(
                                "dependencies",
                                coordinate,
                                null,
                                null,
                                false,
                                null,
                                false,
                                false,
                                List.of(),
                                "osx-aarch_64",
                                null)));
        ZoltLockfile aggregated = new ZoltLockfile(
                1,
                List.of(classifiedExternal(
                        "io.netty",
                        "netty-transport-native-epoll",
                        "4.1.90.Final",
                        "linux-x86_64",
                        "acme-worker")),
                List.of());

        PublishException exception = assertThrows(
                PublishException.class,
                () -> projection.project("acme-worker", memberConfig, aggregated));

        assertTrue(exception.getMessage().contains("osx-aarch_64"));
        assertTrue(exception.getMessage().contains("zolt resolve --workspace"));
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
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of("acme-http"),
                List.of(),
                List.of(),
                List.of());
    }

    private static LockPackage classifiedExternal(
            String group,
            String artifact,
            String version,
            String classifier,
            String member) {
        String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version;
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                ProjectConfig.MAVEN_CENTRAL,
                DependencyScope.COMPILE,
                false,
                Optional.of(base + "-" + classifier + ".jar"),
                Optional.of(base + ".pom"),
                Optional.of("jar-" + classifier),
                Optional.of("pom-sha"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(member),
                List.of(),
                List.of(),
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
