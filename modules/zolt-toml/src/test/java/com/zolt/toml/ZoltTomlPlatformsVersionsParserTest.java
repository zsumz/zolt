package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
