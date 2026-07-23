package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BomSettings;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.PublicationMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Byte-equality golden POM tests: the emitted POM must equal a checked-in fixture exactly, so any
 * change to element order, indentation, or the dependencyManagement/classifier shape is caught.
 */
final class PublishPomGoldenTest {
    private final PublishPomGenerator generator = new PublishPomGenerator();

    @Test
    void classifierAndTypeRideJarPomInMavenElementOrder() throws IOException {
        ProjectConfig config = base("app", "1.0.0", "com.example", PublicationMetadata.empty())
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "io.netty:netty-transport-native-epoll"),
                        new DependencyMetadata(
                                "dependencies",
                                "io.netty:netty-transport-native-epoll",
                                null,
                                null,
                                false,
                                null,
                                false,
                                false,
                                List.of(),
                                "linux-x86_64",
                                null),
                        DependencyMetadata.key("dependencies", "org.example:widget"),
                        new DependencyMetadata(
                                "dependencies",
                                "org.example:widget",
                                null,
                                null,
                                false,
                                null,
                                false,
                                false,
                                List.of(),
                                null,
                                "zip")));
        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        external("io.netty", "netty-transport-native-epoll", "4.1.100.Final", DependencyScope.COMPILE),
                        external("org.example", "widget", "2.0.0", DependencyScope.COMPILE)),
                List.of());

        assertEquals(golden("classifier.pom.xml"), generator.generate(config, lockfile));
    }

    @Test
    void interMemberWorkspaceDependencyRendersRealGavAlongsideExternal() throws IOException {
        ProjectConfig config = base("acme-http", "1.0.0", "com.acme", PublicationMetadata.empty());
        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        workspacePackage("com.acme", "acme-core", "1.0.0", "acme-core"),
                        external("org.slf4j", "slf4j-api", "2.0.13", DependencyScope.COMPILE)),
                List.of());

        assertEquals(golden("inter-member.pom.xml"), generator.generate(config, lockfile));
    }

    @Test
    void bomFamilyEmitsSortedDependencyManagementFromMembersPinsAndImports() throws IOException {
        PublicationMetadata metadata = new PublicationMetadata(
                "Acme Platform BOM",
                "Curated platform versions for the Acme family.",
                "",
                "",
                List.of(),
                "",
                "");
        BomSettings bom = new BomSettings(
                BomSettings.Members.none(),
                List.of(
                        new BomSettings.ManagedVersion("org.postgresql:postgresql", "42.7.4", null, null, null),
                        new BomSettings.ManagedVersion(
                                "io.netty:netty-transport-native-epoll", "4.1.100.Final", "netty", "linux-x86_64", null)),
                List.of(new BomSettings.ImportedBom("com.fasterxml.jackson:jackson-bom", "2.17.0", null)));
        ProjectConfig config = base("acme-bom", "1.0.0", "com.acme.platform", metadata)
                .withPackageSettings(new PackageSettings(PackageMode.BOM, false, false, false, metadata).withBom(bom));
        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        workspacePackage("com.acme", "acme-core", "1.0.0", "acme-core"),
                        workspacePackage("com.acme", "acme-http", "1.0.0", "acme-http")),
                List.of());

        assertEquals(golden("bom-family.pom.xml"), generator.generate(config, lockfile));
    }

    private static ProjectConfig base(String name, String version, String group, PublicationMetadata metadata) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata(name, version, group, "21", Optional.empty()),
                        Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withPackageSettings(new PackageSettings(PackageMode.THIN, false, false, false, metadata));
    }

    private static LockPackage external(String group, String artifact, String version, DependencyScope scope) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                ProjectConfig.MAVEN_CENTRAL,
                scope,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static LockPackage workspacePackage(String group, String artifact, String version, String memberPath) {
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
                Optional.of(memberPath),
                Optional.of("target/classes"),
                List.of());
    }

    private static String golden(String name) throws IOException {
        return new String(
                PublishPomGoldenTest.class.getResourceAsStream("/golden/" + name).readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
