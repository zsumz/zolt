package com.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class GradleBuildFileParserTest {
    private final GradleBuildFileParser parser = new GradleBuildFileParser();

    @Test
    void parsesPluginsRepositoriesAndJavaVersion() {
        String content = """
                plugins {
                    id 'org.springframework.boot' version '3.3.6'
                    id("java-library")
                    jacoco
                }

                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                    maven { url = 'https://repo.example.com/releases' }
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                """;

        assertEquals("21", parser.javaVersion(content));
        assertTrue(parser.plugins(content).stream()
                .anyMatch(plugin -> plugin.id().equals("org.springframework.boot") && plugin.version().equals("3.3.6")));
        assertTrue(parser.plugins(content).stream().anyMatch(plugin -> plugin.id().equals("java-library")));
        assertTrue(parser.plugins(content).stream().anyMatch(plugin -> plugin.id().equals("jacoco")));
        assertTrue(parser.repositories(content).stream()
                .anyMatch(repository -> repository.kind().equals("mavenCentral")
                        && repository.url().equals("https://repo.maven.apache.org/maven2")));
        assertTrue(parser.repositories(content).stream()
                .anyMatch(repository -> repository.kind().equals("maven")
                        && repository.url().equals("https://repo.example.com/releases")));
    }

    @Test
    void parsesKotlinDslBacktickAccessorPlugins() {
        String content = """
                plugins {
                    `java-library`
                    `application`
                    id("org.springframework.boot") version "3.3.6"
                }
                """;

        var plugins = parser.plugins(content);

        assertTrue(plugins.stream().anyMatch(plugin -> plugin.id().equals("java-library")),
                () -> "backtick `java-library` should be detected: " + plugins);
        assertTrue(plugins.stream().anyMatch(plugin -> plugin.id().equals("application")),
                () -> "backtick `application` should be detected: " + plugins);
        assertTrue(plugins.stream().anyMatch(plugin -> plugin.id().equals("org.springframework.boot")));
    }

    @Test
    void parsesDependencyNotationsAndCatalogAliases() {
        String content = """
                dependencies {
                    api 'com.example:api:1.0'
                    implementation libs.guava
                    compileOnly group: 'jakarta.annotation', name: 'jakarta.annotation-api', version: '3.0.0'
                    testImplementation(libs.junit.jupiter)
                    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
                }
                """;

        var dependencies = parser.dependencies(content, Map.of(
                "guava", "com.google.guava:guava:33.4.8-jre",
                "junit.jupiter", "org.junit.jupiter:junit-jupiter:5.11.4"),
                Map.of(), ".", new java.util.ArrayList<>());

        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.configuration().equals("api")
                        && dependency.resolvedCoordinate().equals("com.example:api:1.0")));
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.configuration().equals("implementation")
                        && dependency.versionCatalogAlias().equals("guava")
                        && dependency.resolvedCoordinate().equals("com.google.guava:guava:33.4.8-jre")));
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.configuration().equals("compileOnly")
                        && dependency.resolvedCoordinate().equals("jakarta.annotation:jakarta.annotation-api:3.0.0")));
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.configuration().equals("testImplementation")
                        && dependency.versionCatalogAlias().equals("junit.jupiter")));
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.configuration().equals("annotationProcessor")
                        && dependency.resolvedCoordinate().equals("org.mapstruct:mapstruct-processor:1.6.3")));
    }

    @Test
    void configurationNameSubstringInArtifactIdDoesNotSpawnPhantomDependency() {
        String content = """
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.16'
                    implementation 'jakarta.annotation:jakarta.annotation-api:3.0.0'
                }
                """;

        var dependencies = parser.dependencies(content, Map.of(), Map.of(), ".", new java.util.ArrayList<>());

        assertEquals(2, dependencies.size(), () -> "expected exactly two real dependencies, got " + dependencies);
        assertTrue(dependencies.stream().allMatch(dependency -> dependency.configuration().equals("implementation")),
                () -> "no dependency should be attributed to the `api` config from the `slf4j-api` substring: " + dependencies);
        assertTrue(dependencies.stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("org.slf4j:slf4j-api:2.0.16")));
        assertTrue(dependencies.stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("jakarta.annotation:jakarta.annotation-api:3.0.0")));
        assertTrue(dependencies.stream().noneMatch(dependency -> dependency.configuration().equals("api")),
                () -> "phantom api-config entry leaked from an artifact-id substring: " + dependencies);
    }

    @Test
    void parsesGroupVersionAndMainClassAssignments() {
        String content = """
                group = 'com.example'
                version = '0.3.1'

                application {
                    mainClass = 'com.example.report.ReportApp'
                }
                """;

        assertEquals("com.example", parser.group(content).orElseThrow());
        assertEquals("0.3.1", parser.version(content).orElseThrow());
        assertEquals("com.example.report.ReportApp", parser.mainClass(content).orElseThrow());
    }

    @Test
    void parsesGroovySpaceCallAssignmentsAndMainClassName() {
        String content = """
                group "com.example.groovy"
                version "1.2.3"
                mainClassName = 'com.example.Legacy'
                """;

        assertEquals("com.example.groovy", parser.group(content).orElseThrow());
        assertEquals("1.2.3", parser.version(content).orElseThrow());
        assertEquals("com.example.Legacy", parser.mainClass(content).orElseThrow());
    }

    @Test
    void ignoresInterpolatedGroupAndAbsentFields() {
        String content = """
                group "${applicationGroupId}"
                dependencies {
                    implementation 'com.example:lib:1.0'
                }
                """;

        assertTrue(parser.group(content).isEmpty(), () -> "interpolated group must not be read as a literal");
        assertTrue(parser.version(content).isEmpty());
        assertTrue(parser.mainClass(content).isEmpty());
    }

    @Test
    void parsesSourceRootsWithDefaultsForAdditiveSourceDirs() {
        String content = """
                sourceSets {
                    main {
                        java {
                            srcDirs = ['src/main/java', 'build/generated/sources/openapi']
                        }
                    }
                    test {
                        java {
                            srcDirs += ['src/integrationTest/java']
                        }
                    }
                }
                """;

        assertEquals(
                java.util.List.of("build/generated/sources/openapi", "src/main/java"),
                parser.sourceRoots(content, "main", "src/main/java"));
        assertEquals(
                java.util.List.of("src/integrationTest/java", "src/test/java"),
                parser.sourceRoots(content, "test", "src/test/java"));
        assertEquals(
                java.util.List.of("src/fixtures/java"),
                parser.sourceRoots(content, "fixtures", "src/fixtures/java"));
    }
}
