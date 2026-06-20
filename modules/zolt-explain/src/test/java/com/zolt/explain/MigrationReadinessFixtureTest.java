package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MigrationReadinessFixtureTest {
    private static final Path FIXTURE_ROOT = MigrationExplainTestPaths.fixtureRoot();

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
}
