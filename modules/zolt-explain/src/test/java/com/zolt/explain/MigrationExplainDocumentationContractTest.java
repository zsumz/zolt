package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MigrationExplainDocumentationContractTest {
    private static final Path DOCUMENT = MigrationExplainTestPaths.fixtureRoot()
            .getParent()
            .getParent()
            .resolve("docs/migration-explain.md");

    @Test
    void jsonReportExamplesUseCurrentSummaryAndDependencyFields() throws IOException {
        String document = Files.readString(DOCUMENT);
        String report = section(document, "## JSON Report", "## Readiness Scorecard");

        assertTrue(report.contains("\"projects\": 1"), () -> report);
        assertTrue(report.contains("\"signals\": 1"), () -> report);
        assertTrue(report.contains("\"includedProjects\": 0"), () -> report);
        assertTrue(report.contains("\"versionCatalogAliases\": 0"), () -> report);
        assertTrue(report.contains("\"version\": \"33.4.8-jre\""), () -> report);
        assertTrue(report.contains("\"type\": \"jar\""), () -> report);
        assertTrue(report.contains("\"optional\": false"), () -> report);
        assertTrue(report.contains("\"managed\": false"), () -> report);
        assertTrue(report.contains("\"importedBom\": false"), () -> report);
        assertFalse(report.contains("\"versionSource\""), () -> report);
    }

    @Test
    void jsonExamplesUseRealSignalIdsAndFormatterSeverities() throws IOException {
        String document = Files.readString(DOCUMENT);
        String scorecard = section(document, "## Readiness Scorecard", "## Blocker Report");

        assertTrue(document.contains("\"id\": \"maven.profile.detected\""), () -> document);
        assertFalse(document.contains("maven.profile.environment-activation"), () -> document);
        assertFalse(document.contains("\"severity\": \"info\""), () -> document);
        assertTrue(scorecard.contains("\"severity\": \"warn\""), () -> scorecard);
        assertTrue(scorecard.contains("\"checklist\": []"), () -> scorecard);
    }

    private static String section(String document, String startHeading, String endHeading) {
        int start = document.indexOf(startHeading);
        int end = document.indexOf(endHeading, start + startHeading.length());
        if (start < 0 || end < 0) {
            throw new AssertionError("Could not find section " + startHeading + " before " + endHeading);
        }
        return document.substring(start, end);
    }
}
