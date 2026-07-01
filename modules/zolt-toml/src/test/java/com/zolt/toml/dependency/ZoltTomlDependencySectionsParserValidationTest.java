package com.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencySectionsParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

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

    @Test
    void rejectsStringElementInExclusionsArray() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", exclusions = ["com.acme:legacy"] }
                        """));

        assertEquals(
                "Invalid value for [dependencies.com.acme:core].exclusions[0] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.",
                exception.getMessage());
    }

    @Test
    void rejectsNumberElementInExclusionsArray() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", exclusions = [42] }
                        """));

        assertEquals(
                "Invalid value for [dependencies.com.acme:core].exclusions[0] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.",
                exception.getMessage());
    }

    @Test
    void rejectsNonTableElementReportsOffendingIndex() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", exclusions = [{ group = "a", artifact = "b" }, "com.acme:legacy"] }
                        """));

        assertEquals(
                "Invalid value for [dependencies.com.acme:core].exclusions[1] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.",
                exception.getMessage());
    }

    @Test
    void parsesValidExclusionsArrayUnchanged() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { version = "1.0.0", exclusions = [{ group = "com.acme", artifact = "legacy" }] }
                """);

        DependencyMetadata core = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.acme:core"));
        assertEquals(1, core.exclusions().size());
        assertEquals("com.acme:legacy", core.exclusions().getFirst().coordinate());
        assertFalse(core.exclusions().isEmpty());
    }

    @Test
    void exclusionEntryMissingGroupKeepsBlankFieldMessage() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", exclusions = [{ artifact = "legacy" }] }
                        """));

        assertEquals(
                "Missing required field [dependencies.com.acme:core.exclusions[0]].group in zolt.toml. Add a non-empty string value.",
                exception.getMessage());
    }
}
