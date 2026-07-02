package sh.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.gradle.GradleExplainFormatter;
import sh.zolt.explain.gradle.GradleInspectionResult;
import sh.zolt.explain.gradle.GradleStaticProjectInspector;
import sh.zolt.explain.maven.MavenExplainFormatter;
import sh.zolt.explain.maven.MavenInspectionResult;
import sh.zolt.explain.maven.MavenStaticProjectInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class MigrationExplainFixtureTest {
    private static final Path FIXTURE_ROOT = MigrationExplainTestPaths.fixtureRoot();

    @Test
    void mavenSimpleFixtureHasDeterministicGoldenTextAndJson() {
        Path fixture = fixture("maven-simple");
        MavenInspectionResult first = new MavenStaticProjectInspector().inspect(fixture);
        MavenInspectionResult second = new MavenStaticProjectInspector().inspect(fixture);
        MavenExplainFormatter formatter = new MavenExplainFormatter();

        assertEquals(MigrationExplainGoldenFixtures.mavenSimpleText(), normalize(formatter.text(first), fixture));
        assertEquals(MigrationExplainGoldenFixtures.mavenSimpleText(), normalize(formatter.text(second), fixture));
        assertEquals(MigrationExplainGoldenFixtures.mavenSimpleJson(), normalize(formatter.json(first), fixture));
        assertEquals(MigrationExplainGoldenFixtures.mavenSimpleJson(), normalize(formatter.json(second), fixture));
    }

    @Test
    void gradleSimpleFixtureHasDeterministicGoldenTextAndJson() {
        Path fixture = fixture("gradle-simple");
        GradleInspectionResult first = new GradleStaticProjectInspector().inspect(fixture);
        GradleInspectionResult second = new GradleStaticProjectInspector().inspect(fixture);
        GradleExplainFormatter formatter = new GradleExplainFormatter();

        assertEquals(MigrationExplainGoldenFixtures.gradleSimpleText(), normalize(formatter.text(first), fixture));
        assertEquals(MigrationExplainGoldenFixtures.gradleSimpleText(), normalize(formatter.text(second), fixture));
        assertEquals(MigrationExplainGoldenFixtures.gradleSimpleJson(), normalize(formatter.json(first), fixture));
        assertEquals(MigrationExplainGoldenFixtures.gradleSimpleJson(), normalize(formatter.json(second), fixture));
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

        assertEquals(MigrationExplainGoldenFixtures.gradleEnterpriseSpringText(), text);
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

}
