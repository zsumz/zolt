package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ZoltTomlPlatformsVersionsParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

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
        for (String version : java.util.List.of("${guava}", "$guava", "@guava@", "[1.0,2.0)", "1.+", "1.0-SNAPSHOT")) {
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
    void rejectsUnsupportedLiteralDependencyVersions() {
        for (String version : java.util.List.of("[1.0,2.0)", "1.+", "LATEST", "RELEASE", "1.0-SNAPSHOT", "1.0.", "$libVersion")) {
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
