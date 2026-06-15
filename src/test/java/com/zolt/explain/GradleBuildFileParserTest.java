package com.zolt.explain;

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
                "junit.jupiter", "org.junit.jupiter:junit-jupiter:5.11.4"));

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
