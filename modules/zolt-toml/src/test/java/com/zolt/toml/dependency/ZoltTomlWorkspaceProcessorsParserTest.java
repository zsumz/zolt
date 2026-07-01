package com.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

final class ZoltTomlWorkspaceProcessorsParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesWorkspaceDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }

                [test.dependencies]
                "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                """);

        assertEquals("modules/core", config.workspaceDependencies().get("com.acme:core"));
        assertEquals("modules/test-fixtures", config.workspaceTestDependencies().get("com.acme:test-fixtures"));
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.testDependencies().isEmpty());
    }

    @Test
    void rejectsWorkspaceDependencyWithVersion() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "api"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", workspace = "modules/core" }
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.acme:core in zolt.toml. Use version, versionRef, or workspace; do not combine them.",
                exception.getMessage());
    }

    @Test
    void rejectsBlankWorkspaceDependencyPath() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "api"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { workspace = "" }
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.acme:core.workspace in zolt.toml. Use a non-empty workspace member path.",
                exception.getMessage());
    }

    @Test
    void parsesAnnotationProcessorDeclarations() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "micronaut"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "io.micronaut.platform:micronaut-platform" = "5.0.0"

                [annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "org.mapstruct:mapstruct-processor" = { version = "1.6.3" }

                [test.annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "com.example:test-processor" = "1.0.0"
                """);

        assertTrue(config.managedAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.6.3", config.annotationProcessors().get("org.mapstruct:mapstruct-processor"));
        assertTrue(config.managedTestAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.0.0", config.testAnnotationProcessors().get("com.example:test-processor"));
    }


    @Test
    void parsesWorkspaceAnnotationProcessorDeclarations() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "consumer"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [annotationProcessors]
                "com.example:shape-processor" = { workspace = "modules/shape-processor" }

                [test.annotationProcessors]
                "com.example:test-processor" = { workspace = "modules/test-processor" }
                """);

        assertEquals(
                "modules/shape-processor",
                config.workspaceAnnotationProcessors().get("com.example:shape-processor"));
        assertEquals(
                "modules/test-processor",
                config.workspaceTestAnnotationProcessors().get("com.example:test-processor"));
        assertTrue(config.annotationProcessors().isEmpty());
        assertTrue(config.managedAnnotationProcessors().isEmpty());
        assertTrue(config.testAnnotationProcessors().isEmpty());
        assertTrue(config.managedTestAnnotationProcessors().isEmpty());
    }

    @Test
    void rejectsWorkspaceAnnotationProcessorWithVersion() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "consumer"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [annotationProcessors]
                        "com.example:shape-processor" = { version = "1.0.0", workspace = "modules/shape-processor" }
                        """));

        assertEquals(
                "Invalid value for [annotationProcessors].com.example:shape-processor in zolt.toml. Use version, versionRef, or workspace; do not combine them.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedAnnotationProcessorDeclaration() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [annotationProcessors]
                        "io.micronaut:micronaut-inject-java" = 42
                        """));

        assertEquals(
                "Invalid value for [annotationProcessors].io.micronaut:micronaut-inject-java in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata.",
                exception.getMessage());
    }


}
