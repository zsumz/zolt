package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.explain.gradle.GradleDependencyInspection;
import com.zolt.explain.gradle.GradleExplainFormatter;
import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.gradle.GradlePluginInspection;
import com.zolt.explain.gradle.GradleProjectInspection;
import com.zolt.explain.gradle.GradleRepositoryInspection;
import com.zolt.explain.gradle.GradleVersionCatalogAlias;
import com.zolt.explain.maven.MavenExplainFormatter;
import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenProjectInspection;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ExplainReportFormatterTest {
    @Test
    void mavenTextReportGroupsSignalsAndNextSteps() {
        MavenInspectionResult result = new MavenInspectionResult(
                Path.of("/repo"),
                List.of(new MavenProjectInspection(
                        Path.of("."),
                        "legacy",
                        "com.example",
                        "1.0.0",
                        "Legacy",
                        "war",
                        "21",
                        List.of("app"),
                        List.of("src/main/java"),
                        List.of("src/test/java"),
                        List.of("src/main/resources"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())),
                ExplainSignals.sorted(List.of(
                        ExplainSignals.MAVEN_PACKAGING_UNSUPPORTED.signal(
                                ".",
                                "Packaging `war` needs an explicit Zolt packaging primitive."),
                        ExplainSignals.MAVEN_DEPENDENCY_DYNAMIC_VERSION.signal(
                                ".",
                                "Dependency `com.example:legacy-lib:[1.0,2.0)` uses dynamic version `[1.0,2.0)` (version-policy rule: version-range)."))));

        assertEquals("""
                Zolt explain: Maven project

                Project
                  Root: /repo
                  Projects: 1
                  Signals: 2

                Projects
                  - . (legacy, packaging=war, java=21)
                    modules: app
                    dependencies: 0
                    managed dependencies: 0
                    imported BOMs: 0
                    plugins: 0
                    profiles: 0

                What Zolt can build
                  ok    no static buildability issues found in this first inspection pass

                What can cache
                  ok    no static cacheability issues found in this first inspection pass

                Non-determinism
                  block  Dependency `com.example:legacy-lib:[1.0,2.0)` uses dynamic version `[1.0,2.0)` (version-policy rule: version-range).

                Migration blockers
                  block  Packaging `war` needs an explicit Zolt packaging primitive.

                Next steps
                  1. Replace Maven ranges or SNAPSHOTs with fixed versions before migrating.
                  2. Map this packaging mode to Zolt package settings before migrating.

                This command inspected Maven metadata statically and did not execute Maven.
                """,
                new MavenExplainFormatter().text(result));
    }

    @Test
    void gradleJsonReportIncludesMigrationContract() {
        GradleInspectionResult result = new GradleInspectionResult(
                Path.of("/repo"),
                "settings.gradle",
                List.of("app"),
                List.of(new GradleVersionCatalogAlias("guava", "com.google.guava:guava:33.4.8-jre")),
                List.of(new GradleProjectInspection(
                        Path.of("."),
                        "demo",
                        "build.gradle",
                        "groovy",
                        "21",
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        List.of(new GradlePluginInspection("java", "")),
                        List.of(new GradleRepositoryInspection("mavenCentral", "https://repo.maven.apache.org/maven2")),
                        List.of(new GradleDependencyInspection(
                                "implementation",
                                "libs.guava",
                                "com.google.guava:guava:33.4.8-jre",
                                "guava")),
                        List.of("src/main/java"),
                        List.of("src/test/java"))),
                ExplainSignals.sorted(List.of(
                        ExplainSignals.GRADLE_CUSTOM_TASK_DETECTED.signal(
                                ".",
                                "Gradle build declares custom tasks."))));

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "source": "gradle",
                  "root": "/repo",
                  "settingsFile": "settings.gradle",
                  "summary": {
                    "includedProjects": 1,
                    "projects": 1,
                    "versionCatalogAliases": 1,
                    "signals": 1,
                    "blockers": 0,
                    "warnings": 1,
                    "unknown": 0,
                    "ok": 0
                  },
                  "includedProjects": ["app"],
                  "versionCatalogAliases": [
                    {
                      "alias": "guava",
                      "coordinate": "com.google.guava:guava:33.4.8-jre"
                    }
                  ],
                  "projects": [
                    {
                      "path": ".",
                      "name": "demo",
                      "buildFile": "build.gradle",
                      "dsl": "groovy",
                      "javaVersion": "21",
                      "plugins": [
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
                          "notation": "libs.guava",
                          "resolvedCoordinate": "com.google.guava:guava:33.4.8-jre",
                          "versionCatalogAlias": "guava"
                        }
                      ],
                      "sourceRoots": ["src/main/java"],
                      "testSourceRoots": ["src/test/java"]
                    }
                  ],
                  "signals": [
                    {
                      "severity": "warn",
                      "category": "buildability",
                      "project": ".",
                      "id": "gradle.custom-task.detected",
                      "message": "Gradle build declares custom tasks.",
                      "nextStep": "Review whether custom tasks generate sources, resources, tests, package outputs, or runtime assets."
                    }
                  ],
                  "migration": {
                    "status": "manual-review",
                    "nextSteps": ["Review the static report, then create zolt.toml and run zolt resolve."]
                  }
                }
                """,
                new GradleExplainFormatter().json(result));
    }
}
