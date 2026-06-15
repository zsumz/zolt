package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencySectionsParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesTestDependencies() {
        ProjectConfig config = parser.parse(Path.of("examples/junit-basic/zolt.toml"));

        assertEquals("1.11.4", config.testDependencies().get("org.junit.platform:junit-platform-console-standalone"));
    }

    @Test
    void petclinicFixtureDeclaresH2AsRuntimeOnly() {
        ProjectConfig config = parser.parse(Path.of("examples/spring-boot-petclinic-lite/zolt.toml"));

        assertTrue(config.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertFalse(config.dependencies().containsKey("com.h2database:h2"));
        assertFalse(config.managedDependencies().contains("com.h2database:h2"));
    }

    @Test
    void preservesDependencyDeclarationOrder() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "ordered"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:alpha" = "1.0.0"
                "com.example:beta" = "1.0.0"
                "com.example:core" = { workspace = "modules/core" }
                "com.example:util" = { workspace = "modules/util" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = "5.11.4"
                "org.assertj:assertj-core" = "3.27.3"
                """);

        assertEquals(List.of("com.example:alpha", "com.example:beta"), new ArrayList<>(config.dependencies().keySet()));
        assertEquals(List.of("com.example:core", "com.example:util"), new ArrayList<>(config.workspaceDependencies().keySet()));
        assertEquals(List.of("org.junit.jupiter:junit-jupiter", "org.assertj:assertj-core"), new ArrayList<>(config.testDependencies().keySet()));
    }


    @Test
    void parsesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                """);

        assertEquals("2.20.0", config.apiDependencies().get("com.fasterxml.jackson.core:jackson-annotations"));
        assertTrue(config.managedApiDependencies().contains("com.acme:managed-contract"));
        assertEquals("modules/shared-contract", config.workspaceApiDependencies().get("com.acme:shared-contract"));
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.workspaceDependencies().isEmpty());
    }

    @Test
    void parsesRuntimeAndProvidedDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.h2database:h2" = {}
                "org.slf4j:slf4j-simple" = "2.0.17"

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"
                "com.acme:managed-container-api" = {}

                [dev.dependencies]
                "org.springframework.boot:spring-boot-devtools" = {}
                "com.acme:local-tool" = "1.0.0"
                """);

        assertEquals("2.0.17", config.runtimeDependencies().get("org.slf4j:slf4j-simple"));
        assertTrue(config.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", config.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertTrue(config.managedProvidedDependencies().contains("com.acme:managed-container-api"));
        assertEquals("1.0.0", config.devDependencies().get("com.acme:local-tool"));
        assertTrue(config.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void rejectsDuplicateApiAndImplementationDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api.dependencies]
                        "com.acme:contract" = "1.0.0"

                        [dependencies]
                        "com.acme:contract" = "1.0.0"
                        """));

        assertEquals(
                "Dependency com.acme:contract is declared in both [api.dependencies] and [dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateMainRuntimeAndProvidedDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [runtime.dependencies]
                        "com.h2database:h2" = "2.4.240"

                        [provided.dependencies]
                        "com.h2database:h2" = "2.4.240"
                        """));

        assertEquals(
                "Dependency com.h2database:h2 is declared in both [runtime.dependencies] and [provided.dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateDevAndRuntimeDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [runtime.dependencies]
                        "org.springframework.boot:spring-boot-devtools" = "4.0.6"

                        [dev.dependencies]
                        "org.springframework.boot:spring-boot-devtools" = "4.0.6"
                        """));

        assertEquals(
                "Dependency org.springframework.boot:spring-boot-devtools is declared in both [runtime.dependencies] and [dev.dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownApiField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api]
                        exports = ["com.acme:contract"]
                        """));

        assertEquals(
                "Unknown field [api].exports in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedApiDependencyDeclaration() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api.dependencies]
                        "com.acme:contract" = 42
                        """));

        assertEquals(
                "Invalid value for [api.dependencies].com.acme:contract in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata.",
                exception.getMessage());
    }


    @Test
    void dependencyValuesMustBeStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = 33
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.google.guava:guava in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata.",
                exception.getMessage());
    }

    @Test
    void dependencyInlineTablesRejectUnknownFields() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = { scope = "compile" }
                        """));

        assertEquals(
                "Unknown field [dependencies.com.google.guava:guava].scope in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }


}
