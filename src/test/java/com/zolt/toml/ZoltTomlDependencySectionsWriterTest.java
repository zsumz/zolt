package com.zolt.toml;

import static com.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencySectionsWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[api.dependencies]\n"));
        assertTrue(toml.contains("\"com.acme:managed-contract\" = {}"));
        assertTrue(toml.contains("\"com.acme:shared-contract\" = { workspace = \"modules/shared-contract\" }"));
        assertTrue(toml.contains("\"com.fasterxml.jackson.core:jackson-annotations\" = \"2.20.0\""));
        assertEquals(config.apiDependencies(), parsed.apiDependencies());
        assertEquals(config.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(config.workspaceApiDependencies(), parsed.workspaceApiDependencies());
    }

    @Test
    void preservesApiDependenciesWhenEditingOtherSections() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.addPlatform(config, "com.acme:enterprise-platform", "2026.1.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
        assertEquals("2026.1.0", parsed.platforms().get("com.acme:enterprise-platform"));
    }

    @Test
    void writesRuntimeAndProvidedDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");
        config = writer.addDependency(
                config,
                DependencySection.PROVIDED,
                "jakarta.servlet:jakarta.servlet-api",
                "6.1.0");
        config = writer.addPlatform(config, "org.springframework.boot:spring-boot-dependencies", "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertTrue(toml.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", parsed.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
    }

    @Test
    void editingRuntimeDependencyRemovesConflictingMainScopeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.h2database:h2" = "2.4.240"
                """);

        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
    }

    @Test
    void removingProvidedDependencyPreservesRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.h2database:h2" = "2.4.240"

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"
                """);

        config = writer.removeDependency(config, DependencySection.PROVIDED, "jakarta.servlet:jakarta.servlet-api");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("2.4.240", parsed.runtimeDependencies().get("com.h2database:h2"));
        assertTrue(parsed.providedDependencies().isEmpty());
    }

    @Test
    void writesDevDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.DEV, "org.springframework.boot:spring-boot-devtools");
        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dev.dependencies]\n\"com.acme:local-tool\" = \"1.0.0\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertEquals("1.0.0", parsed.devDependencies().get("com.acme:local-tool"));
        assertTrue(parsed.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void editingDevDependencyRemovesConflictingRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.acme:local-tool" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.runtimeDependencies().isEmpty());
        assertEquals("2.0.0", parsed.devDependencies().get("com.acme:local-tool"));
    }

    @Test
    void editingMainDependencyRemovesConflictingApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.apiDependencies().isEmpty());
        assertEquals("2.0.0", parsed.dependencies().get("com.acme:contract"));
    }

    @Test
    void editingApiDependencyRemovesConflictingMainDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.API, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void removingMainDependencyPreservesApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.removeDependency(config, DependencySection.MAIN, "com.acme:contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void editsApiDependenciesAcrossVersionedManagedAndWorkspaceForms() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = "1.0.0"
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                """);

        config = writer.addManagedDependency(config, DependencySection.API, "com.acme:managed-contract");
        config = writer.addDependency(config, DependencySection.API, "com.acme:shared-contract", "2.0.0");
        config = writer.removeDependency(config, DependencySection.API, "com.acme:missing-contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedApiDependencies().contains("com.acme:managed-contract"));
        assertFalse(parsed.apiDependencies().containsKey("com.acme:managed-contract"));
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:shared-contract"));
        assertFalse(parsed.workspaceApiDependencies().containsKey("com.acme:shared-contract"));
    }


    @Test
    void addsDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("33.4.0-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

}
