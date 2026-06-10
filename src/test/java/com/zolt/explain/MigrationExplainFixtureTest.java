package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class MigrationExplainFixtureTest {
    private static final Path FIXTURE_ROOT = Path.of("examples/migration-explain").toAbsolutePath().normalize();

    @Test
    void mavenSimpleFixtureHasDeterministicGoldenTextAndJson() {
        Path fixture = fixture("maven-simple");
        MavenInspectionResult first = new MavenStaticProjectInspector().inspect(fixture);
        MavenInspectionResult second = new MavenStaticProjectInspector().inspect(fixture);
        MavenExplainFormatter formatter = new MavenExplainFormatter();

        assertEquals(goldenMavenSimpleText(), normalize(formatter.text(first), fixture));
        assertEquals(goldenMavenSimpleText(), normalize(formatter.text(second), fixture));
        assertEquals(goldenMavenSimpleJson(), normalize(formatter.json(first), fixture));
        assertEquals(goldenMavenSimpleJson(), normalize(formatter.json(second), fixture));
    }

    @Test
    void gradleSimpleFixtureHasDeterministicGoldenTextAndJson() {
        Path fixture = fixture("gradle-simple");
        GradleInspectionResult first = new GradleStaticProjectInspector().inspect(fixture);
        GradleInspectionResult second = new GradleStaticProjectInspector().inspect(fixture);
        GradleExplainFormatter formatter = new GradleExplainFormatter();

        assertEquals(goldenGradleSimpleText(), normalize(formatter.text(first), fixture));
        assertEquals(goldenGradleSimpleText(), normalize(formatter.text(second), fixture));
        assertEquals(goldenGradleSimpleJson(), normalize(formatter.json(first), fixture));
        assertEquals(goldenGradleSimpleJson(), normalize(formatter.json(second), fixture));
    }

    @Test
    void mavenMultiModuleFixtureReportsMigrationSignalsWithoutExecutingMaven() throws IOException {
        Path fixture = fixture("maven-multimodule");
        Path marker = fixture.resolve("executed.txt");
        Files.deleteIfExists(marker);

        MavenInspectionResult result = new MavenStaticProjectInspector().inspect(fixture);
        String text = normalize(new MavenExplainFormatter().text(result), fixture);
        String json = normalize(new MavenExplainFormatter().json(result), fixture);

        assertFalse(Files.exists(marker));
        assertSignalIds(
                result,
                "maven.packaging.unsupported",
                "maven.dependency.dynamic-version",
                "maven.plugin.lifecycle-binding",
                "maven.profile.detected");
        assertTrue(text.contains("What Zolt can build"));
        assertTrue(text.contains("What can cache"));
        assertTrue(text.contains("Non-determinism"));
        assertTrue(text.contains("Migration blockers"));
        assertTrue(text.contains("This command inspected Maven metadata statically and did not execute Maven."));
        assertTrue(json.contains("\"status\": \"blocked\""));
        assertTrue(json.contains("\"id\": \"maven.dependency.dynamic-version\""));
    }

    @Test
    void gradleMultiProjectFixtureReportsMigrationSignalsWithoutExecutingGradle() throws IOException {
        Path fixture = fixture("gradle-multiproject");
        Path marker = fixture.resolve("executed.txt");
        Files.deleteIfExists(marker);

        GradleInspectionResult result = new GradleStaticProjectInspector().inspect(fixture);
        String text = normalize(new GradleExplainFormatter().text(result), fixture);
        String json = normalize(new GradleExplainFormatter().json(result), fixture);

        assertFalse(Files.exists(marker));
        assertSignalIds(
                result,
                "gradle.build-src.detected",
                "gradle.included-build.detected",
                "gradle.plugin.convention",
                "gradle.dependency.dynamic-version",
                "gradle.imperative-dependency-logic",
                "gradle.cross-project-build-logic",
                "gradle.custom-task.detected");
        assertTrue(text.contains("What Zolt can build"));
        assertTrue(text.contains("What can cache"));
        assertTrue(text.contains("Non-determinism"));
        assertTrue(text.contains("Migration blockers"));
        assertTrue(text.contains("This command inspected Gradle metadata statically and did not execute Gradle."));
        assertTrue(json.contains("\"status\": \"blocked\""));
        assertTrue(json.contains("\"id\": \"gradle.dependency.dynamic-version\""));
    }

    private static Path fixture(String name) {
        return FIXTURE_ROOT.resolve(name);
    }

    private static String normalize(String value, Path fixture) {
        return value.replace(fixture.toString().replace('\\', '/'), "$ROOT");
    }

    private static void assertSignalIds(MavenInspectionResult result, String... expectedIds) {
        Set<String> actual = result.signals().stream().map(ExplainSignal::id).collect(Collectors.toSet());
        for (String expectedId : expectedIds) {
            assertTrue(actual.contains(expectedId), () -> "missing signal " + expectedId + " in " + actual);
        }
    }

    private static void assertSignalIds(GradleInspectionResult result, String... expectedIds) {
        Set<String> actual = result.signals().stream().map(ExplainSignal::id).collect(Collectors.toSet());
        for (String expectedId : expectedIds) {
            assertTrue(actual.contains(expectedId), () -> "missing signal " + expectedId + " in " + actual);
        }
    }

    private static String goldenMavenSimpleText() {
        return """
                Zolt explain: Maven project

                Project
                  Root: $ROOT
                  Projects: 1
                  Signals: 0

                Projects
                  - . (maven-simple, packaging=jar, java=21)
                    dependencies: 2
                    managed dependencies: 0
                    imported BOMs: 0
                    plugins: 0
                    profiles: 0

                What Zolt can build
                  ok    no static buildability issues found in this first inspection pass

                What can cache
                  ok    no static cacheability issues found in this first inspection pass

                Non-determinism
                  ok    no static non-determinism issues found in this first inspection pass

                Migration blockers
                  ok    no static migration-blocker issues found in this first inspection pass

                Next steps
                  1. Review the static report, then create zolt.toml and run zolt resolve.

                This command inspected Maven metadata statically and did not execute Maven.
                """;
    }

    private static String goldenMavenSimpleJson() {
        return """
                {
                  "schemaVersion": 1,
                  "source": "maven",
                  "root": "$ROOT",
                  "summary": {
                    "projects": 1,
                    "signals": 0,
                    "blockers": 0,
                    "warnings": 0,
                    "unknown": 0,
                    "ok": 1
                  },
                  "projects": [
                    {
                      "path": ".",
                      "name": "maven-simple",
                      "packaging": "jar",
                      "javaVersion": "21",
                      "modules": [],
                      "sourceRoots": ["src/main/java"],
                      "testSourceRoots": ["src/test/java"],
                      "resourceRoots": ["src/main/resources"],
                      "dependencies": [
                        {
                          "scope": "compile",
                          "coordinate": "com.google.guava:guava:33.4.8-jre",
                          "version": "33.4.8-jre",
                          "type": "jar",
                          "optional": false,
                          "managed": false,
                          "importedBom": false
                        },
                        {
                          "scope": "test",
                          "coordinate": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "version": "5.11.4",
                          "type": "jar",
                          "optional": false,
                          "managed": false,
                          "importedBom": false
                        }
                      ],
                      "dependencyManagement": [],
                      "importedBoms": [],
                      "plugins": [],
                      "profiles": []
                    }
                  ],
                  "signals": [],
                  "migration": {
                    "status": "ready",
                    "nextSteps": ["Review the static report, then create zolt.toml and run zolt resolve."]
                  }
                }
                """;
    }

    private static String goldenGradleSimpleText() {
        return """
                Zolt explain: Gradle project

                Project
                  Root: $ROOT
                  Settings: settings.gradle
                  Included projects: 0
                  Projects: 1
                  Version catalog aliases: 0
                  Signals: 0

                Projects
                  - . (gradle-simple, dsl=groovy, java=21)
                    build file: build.gradle
                    plugins: 2
                    repositories: 1
                    dependencies: 2

                What Zolt can build
                  ok    no static buildability issues found in this first inspection pass

                What can cache
                  ok    no static cacheability issues found in this first inspection pass

                Non-determinism
                  ok    no static non-determinism issues found in this first inspection pass

                Migration blockers
                  ok    no static migration-blocker issues found in this first inspection pass

                Next steps
                  1. Review the static report, then create zolt.toml and run zolt resolve.

                This command inspected Gradle metadata statically and did not execute Gradle.
                """;
    }

    private static String goldenGradleSimpleJson() {
        return """
                {
                  "schemaVersion": 1,
                  "source": "gradle",
                  "root": "$ROOT",
                  "settingsFile": "settings.gradle",
                  "summary": {
                    "includedProjects": 0,
                    "projects": 1,
                    "versionCatalogAliases": 0,
                    "signals": 0,
                    "blockers": 0,
                    "warnings": 0,
                    "unknown": 0,
                    "ok": 1
                  },
                  "includedProjects": [],
                  "versionCatalogAliases": [],
                  "projects": [
                    {
                      "path": ".",
                      "name": "gradle-simple",
                      "buildFile": "build.gradle",
                      "dsl": "groovy",
                      "javaVersion": "21",
                      "plugins": [
                        {
                          "id": "application",
                          "version": ""
                        },
                        {
                          "id": "java",
                          "version": ""
                        }
                      ],
                      "repositories": [
                        {
                          "kind": "mavenCentral",
                          "url": "https://repo.maven.apache.org/maven2"
                        }
                      ],
                      "dependencies": [
                        {
                          "configuration": "implementation",
                          "notation": "com.google.guava:guava:33.4.8-jre",
                          "resolvedCoordinate": "com.google.guava:guava:33.4.8-jre",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "testImplementation",
                          "notation": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "resolvedCoordinate": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "versionCatalogAlias": ""
                        }
                      ],
                      "sourceRoots": ["src/main/java"],
                      "testSourceRoots": ["src/test/java"]
                    }
                  ],
                  "signals": [],
                  "migration": {
                    "status": "ready",
                    "nextSteps": ["Review the static report, then create zolt.toml and run zolt resolve."]
                  }
                }
                """;
    }
}
