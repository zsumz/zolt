package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DeveloperEntry;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.PublicationMetadata;
import sh.zolt.toml.ZoltTomlParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PublishPomGeneratorTest {
    private final PublishPomGenerator generator = new PublishPomGenerator();

    @Test
    void emitsCoordinatesMetadataAndSortedScopedDependencies() {
        PublicationMetadata metadata = new PublicationMetadata(
                "App Library",
                "",
                "https://example.test/app",
                "Apache-2.0",
                List.of("Ada Lovelace"),
                "https://github.com/example/app",
                "https://github.com/example/app/issues");
        ProjectConfig config = config(metadata, Map.of());

        // Deliberately out of coordinate order to prove the generator sorts.
        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        direct("org.slf4j", "slf4j-api", "2.0.13", DependencyScope.COMPILE),
                        direct("com.zaxxer", "HikariCP", "5.1.0", DependencyScope.RUNTIME),
                        direct("jakarta.servlet", "jakarta.servlet-api", "6.0.0", DependencyScope.PROVIDED)),
                List.of());

        String pom = generator.generate(config, lockfile);

        assertTrue(pom.contains("<modelVersion>4.0.0</modelVersion>"));
        assertTrue(pom.contains("<groupId>com.example</groupId>"));
        assertTrue(pom.contains("<artifactId>app</artifactId>"));
        assertTrue(pom.contains("<version>1.0.0</version>"));
        assertTrue(pom.contains("<name>App Library</name>"));
        assertTrue(pom.contains("<url>https://example.test/app</url>"));
        assertTrue(pom.contains("<name>Apache-2.0</name>"));
        assertTrue(pom.contains("<name>Ada Lovelace</name>"));
        // Blank description is omitted entirely.
        assertTrue(!pom.contains("<description>"));
        // Default jar packaging is left implicit.
        assertTrue(!pom.contains("<packaging>"));

        // Compile scope is implicit (no <scope> element); runtime/provided are emitted.
        // Indentation is load-bearing, so these fragments keep the literal POM spacing.
        assertTrue(pom.contains("    <dependency>\n"
                + "      <groupId>org.slf4j</groupId>\n"
                + "      <artifactId>slf4j-api</artifactId>\n"
                + "      <version>2.0.13</version>\n"
                + "    </dependency>\n"));
        assertTrue(pom.contains("    <dependency>\n"
                + "      <groupId>com.zaxxer</groupId>\n"
                + "      <artifactId>HikariCP</artifactId>\n"
                + "      <version>5.1.0</version>\n"
                + "      <scope>runtime</scope>\n"
                + "    </dependency>\n"));
        assertTrue(pom.contains("<scope>provided</scope>"));

        // Sorted by coordinate: com.zaxxer < jakarta.servlet < org.slf4j.
        int hikari = pom.indexOf("HikariCP");
        int servlet = pom.indexOf("jakarta.servlet-api");
        int slf4j = pom.indexOf("slf4j-api");
        assertTrue(hikari < servlet && servlet < slf4j);
    }

    @Test
    void emitsPackagingLicenseUrlStructuredDevelopersAndScmDetails() {
        PublicationMetadata metadata = new PublicationMetadata(
                "App Library",
                "A test library.",
                "https://example.test/app",
                "Apache-2.0",
                "https://www.apache.org/licenses/LICENSE-2.0.txt",
                List.of(),
                List.of(new DeveloperEntry(
                        "ada",
                        "Ada Lovelace",
                        "ada@example.test",
                        "Analytical Engines",
                        "https://example.test/ada")),
                "https://github.com/example/app",
                "scm:git:https://github.com/example/app.git",
                "scm:git:ssh://git@github.com/example/app.git",
                "v1.0.0",
                "https://github.com/example/app/issues");
        ProjectConfig config = config(PackageMode.WAR, metadata, Map.of());

        String pom = generator.generate(config, new ZoltLockfile(1, List.of(), List.of()));

        // WAR packaging is explicit; jar stays implicit.
        assertTrue(pom.contains("<packaging>war</packaging>"));
        // License carries name and url.
        assertTrue(pom.contains("<name>Apache-2.0</name>"));
        assertTrue(pom.contains("<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>"));
        // Structured developer emits id/name/email/organization/url.
        assertTrue(pom.contains("      <id>ada</id>\n"
                + "      <name>Ada Lovelace</name>\n"
                + "      <email>ada@example.test</email>\n"
                + "      <organization>Analytical Engines</organization>\n"
                + "      <url>https://example.test/ada</url>\n"));
        // SCM carries connection, developerConnection, tag and url in Maven order.
        assertTrue(pom.contains("  <scm>\n"
                + "    <connection>scm:git:https://github.com/example/app.git</connection>\n"
                + "    <developerConnection>scm:git:ssh://git@github.com/example/app.git</developerConnection>\n"
                + "    <tag>v1.0.0</tag>\n"
                + "    <url>https://github.com/example/app</url>\n"
                + "  </scm>\n"));
    }

    @Test
    void escapesXmlSpecialCharactersInMetadata() {
        PublicationMetadata metadata = new PublicationMetadata(
                "A & B <lib> \"quoted\" 'apos'",
                "",
                "",
                "",
                List.of(),
                "",
                "");
        ProjectConfig config = config(metadata, Map.of());

        String pom = generator.generate(config, new ZoltLockfile(1, List.of(), List.of()));

        assertTrue(pom.contains("<name>A &amp; B &lt;lib&gt; &quot;quoted&quot; &apos;apos&apos;</name>"));
        assertTrue(!pom.contains("<name>A & B"));
    }

    @Test
    void dropsTestScopeDedupesPublishOnlyAndHonorsOptionalAndExclusions() {
        // publishOnly metadata sharing the slf4j coordinate must dedupe (not duplicate) it,
        // contributing the optional flag and exclusions.
        DependencyMetadata slf4jPublishOnly = new DependencyMetadata(
                "dependencies",
                "org.slf4j:slf4j-api",
                "2.0.13",
                false,
                null,
                true,
                true,
                List.of(new DependencyExclusionSpec("commons-logging", "commons-logging")));
        ProjectConfig config = config(
                PublicationMetadata.empty(),
                Map.of(DependencyMetadata.key("dependencies", "org.slf4j:slf4j-api"), slf4jPublishOnly));

        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        direct("org.slf4j", "slf4j-api", "2.0.13", DependencyScope.COMPILE),
                        // TEST scope is not published and must be dropped.
                        direct("org.junit.jupiter", "junit-jupiter", "5.11.4", DependencyScope.TEST)),
                List.of());

        String pom = generator.generate(config, lockfile);

        // junit (test scope) dropped.
        assertTrue(!pom.contains("junit-jupiter"));
        // slf4j appears exactly once despite being in both lockfile and publishOnly metadata.
        assertEquals(1, countOccurrences(pom, "<artifactId>slf4j-api</artifactId>"));
        assertTrue(pom.contains("<optional>true</optional>"));
        assertTrue(pom.contains("      <exclusions>\n"
                + "        <exclusion>\n"
                + "          <groupId>commons-logging</groupId>\n"
                + "          <artifactId>commons-logging</artifactId>\n"
                + "        </exclusion>\n"
                + "      </exclusions>\n"));
    }

    @Test
    void apiDependencyKeepsClassifierTypeOptionalAndExclusions() {
        ProjectConfig config = new ZoltTomlParser().parse("""
                [project]
                name = "app"
                version = "1.0.0"
                group = "com.example"
                java = "21"

                [api.dependencies]
                "io.netty:netty-transport-native-epoll" = { version = "4.1.100.Final", classifier = "linux-x86_64", type = "zip", optional = true, exclusions = [{ group = "io.netty", artifact = "netty-common" }] }
                """);
        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(direct(
                        "io.netty",
                        "netty-transport-native-epoll",
                        "4.1.100.Final",
                        DependencyScope.COMPILE)),
                List.of());

        String pom = generator.generate(config, lockfile);

        assertTrue(pom.contains("<classifier>linux-x86_64</classifier>"));
        assertTrue(pom.contains("<type>zip</type>"));
        assertTrue(pom.contains("<optional>true</optional>"));
        assertTrue(pom.contains("<groupId>io.netty</groupId>\n"
                + "          <artifactId>netty-common</artifactId>"));
    }

    @Test
    void rendersTwoVariantsOfOneGaAsDistinctDependenciesInsteadOfCollapsing() {
        // A member depends on two classified variants of one GA in different published scopes: the plain-GA
        // dedup key would collapse them onto one <dependency>; the variant-aware identity keeps both.
        String coordinate = "io.netty:netty-transport-native-epoll";
        DependencyMetadata linux = new DependencyMetadata(
                "dependencies", coordinate, null, null, false, null, false, false, List.of(), "linux-x86_64", null);
        DependencyMetadata osx = new DependencyMetadata(
                "provided.dependencies", coordinate, null, null, false, null, false, false, List.of(), "osx-aarch_64", null);
        ProjectConfig config = config(
                PublicationMetadata.empty(),
                Map.of(
                        DependencyMetadata.key("dependencies", coordinate), linux,
                        DependencyMetadata.key("provided.dependencies", coordinate), osx));

        ZoltLockfile lockfile = new ZoltLockfile(
                1,
                List.of(
                        direct("io.netty", "netty-transport-native-epoll", "4.1.100.Final", DependencyScope.COMPILE),
                        direct("io.netty", "netty-transport-native-epoll", "4.1.100.Final", DependencyScope.PROVIDED)),
                List.of());

        String pom = generator.generate(config, lockfile);

        assertEquals(2, countOccurrences(pom, "<artifactId>netty-transport-native-epoll</artifactId>"));
        assertTrue(pom.contains("<classifier>linux-x86_64</classifier>"));
        assertTrue(pom.contains("<classifier>osx-aarch_64</classifier>"));
        assertTrue(pom.contains("<scope>provided</scope>"));
    }

    @Test
    void invalidPublishOnlyCoordinateRaisesPublishException() {
        DependencyMetadata invalid = new DependencyMetadata(
                "dependencies",
                "not-a-coordinate",
                "1.0.0",
                false,
                null,
                false,
                true,
                List.of());
        ProjectConfig config = config(
                PublicationMetadata.empty(),
                Map.of(DependencyMetadata.key("dependencies", "not-a-coordinate"), invalid));

        PublishException exception = assertThrows(
                PublishException.class,
                () -> generator.generate(config, new ZoltLockfile(1, List.of(), List.of())));

        assertTrue(exception.getMessage().contains("Invalid dependency coordinate"));
        assertTrue(exception.getMessage().contains("not-a-coordinate"));
    }

    private static ProjectConfig config(
            PublicationMetadata metadata,
            Map<String, DependencyMetadata> dependencyMetadata) {
        return config(PackageMode.THIN, metadata, dependencyMetadata);
    }

    private static ProjectConfig config(
            PackageMode mode,
            PublicationMetadata metadata,
            Map<String, DependencyMetadata> dependencyMetadata) {
        ProjectConfig base = ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("app", "1.0.0", "com.example", "21", Optional.empty()),
                Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
        return base
                .withPackageSettings(new PackageSettings(mode, false, false, false, metadata))
                .withDependencyMetadata(dependencyMetadata);
    }

    private static LockPackage direct(String group, String artifact, String version, DependencyScope scope) {
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

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
