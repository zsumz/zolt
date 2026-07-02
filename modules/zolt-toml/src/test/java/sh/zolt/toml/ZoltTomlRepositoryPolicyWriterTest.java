package sh.zolt.toml;

import static sh.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.DependencyConstraint;
import sh.zolt.project.DependencyConstraintKind;
import sh.zolt.project.DependencyPolicyExclusion;
import sh.zolt.project.DependencyPolicySettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.project.RepositorySettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ZoltTomlRepositoryPolicyWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesCredentialedRepositories() {
        ProjectConfig config = config()
                .project("enterprise", "com.acme", "17", Optional.empty())
                .repositorySettings(Map.of(
                        "central", RepositorySettings.unauthenticated("central", ProjectConfig.MAVEN_CENTRAL),
                        "company", new RepositorySettings(
                                "company",
                                "https://repo.acme.example/maven",
                                Optional.of("company-artifactory"))))
                .repositoryCredentials(Map.of(
                        "company-artifactory",
                        new RepositoryCredentialSettings(
                                "company-artifactory",
                                "ARTIFACTORY_USERNAME",
                                "ARTIFACTORY_ACCESS_TOKEN")))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"company\" = { url = \"https://repo.acme.example/maven\", credentials = \"company-artifactory\" }"));
        assertTrue(toml.contains("[repositoryCredentials.\"company-artifactory\"]"));
        assertFalse(toml.contains("ReadPermanent"));
        assertEquals(
                "company-artifactory",
                parsed.repositorySettings().get("company").credentials().orElseThrow());
        assertEquals(
                "ARTIFACTORY_ACCESS_TOKEN",
                parsed.repositoryCredentials().get("company-artifactory").passwordEnv());
    }

    @Test
    void writesDependencyPolicyAndConstraints() {
        ProjectConfig config = writer.defaultApplicationConfig("enterprise", "com.acme", "com.acme.Main")
                .withDependencyPolicy(new DependencyPolicySettings(
                        List.of(new DependencyPolicyExclusion(
                                "commons-logging",
                                "commons-logging",
                                Optional.of("Use jcl-over-slf4j"))),
                        Map.of(
                                "org.apache.tomcat.embed:tomcat-embed-core",
                                new DependencyConstraint(
                                        "org.apache.tomcat.embed:tomcat-embed-core",
                                        "10.1.40",
                                        DependencyConstraintKind.STRICT,
                                        Optional.of("Container baseline"))),
                        true));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dependencyPolicy]"));
        assertTrue(toml.contains("failOnVersionConflict = true"));
        assertTrue(toml.contains("exclude = [{ group = \"commons-logging\", artifact = \"commons-logging\", reason = \"Use jcl-over-slf4j\" }]"));
        assertTrue(toml.contains("[dependencyConstraints]"));
        assertTrue(toml.contains("\"org.apache.tomcat.embed:tomcat-embed-core\" = { version = \"10.1.40\", kind = \"strict\", reason = \"Container baseline\" }"));
        assertEquals(config.dependencyPolicy(), parsed.dependencyPolicy());
    }

    @Test
    void writesFailOnVersionConflictWithoutExclusionsOrConstraints() {
        ProjectConfig config = writer.defaultApplicationConfig("enterprise", "com.acme", "com.acme.Main")
                .withDependencyPolicy(new DependencyPolicySettings(List.of(), Map.of(), true));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dependencyPolicy]"));
        assertTrue(toml.contains("failOnVersionConflict = true"));
        assertFalse(toml.contains("exclude ="));
        assertTrue(parsed.dependencyPolicy().failOnVersionConflict());
        assertTrue(parsed.dependencyPolicy().exclusions().isEmpty());
        assertTrue(parsed.dependencyPolicy().constraints().isEmpty());
        assertEquals(config.dependencyPolicy(), parsed.dependencyPolicy());
    }

    @Test
    void writesDependencyConstraintVersionRefsWhenPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [versions]
                tomcat = "10.1.40"

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict", reason = "Container baseline" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.apache.tomcat.embed:tomcat-embed-core\" = { versionRef = \"tomcat\", kind = \"strict\", reason = \"Container baseline\" }"));
        DependencyConstraint constraint = parsed.dependencyPolicy()
                .constraints()
                .get("org.apache.tomcat.embed:tomcat-embed-core");
        assertEquals("10.1.40", constraint.version());
        assertEquals("tomcat", constraint.versionRef().orElseThrow());
    }

}
