package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlPlatformsVersionsParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesPlatformsAndManagedDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "spring"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = "4.0.6"

                [dependencies]
                "org.springframework.boot:spring-boot-starter-webmvc" = {}
                "org.slf4j:slf4j-api" = { version = "2.0.17" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = {}
                """);

        assertEquals(
                "4.0.6",
                config.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertTrue(config.managedDependencies().contains("org.springframework.boot:spring-boot-starter-webmvc"));
        assertEquals("2.0.17", config.dependencies().get("org.slf4j:slf4j-api"));
        assertTrue(config.managedTestDependencies().contains("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void parsesVersionAliasesForVersionBearingFields() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                slf4j = "2.0.17"
                lombok = "1.18.38"
                tomcat = "10.1.40"
                junit = "5.11.4"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.slf4j:slf4j-api" = { versionRef = "slf4j" }

                [annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = { versionRef = "junit" }

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }
                """);

        assertEquals("4.0.6", config.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                config.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
        assertEquals("4.0.6", config.versionAliases().get("boot"));
        assertEquals("2.0.17", config.dependencies().get("org.slf4j:slf4j-api"));
        assertEquals("1.18.38", config.annotationProcessors().get("org.projectlombok:lombok"));
        assertEquals("1.18.38", config.testAnnotationProcessors().get("org.projectlombok:lombok"));
        assertEquals("5.11.4", config.testDependencies().get("org.junit.jupiter:junit-jupiter"));
        assertEquals(
                "10.1.40",
                config.dependencyPolicy()
                        .constraints()
                        .get("org.apache.tomcat.embed:tomcat-embed-core")
                        .version());
        assertEquals(
                "tomcat",
                config.dependencyPolicy()
                        .constraints()
                        .get("org.apache.tomcat.embed:tomcat-embed-core")
                        .versionRef()
                        .orElseThrow());
    }

    @Test
    void rejectsUnknownVersionAliasReference() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = { versionRef = "guava" }
                        """));

        assertEquals(
                "Unknown versionRef `guava` in [dependencies.com.google.guava:guava]. Add [versions].guava or use an explicit version.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedVersionAliasName() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [versions]
                        "spring boot" = "4.0.6"
                        """));

        assertEquals(
                "Invalid [versions] alias `spring boot`. Alias names may contain only letters, digits, dot, underscore, and hyphen.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedVersionAliasValues() {
        for (String version : List.of("${guava}", "@guava@", "[1.0,2.0)", "1.+", "1.0-SNAPSHOT")) {
            ZoltConfigException exception = assertThrows(
                    ZoltConfigException.class,
                    () -> parser.parse("""
                            [project]
                            name = "aliases"
                            version = "0.1.0"
                            group = "com.example"
                            java = "21"

                            [versions]
                            guava = "%s"
                            """.formatted(version)));

            assertEquals(
                    "Invalid [versions].guava in zolt.toml. Use a non-empty literal version string; Zolt does not support interpolation, dynamic versions, version ranges, or SNAPSHOTs.",
                    exception.getMessage());
        }
    }

    @Test
    void projectVersionMayBeSnapshot() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "snapshot-app"
                version = "0.1.0-SNAPSHOT"
                group = "com.example"
                java = "21"
                """);

        assertEquals("0.1.0-SNAPSHOT", config.project().version());
    }

    @Test
    void rejectsUnsupportedLiteralDependencyVersions() {
        for (String version : List.of("[1.0,2.0)", "1.+", "LATEST", "RELEASE", "1.0-SNAPSHOT", "1.0.")) {
            ZoltConfigException exception = assertThrows(
                    ZoltConfigException.class,
                    () -> parser.parse("""
                            [project]
                            name = "versions"
                            version = "0.1.0"
                            group = "com.example"
                            java = "21"

                            [dependencies]
                            "com.example:lib" = "%s"
                            """.formatted(version)));

            assertTrue(exception.getMessage().contains("Invalid external dependency version `" + version + "`"));
            assertTrue(exception.getMessage().contains("[dependencies.com.example:lib]"));
        }
    }

    @Test
    void rejectsUnsupportedPlatformConstraintAndToolVersions() {
        assertVersionPolicyFailure("""
                [project]
                name = "versions"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "com.example:platform" = "1.0-SNAPSHOT"
                """, "Invalid platform version `1.0-SNAPSHOT`", "[platforms.com.example:platform]");
        assertVersionPolicyFailure("""
                [project]
                name = "versions"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencyConstraints]
                "com.example:lib" = { version = "latest.release", kind = "strict" }
                """, "Invalid dependency constraint version `latest.release`", "[dependencyConstraints.com.example:lib]");
        assertVersionPolicyFailure("""
                [project]
                name = "versions"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "LATEST"
                """, "Invalid tool dependency version `LATEST`", "[generated.openapiTool]");
    }

    @Test
    void rejectsVersionAndVersionRefTogether() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [versions]
                        guava = "33.4.8-jre"

                        [dependencies]
                        "com.google.guava:guava" = { version = "33.4.8-jre", versionRef = "guava" }
                        """));

        assertEquals(
                "Invalid value for [dependencies.com.google.guava:guava] in zolt.toml. Use either version or versionRef, not both.",
                exception.getMessage());
    }

    @Test
    void rejectsVersionRefInNonVersionField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        versionRef = "guava"
                        """));

        assertEquals(
                "Invalid value for [package].versionRef in zolt.toml. versionRef is only supported for dependency, platform, constraint, and tool artifact versions.",
                exception.getMessage());
    }


    private void assertVersionPolicyFailure(String toml, String versionMessage, String subject) {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse(toml));

        assertTrue(exception.getMessage().contains(versionMessage));
        assertTrue(exception.getMessage().contains(subject));
    }
}
