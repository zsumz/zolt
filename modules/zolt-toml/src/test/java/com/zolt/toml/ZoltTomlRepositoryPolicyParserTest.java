package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import org.junit.jupiter.api.Test;

final class ZoltTomlRepositoryPolicyParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesCredentialedRepositories() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [repositories]
                "company" = { url = "https://repo.acme.example/maven", credentials = "company-artifactory" }
                "central" = "https://repo.maven.apache.org/maven2"

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ARTIFACTORY_USERNAME"
                passwordEnv = "ARTIFACTORY_ACCESS_TOKEN"
                """);

        assertEquals("https://repo.acme.example/maven", config.repositories().get("company"));
        assertEquals(
                "company-artifactory",
                config.repositorySettings().get("company").credentials().orElseThrow());
        assertEquals(
                "ARTIFACTORY_USERNAME",
                config.repositoryCredentials().get("company-artifactory").usernameEnv());
        assertEquals(
                "ARTIFACTORY_ACCESS_TOKEN",
                config.repositoryCredentials().get("company-artifactory").passwordEnv());
    }

    @Test
    void rejectsRepositoryCredentialReferenceWithoutDefinition() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [repositories]
                "company" = { url = "https://repo.acme.example/maven", credentials = "missing" }
                """));

        assertTrue(exception.getMessage().contains("Repository `company` references credentials `missing`"));
        assertTrue(exception.getMessage().contains("[repositoryCredentials.missing] is not defined"));
    }

    @Test
    void parsesDependencyPolicyAndConstraints() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyPolicy]
                failOnVersionConflict = true
                exclude = [
                  { group = "commons-logging", artifact = "commons-logging", reason = "Use jcl-over-slf4j" },
                  { group = "log4j", artifact = "log4j" }
                ]

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { version = "10.1.40", kind = "strict", reason = "Container baseline" }
                """);

        assertTrue(config.dependencyPolicy().failOnVersionConflict());
        assertEquals(2, config.dependencyPolicy().exclusions().size());
        assertEquals("commons-logging", config.dependencyPolicy().exclusions().getFirst().group());
        assertEquals(
                "Use jcl-over-slf4j",
                config.dependencyPolicy().exclusions().getFirst().reason().orElseThrow());
        assertEquals(
                "10.1.40",
                config.dependencyPolicy()
                        .constraints()
                        .get("org.apache.tomcat.embed:tomcat-embed-core")
                        .version());
    }

    @Test
    void rejectsMalformedDependencyConstraintCoordinate() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyConstraints]
                "org.example:bad:1.0.0" = { version = "2.0.0", kind = "strict" }
                """));

        assertTrue(exception.getMessage().contains("Invalid coordinate `org.example:bad:1.0.0`"));
        assertTrue(exception.getMessage().contains("Use `group:artifact`"));
    }

    @Test
    void rejectsUnsupportedDependencyConstraintKind() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyConstraints]
                "org.example:library" = { version = "2.0.0", kind = "prefer" }
                """));

        assertTrue(exception.getMessage().contains("Unsupported dependency constraint kind `prefer`"));
        assertTrue(exception.getMessage().contains("strict"));
    }

}
