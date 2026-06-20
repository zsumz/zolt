package com.zolt.toml;

import static com.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ZoltTomlVersionPlatformWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void mutationHelpersRejectUnsupportedLiteralVersions() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");

        IllegalArgumentException dependency = assertThrows(
                IllegalArgumentException.class,
                () -> writer.addDependency(
                        config,
                        DependencySection.MAIN,
                        "com.example:legacy-api",
                        "1.0-SNAPSHOT"));
        IllegalArgumentException platform = assertThrows(
                IllegalArgumentException.class,
                () -> writer.addPlatform(config, "com.example:platform", "LATEST"));

        assertTrue(dependency.getMessage().contains("Invalid external dependency version `1.0-SNAPSHOT`"));
        assertTrue(platform.getMessage().contains("Invalid platform version `LATEST`"));
    }


    @Test
    void writesVersionAliasesWhenPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                slf4j = "2.0.17"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.slf4j:slf4j-api" = { versionRef = "slf4j" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[versions]\n\"boot\" = \"4.0.6\"\n\"slf4j\" = \"2.0.17\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-dependencies\" = { versionRef = \"boot\" }"));
        assertTrue(toml.contains("\"org.slf4j:slf4j-api\" = { versionRef = \"slf4j\" }"));
        assertEquals(Map.of("boot", "4.0.6", "slf4j", "2.0.17"), parsed.versionAliases());
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
        assertEquals("2.0.17", parsed.dependencies().get("org.slf4j:slf4j-api"));
    }

    @Test
    void addsVersionRefPlatformWhileKeepingConcreteResolverInput() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                """);

        config = writer.addVersionRefPlatform(
                config,
                "org.springframework.boot:spring-boot-dependencies",
                "boot",
                "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-dependencies\" = { versionRef = \"boot\" }"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
    }

    @Test
    void addsVersionRefDependencyWhileKeepingConcreteResolverInput() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                """);

        config = writer.addVersionRefDependency(
                config,
                DependencySection.MAIN,
                "com.google.guava:guava",
                "guava",
                "33.4.8-jre");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.google.guava:guava\" = { versionRef = \"guava\" }"));
        assertEquals("33.4.8-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals(
                "guava",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key("dependencies", "com.google.guava:guava"))
                        .versionRef());
    }


    @Test
    void writesManagedDependencies() {
        ProjectConfig config = config()
                .project("spring", "com.example", "21", Optional.empty())
                .platforms(Map.of("org.springframework.boot:spring-boot-dependencies", "4.0.6"))
                .managedDependencies(Set.of("org.springframework.boot:spring-boot-starter-webmvc"))
                .managedTestDependencies(Set.of("org.junit.jupiter:junit-jupiter"))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-starter-webmvc\" = {}"));
        assertEquals(config.platforms(), parsed.platforms());
        assertEquals(config.managedDependencies(), parsed.managedDependencies());
        assertEquals(config.managedTestDependencies(), parsed.managedTestDependencies());
    }


    @Test
    void addsManagedDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-tool");
        config = writer.addManagedDependency(config, DependencySection.PROCESSOR, "com.example:processor");
        config = writer.addManagedDependency(config, DependencySection.TEST_PROCESSOR, "com.example:test-processor");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedDependencies().contains("com.example:app"));
        assertTrue(parsed.managedTestDependencies().contains("com.example:test-tool"));
        assertTrue(parsed.managedAnnotationProcessors().contains("com.example:processor"));
        assertTrue(parsed.managedTestAnnotationProcessors().contains("com.example:test-processor"));
    }


    @Test
    void explicitDependencyReplacesManagedDependency() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.example:app"));
        assertTrue(parsed.managedDependencies().isEmpty());
    }

    @Test
    void addsAndRemovesPlatforms() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addPlatform(config, "com.example:enterprise-platform", "2026.1.0");
        config = writer.removePlatform(config, "com.example:enterprise-platform");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.platforms().isEmpty());
    }


}
