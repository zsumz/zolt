package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.ExplainSignal;
import sh.zolt.explain.MigrationReadinessCategory;
import sh.zolt.explain.MigrationReadinessFinding;
import org.junit.jupiter.api.Test;

final class GradleMigrationReadinessFindingsTest {
    @Test
    void enterprisePluginMessagesMapToSpecificZoltPrimitives() {
        assertPluginMapped(
                "id 'jacoco' with jacocoTestReport",
                MigrationReadinessCategory.SUPPORTED,
                "coverage",
                "id 'jacoco' and jacocoTestReport",
                "zolt coverage",
                "",
                "coverage commands");
        assertPluginMapped(
                "id 'maven-publish'",
                MigrationReadinessCategory.PLANNED,
                "publish",
                "id 'maven-publish'",
                "[publish] and zolt publish --dry-run",
                "",
                "publication metadata");
        assertPluginMapped(
                "id 'org.openapi.generator'",
                MigrationReadinessCategory.SUPPORTED,
                "generated-sources",
                "id 'org.openapi.generator'",
                "kind = \"openapi\" generated-source steps",
                "",
                "typed Zolt generated-source steps");
        assertPluginMapped(
                "id 'org.springframework.boot'",
                MigrationReadinessCategory.SUPPORTED,
                "package",
                "Spring Boot or WAR Gradle plugin",
                "spring-boot, war, and spring-boot-war package modes",
                "",
                "package placement");
        assertPluginMapped(
                "id 'io.spring.dependency-management'",
                MigrationReadinessCategory.SUPPORTED,
                "dependencies",
                "io.spring.dependency-management BOM imports",
                "[platforms]",
                "",
                "Zolt platforms");
        assertPluginMapped(
                "id 'com.acme.enterprise'",
                MigrationReadinessCategory.SUPPORTED,
                "dependencies",
                "recognized Gradle plugin",
                "Zolt-owned Java build primitive",
                "",
                "Inspect the plugin mapping.");
    }

    @Test
    void directGradleSignalsKeepSpecificCategoriesFollowUpsAndNextSteps() {
        MigrationReadinessFinding buildFileName = GradleMigrationReadinessFindings.map(signal(
                "gradle.project.build-file-name-unresolved",
                "settings buildFileName uses a provider.",
                "Replace the dynamic buildFileName assignment."));
        MigrationReadinessFinding unresolvedDependency = GradleMigrationReadinessFindings.map(signal(
                "gradle.dependency.unresolved-notation",
                "Dependency notation is computed.",
                "Replace it with an explicit dependency."));

        assertEquals(MigrationReadinessCategory.PLANNED, buildFileName.category());
        assertEquals("concern:dependencies settings buildFileName mutation with candidate build file",
                buildFileName.sourcePattern());
        assertEquals("workspace members", buildFileName.zoltPrimitive());
        assertEquals("", buildFileName.followUp());
        assertEquals("Replace the dynamic buildFileName assignment.", buildFileName.nextStep());

        assertEquals(MigrationReadinessCategory.UNKNOWN, unresolvedDependency.category());
        assertEquals("concern:dependencies unresolved Gradle dependency expression",
                unresolvedDependency.sourcePattern());
        assertEquals("explicit [dependencies]", unresolvedDependency.zoltPrimitive());
        assertEquals("", unresolvedDependency.followUp());
        assertEquals("Replace it with an explicit dependency.", unresolvedDependency.nextStep());
    }

    private static void assertPluginMapped(
            String message,
            MigrationReadinessCategory category,
            String concern,
            String sourcePattern,
            String primitive,
            String followUp,
            String nextStepFragment) {
        MigrationReadinessFinding finding = GradleMigrationReadinessFindings.map(
                signal("gradle.enterprise-plugin.mapped", message, "Inspect the plugin mapping."));

        assertEquals(category, finding.category());
        assertEquals("concern:" + concern + " " + sourcePattern, finding.sourcePattern());
        assertEquals(primitive, finding.zoltPrimitive());
        assertEquals(followUp, finding.followUp());
        assertTrue(finding.nextStep().contains(nextStepFragment), finding::nextStep);
        assertEquals("gradle.enterprise-plugin.mapped", finding.signalId());
        assertEquals("app", finding.project());
    }

    private static ExplainSignal signal(String id, String message, String nextStep) {
        return new ExplainSignal(
                ExplainSignal.Severity.WARN,
                ExplainSignal.Category.MIGRATION_BLOCKER,
                "app",
                id,
                message,
                nextStep);
    }
}
