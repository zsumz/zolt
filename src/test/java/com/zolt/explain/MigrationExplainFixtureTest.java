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

    @Test
    void gradleEnterpriseSpringFixtureReportsZoltPrimitiveMappingsWithoutExecutingGradle() throws IOException {
        Path fixture = fixture("gradle-enterprise-spring");
        Path marker = fixture.resolve("executed.txt");
        Files.deleteIfExists(marker);

        GradleInspectionResult first = new GradleStaticProjectInspector().inspect(fixture);
        GradleInspectionResult second = new GradleStaticProjectInspector().inspect(fixture);
        GradleExplainFormatter formatter = new GradleExplainFormatter();
        String text = normalize(formatter.text(first), fixture);
        String json = normalize(formatter.json(first), fixture);

        assertEquals(goldenGradleEnterpriseSpringText(), text);
        assertEquals(text, normalize(formatter.text(second), fixture));
        assertEquals(json, normalize(formatter.json(second), fixture));
        assertFalse(Files.exists(marker));
        assertSignalIds(
                first,
                "gradle.enterprise-plugin.mapped",
                "gradle.repository.credentials",
                "gradle.repository.maven-local",
                "gradle.dependency-policy.mutation",
                "gradle.openapi.generated-sources",
                "gradle.resource-filtering",
                "gradle.test-runtime-settings",
                "gradle.package.archive-mutation",
                "gradle.publication.detected");
        assertTrue(text.contains("Gradle plugin `org.springframework.boot` maps to Zolt Spring Boot platform"));
        assertTrue(text.contains("Gradle OpenAPI generator tasks feed generated Java sources into sourceSets."));
        assertTrue(text.contains("Gradle bootWar package content is changed with archive excludes."));
        assertTrue(text.contains("This command inspected Gradle metadata statically and did not execute Gradle."));
        assertTrue(json.contains("\"id\": \"gradle.repository.maven-local\""));
        assertTrue(json.contains("\"id\": \"gradle.package.archive-mutation\""));
        assertTrue(json.contains("\"status\": \"blocked\""));
    }

    @Test
    void gradleEnterpriseSpringFixtureReportsReadinessScorecardWithoutExecutingGradle() throws IOException {
        Path fixture = fixture("gradle-enterprise-spring");
        Path marker = fixture.resolve("executed.txt");
        Files.deleteIfExists(marker);

        GradleInspectionResult first = new GradleStaticProjectInspector().inspect(fixture);
        GradleInspectionResult second = new GradleStaticProjectInspector().inspect(fixture);
        MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
        String text = normalize(formatter.text(MigrationReadinessScorecards.from(first)), fixture);
        String json = normalize(formatter.json(MigrationReadinessScorecards.from(first)), fixture);

        assertEquals(text, normalize(formatter.text(MigrationReadinessScorecards.from(second)), fixture));
        assertEquals(json, normalize(formatter.json(MigrationReadinessScorecards.from(second)), fixture));
        assertFalse(Files.exists(marker));
        assertTrue(text.contains("Zolt migration readiness scorecard: gradle project"));
        assertTrue(text.contains("repositories: non-deterministic"));
        assertTrue(text.contains("dependencies: blocked"));
        assertTrue(text.contains("generated-sources: supported"));
        assertTrue(text.contains("resources: supported"));
        assertTrue(text.contains("tests: supported"));
        assertTrue(text.contains("coverage: supported"));
        assertTrue(text.contains("package: blocked"));
        assertTrue(text.contains("publish: planned"));
        assertTrue(text.contains("ci: planned"));
        assertTrue(text.contains("mavenLocal() property switch -> local repository overlays"));
        assertTrue(text.contains("bootWar archive mutation -> package placement policy"));
        assertTrue(text.contains("This scorecard inspected build metadata statically and did not execute Maven or Gradle."));
        assertTrue(json.contains("\"command\": \"explain-scorecard\""));
        assertTrue(json.contains("\"name\": \"repositories\""));
        assertTrue(json.contains("\"category\": \"non-deterministic\""));
        assertTrue(json.contains("\"sourcePattern\": \"OpenAPI GenerateTask wired into sourceSets\""));
        assertTrue(json.contains("\"zoltPrimitive\": \"kind = \\\"openapi\\\" generated-source steps\""));
        assertTrue(json.contains("\"followUp\": \"\""));
    }

    @Test
    void gradleEnterpriseSpringFixtureReportsBlockersWithFollowUpsWithoutExecutingGradle() throws IOException {
        Path fixture = fixture("gradle-enterprise-spring");
        Path marker = fixture.resolve("executed.txt");
        Files.deleteIfExists(marker);

        GradleInspectionResult first = new GradleStaticProjectInspector().inspect(fixture);
        GradleInspectionResult second = new GradleStaticProjectInspector().inspect(fixture);
        MigrationBlockerReportFormatter formatter = new MigrationBlockerReportFormatter();
        String text = normalize(formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(first))), fixture);
        String json = normalize(formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(first))), fixture);

        assertEquals(text, normalize(formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(second))), fixture));
        assertEquals(json, normalize(formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(second))), fixture));
        assertFalse(Files.exists(marker));
        assertTrue(text.contains("Zolt migration blocker report: gradle project"));
        assertTrue(text.contains("blocked  configurations.all, excludes, force, or resolutionStrategy -> [dependencyPolicy] and [dependencyConstraints]"));
        assertTrue(text.contains("blocked  imperative dependency or configuration mutation -> [dependencies], classpath lanes, processors, and generated roots"));
        assertTrue(text.contains("blocked  bootWar archive mutation -> package placement policy"));
        assertTrue(text.contains("non-deterministic  credentials resolved from Gradle properties, env, or defaults -> [repositories] credential identities"));
        assertTrue(text.contains("This blocker report inspected build metadata statically and did not execute Maven or Gradle."));
        assertFalse(text.contains("ReadOnly"));
        assertFalse(text.contains("ARTIFACTORY_ACCESS_TOKEN"));
        assertTrue(json.contains("\"command\": \"explain-blockers\""));
        assertTrue(json.contains("\"status\": \"blocked\""));
        assertTrue(json.contains("\"severity\": \"block\""));
        assertTrue(json.contains("\"sourcePattern\": \"bootWar archive mutation\""));
        assertTrue(json.contains("\"zoltPrimitive\": \"package placement policy\""));
        assertTrue(json.contains("\"followUp\": \"\""));
        assertTrue(json.contains("\"signalId\": \"gradle.repository.credentials\""));
    }

    @Test
    void mavenFixtureReportsDeterministicReadinessScorecard() {
        Path fixture = fixture("maven-simple");
        MavenInspectionResult first = new MavenStaticProjectInspector().inspect(fixture);
        MavenInspectionResult second = new MavenStaticProjectInspector().inspect(fixture);
        MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
        String text = normalize(formatter.text(MigrationReadinessScorecards.from(first)), fixture);
        String json = normalize(formatter.json(MigrationReadinessScorecards.from(first)), fixture);

        assertEquals(text, normalize(formatter.text(MigrationReadinessScorecards.from(second)), fixture));
        assertEquals(json, normalize(formatter.json(MigrationReadinessScorecards.from(second)), fixture));
        assertTrue(text.contains("Zolt migration readiness scorecard: maven project"));
        assertTrue(text.contains("Status: supported"));
        assertTrue(text.contains("repositories: supported"));
        assertTrue(text.contains("publish: supported"));
        assertTrue(json.contains("\"source\": \"maven\""));
        assertTrue(json.contains("\"category\": \"supported\""));
        assertTrue(json.contains("\"sourcePattern\": \"dependencies and dependencyManagement\""));
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

    private static String goldenGradleEnterpriseSpringText() {
        return """
                Zolt explain: Gradle project

                Project
                  Root: $ROOT
                  Settings: settings.gradle
                  Included projects: 0
                  Projects: 1
                  Version catalog aliases: 0
                  Signals: 17

                Projects
                  - . (gradle-enterprise-spring, dsl=groovy, java=17)
                    build file: build.gradle
                    plugins: 7
                    repositories: 2
                    dependencies: 6

                What Zolt can build
                  warn  Gradle build declares custom tasks.
                  warn  Gradle OpenAPI generator tasks feed generated Java sources into sourceSets.
                  warn  Gradle Maven Publish configuration selects artifacts and repositories.
                  warn  Gradle test task declares runtime properties, environment, JVM args, or event logging.
                  ok  Gradle plugin `io.spring.dependency-management` maps to Zolt [platforms] BOM imports and dependency policy.
                  ok  Gradle plugin `jacoco` maps to Zolt coverage command.
                  ok  Gradle plugin `java` maps to Zolt Java source, javac, classpath, and package primitives.
                  ok  Gradle plugin `maven-publish` maps to planned Zolt publication metadata, dry-run, and publish commands.
                  ok  Gradle plugin `org.openapi.generator` maps to Zolt typed OpenAPI generated-source steps.
                  ok  Gradle plugin `org.springframework.boot` maps to Zolt Spring Boot platform, run, test, and executable archive support.
                  ok  Gradle plugin `war` maps to Zolt WAR and Spring Boot WAR package modes.

                What can cache
                  warn  Gradle processResources performs token/resource filtering.

                Non-determinism
                  warn  Gradle repository credentials are resolved inside the build script.
                  warn  Gradle build can read Maven-local artifacts through mavenLocal().

                Migration blockers
                  block  Gradle build mutates dependency policy through excludes, resolutionStrategy, or forced versions.
                  block  Gradle build uses imperative dependency or configuration mutation.
                  block  Gradle bootWar package content is changed with archive excludes.

                Next steps
                  1. Replace global Gradle excludes and forced versions with explicit Zolt dependency policy constraints.
                  2. Replace imperative Gradle logic with explicit Zolt dependencies, platforms, processors, and source roots.
                  3. Replace package archive mutation with Zolt dependency scopes and package placement diagnostics.

                This command inspected Gradle metadata statically and did not execute Gradle.
                """;
    }
}
