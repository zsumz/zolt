package sh.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MigrationReadinessFormatterTest {
    @Test
    void scorecardTextIncludesFindingProjectAndMessageOnSummaryLine() {
        MigrationReadinessFinding finding = finding(
                "Dependency `com.acme:lib-one:2.0-SNAPSHOT` uses dynamic version `2.0-SNAPSHOT`.",
                "module-a");
        MigrationReadinessScorecard scorecard = scorecard(finding);

        String text = new MigrationReadinessScorecardFormatter().text(scorecard);

        assertTrue(text.contains(
                "non-deterministic  SNAPSHOT or Maven version range -> fixed versions and [platforms]"
                        + " [project: module-a] - Dependency `com.acme:lib-one:2.0-SNAPSHOT`"
                        + " uses dynamic version `2.0-SNAPSHOT`."), () -> text);
        assertTrue(text.contains("signal: maven.dependency.dynamic-version, severity: block"), () -> text);
    }

    @Test
    void blockerTextIncludesFindingProjectAndMessageOnSummaryLineWithNextStep() {
        MigrationReadinessFinding finding = finding(
                "Dependency `com.acme:lib-one:2.0-SNAPSHOT` uses dynamic version `2.0-SNAPSHOT`.",
                "module-a");

        String text = new MigrationBlockerReportFormatter().text(MigrationBlockerReports.from(scorecard(finding)));

        assertTrue(text.contains(
                "non-deterministic  SNAPSHOT or Maven version range -> fixed versions and [platforms]"
                        + " [project: module-a] - Dependency `com.acme:lib-one:2.0-SNAPSHOT`"
                        + " uses dynamic version `2.0-SNAPSHOT`."), () -> text);
        assertTrue(text.contains("next: Replace Maven ranges or SNAPSHOTs with fixed versions before migrating."), () -> text);
    }

    @Test
    void textReportsKeepOldSummaryLineShapeWhenProjectAndMessageAreBlank() {
        MigrationReadinessFinding finding = finding("", "");
        MigrationReadinessScorecard scorecard = scorecard(finding);

        String scorecardText = new MigrationReadinessScorecardFormatter().text(scorecard);
        String blockerText = new MigrationBlockerReportFormatter().text(MigrationBlockerReports.from(scorecard));

        assertTrue(scorecardText.contains(
                "      non-deterministic  SNAPSHOT or Maven version range -> fixed versions and [platforms]\n"),
                () -> scorecardText);
        assertTrue(blockerText.contains(
                "  non-deterministic  SNAPSHOT or Maven version range -> fixed versions and [platforms]\n"),
                () -> blockerText);
        assertFalse(scorecardText.contains("[project:"), () -> scorecardText);
        assertFalse(blockerText.contains("[project:"), () -> blockerText);
        assertFalse(scorecardText.contains(" - \n"), () -> scorecardText);
        assertFalse(blockerText.contains(" - \n"), () -> blockerText);
    }

    private static MigrationReadinessScorecard scorecard(MigrationReadinessFinding finding) {
        return new MigrationReadinessScorecard(
                "maven",
                Path.of("/repo"),
                "non-deterministic",
                List.of(new MigrationReadinessConcern("dependencies", "non-deterministic", List.of(finding))),
                List.of("Replace Maven ranges or SNAPSHOTs with fixed versions before migrating."));
    }

    private static MigrationReadinessFinding finding(String message, String project) {
        return new MigrationReadinessFinding(
                MigrationReadinessCategory.NON_DETERMINISTIC,
                ExplainSignal.Severity.BLOCK,
                "concern:dependencies SNAPSHOT or Maven version range",
                "fixed versions and [platforms]",
                "",
                message,
                "Replace Maven ranges or SNAPSHOTs with fixed versions before migrating.",
                "maven.dependency.dynamic-version",
                project);
    }
}
