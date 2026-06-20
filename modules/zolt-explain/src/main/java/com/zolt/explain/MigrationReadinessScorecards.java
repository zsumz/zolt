package com.zolt.explain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MigrationReadinessScorecards {
    private static final List<String> CONCERNS = List.of(
            "repositories",
            "dependencies",
            "generated-sources",
            "resources",
            "tests",
            "coverage",
            "package",
            "publish",
            "ci");

    private MigrationReadinessScorecards() {
    }

    public static MigrationReadinessScorecard from(MavenInspectionResult result) {
        List<MigrationReadinessFinding> findings = new ArrayList<>();
        findings.add(MigrationReadinessFindings.defaultFinding("repositories", "pom repositories", "[repositories]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("dependencies", "dependencies and dependencyManagement", "[dependencies] and [platforms]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("generated-sources", "standard Maven source roots", "[generatedSources]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("resources", "standard Maven resource roots", "[resources]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("tests", "standard Maven test source roots", "[test]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("coverage", "no static coverage plugin required", "none required", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("package", "Maven jar/war packaging metadata", "[package]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("publish", "publication metadata in pom.xml", "[publish]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("ci", "locked/offline Zolt command sequence", "zolt check --target ci", ""));
        for (ExplainSignal signal : result.signals()) {
            findings.add(MavenMigrationReadinessFindings.map(signal));
        }
        return build("maven", result.root(), findings);
    }

    public static MigrationReadinessScorecard from(GradleInspectionResult result) {
        List<MigrationReadinessFinding> findings = new ArrayList<>();
        findings.add(MigrationReadinessFindings.defaultFinding("repositories", "repositories { mavenCentral() / maven { url ... } }", "[repositories]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("dependencies", "simple dependency declarations and BOMs", "[dependencies], [platforms], and dependency policy", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("generated-sources", "declared sourceSets generated roots", "[generatedSources]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("resources", "declared resource roots", "[resources]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("tests", "JUnit Platform test execution", "[test]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("coverage", "coverage is explicit, not finalizedBy task behavior", "zolt coverage", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("package", "jar, war, Spring Boot jar, and Spring Boot WAR package modes", "[package]", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("publish", "publication metadata and dry-run routing", "[publish] and zolt publish --dry-run", ""));
        findings.add(MigrationReadinessFindings.defaultFinding("ci", "locked/offline Zolt command sequence", "zolt check --target ci", ""));
        for (ExplainSignal signal : result.signals()) {
            findings.add(GradleMigrationReadinessFindings.map(signal));
        }
        return build("gradle", result.root(), findings);
    }

    private static MigrationReadinessScorecard build(
            String source,
            Path root,
            List<MigrationReadinessFinding> findings) {
        List<MigrationReadinessConcern> concerns = CONCERNS.stream()
                .map(name -> concern(name, findings))
                .toList();
        return new MigrationReadinessScorecard(source, root, status(concerns), concerns, checklist(concerns));
    }

    private static MigrationReadinessConcern concern(String name, List<MigrationReadinessFinding> findings) {
        List<MigrationReadinessFinding> concernFindings = findings.stream()
                .filter(finding -> concernFor(finding).equals(name))
                .sorted(Comparator
                        .comparingInt((MigrationReadinessFinding finding) -> categoryRank(finding.category()))
                        .thenComparingInt(finding -> severityRank(finding.severity()))
                        .thenComparing(MigrationReadinessFinding::sourcePattern)
                        .thenComparing(MigrationReadinessFinding::zoltPrimitive)
                        .thenComparing(MigrationReadinessFinding::followUp)
                        .thenComparing(MigrationReadinessFinding::message))
                .toList();
        return new MigrationReadinessConcern(name, concernStatus(concernFindings), concernFindings);
    }

    private static String concernFor(MigrationReadinessFinding finding) {
        String sourcePattern = finding.sourcePattern();
        if (sourcePattern.startsWith("concern:")) {
            int end = sourcePattern.indexOf(' ');
            return sourcePattern.substring("concern:".length(), end);
        }
        return "dependencies";
    }

    private static String status(List<MigrationReadinessConcern> concerns) {
        if (concerns.stream().anyMatch(concern -> concern.status().equals("blocked")
                || concern.status().equals("unsupported"))) {
            return "blocked";
        }
        if (concerns.stream().anyMatch(concern -> concern.status().equals("non-deterministic"))) {
            return "non-deterministic";
        }
        if (concerns.stream().anyMatch(concern -> concern.status().equals("planned"))) {
            return "planned";
        }
        return "supported";
    }

    private static String concernStatus(List<MigrationReadinessFinding> findings) {
        if (findings.stream().anyMatch(finding -> finding.category() == MigrationReadinessCategory.UNSUPPORTED)) {
            return "unsupported";
        }
        if (findings.stream().anyMatch(finding -> finding.category() == MigrationReadinessCategory.BLOCKED)) {
            return "blocked";
        }
        if (findings.stream().anyMatch(finding -> finding.category() == MigrationReadinessCategory.NON_DETERMINISTIC)) {
            return "non-deterministic";
        }
        if (findings.stream().anyMatch(finding -> finding.category() == MigrationReadinessCategory.PLANNED)) {
            return "planned";
        }
        return "supported";
    }

    private static List<String> checklist(List<MigrationReadinessConcern> concerns) {
        return concerns.stream()
                .flatMap(concern -> concern.findings().stream())
                .filter(finding -> finding.category() != MigrationReadinessCategory.SUPPORTED)
                .map(MigrationReadinessFinding::nextStep)
                .distinct()
                .sorted()
                .toList();
    }

    private static int categoryRank(MigrationReadinessCategory category) {
        return switch (category) {
            case BLOCKED -> 0;
            case UNSUPPORTED -> 1;
            case NON_DETERMINISTIC -> 2;
            case PLANNED -> 3;
            case SUPPORTED -> 4;
        };
    }

    private static int severityRank(ExplainSignal.Severity severity) {
        return switch (severity) {
            case BLOCK -> 0;
            case UNKNOWN -> 1;
            case WARN -> 2;
            case OK -> 3;
        };
    }
}
