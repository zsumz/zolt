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
        findings.add(defaultFinding("repositories", "pom repositories", "[repositories]", ""));
        findings.add(defaultFinding("dependencies", "dependencies and dependencyManagement", "[dependencies] and [platforms]", ""));
        findings.add(defaultFinding("generated-sources", "standard Maven source roots", "[generatedSources]", ""));
        findings.add(defaultFinding("resources", "standard Maven resource roots", "[resources]", ""));
        findings.add(defaultFinding("tests", "standard Maven test source roots", "[test]", ""));
        findings.add(defaultFinding("coverage", "no static coverage plugin required", "none required", ""));
        findings.add(defaultFinding("package", "Maven jar/war packaging metadata", "[package]", ""));
        findings.add(defaultFinding("publish", "publication metadata in pom.xml", "[publish]", ""));
        findings.add(defaultFinding("ci", "locked/offline Zolt command sequence", "zolt check --target ci", ""));
        for (ExplainSignal signal : result.signals()) {
            findings.add(mapMaven(signal));
        }
        return build("maven", result.root(), findings);
    }

    public static MigrationReadinessScorecard from(GradleInspectionResult result) {
        List<MigrationReadinessFinding> findings = new ArrayList<>();
        findings.add(defaultFinding("repositories", "repositories { mavenCentral() / maven { url ... } }", "[repositories]", ""));
        findings.add(defaultFinding("dependencies", "simple dependency declarations and BOMs", "[dependencies], [platforms], and dependency policy", ""));
        findings.add(defaultFinding("generated-sources", "declared sourceSets generated roots", "[generatedSources]", ""));
        findings.add(defaultFinding("resources", "declared resource roots", "[resources]", ""));
        findings.add(defaultFinding("tests", "JUnit Platform test execution", "[test]", ""));
        findings.add(defaultFinding("coverage", "coverage is explicit, not finalizedBy task behavior", "zolt coverage", ""));
        findings.add(defaultFinding("package", "jar, war, Spring Boot jar, and Spring Boot WAR package modes", "[package]", ""));
        findings.add(defaultFinding("publish", "publication metadata and dry-run routing", "[publish] and zolt publish --dry-run", ""));
        findings.add(defaultFinding("ci", "locked/offline Zolt command sequence", "zolt check --target ci", ""));
        for (ExplainSignal signal : result.signals()) {
            findings.add(mapGradle(signal));
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

    private static MigrationReadinessFinding defaultFinding(
            String concern,
            String sourcePattern,
            String zoltPrimitive,
            String followUp) {
        return new MigrationReadinessFinding(
                MigrationReadinessCategory.SUPPORTED,
                ExplainSignal.Severity.OK,
                "concern:" + concern + " " + sourcePattern,
                zoltPrimitive,
                followUp,
                "Zolt has a native model for this concern or no migration work is required in the inspected project.",
                "Represent this concern directly in zolt.toml and verify with zolt explain, zolt plan, and zolt check.",
                "",
                "");
    }

    private static MigrationReadinessFinding mapMaven(ExplainSignal signal) {
        return switch (signal.id()) {
            case "maven.module.missing-pom" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "module listed in <modules> without a readable pom.xml",
                    "workspace members",
                    "",
                    signal.nextStep());
            case "maven.packaging.unsupported" -> finding(
                    "package",
                    MigrationReadinessCategory.UNSUPPORTED,
                    signal,
                    "<packaging> value without a Zolt package mode",
                    "[package].mode",
                    "",
                    "Replace this packaging with a supported Zolt package mode or keep it outside MVP migration scope.");
            case "maven.dependency.dynamic-version" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "SNAPSHOT or Maven version range",
                    "fixed versions and [platforms]",
                    "",
                    signal.nextStep());
            case "maven.plugin.lifecycle-binding" -> finding(
                    "generated-sources",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "Maven plugin bound to lifecycle phase",
                    "typed Zolt command or generated-source/resource/package primitive",
                    "",
                    signal.nextStep());
            case "maven.plugin.static-signal" -> finding(
                    "tests",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "known Maven plugin signal",
                    "explicit Zolt test/package/generation settings",
                    "",
                    signal.nextStep());
            case "maven.profile.detected" -> finding(
                    "ci",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "Maven profile activation",
                    "execution context policy",
                    "",
                    signal.nextStep());
            default -> generic(signal);
        };
    }

    private static MigrationReadinessFinding mapGradle(ExplainSignal signal) {
        return switch (signal.id()) {
            case "gradle.build-src.detected" -> finding(
                    "ci",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "buildSrc executable build logic",
                    "explicit Zolt project model",
                    "",
                    signal.nextStep());
            case "gradle.project.missing-build-file" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "settings include without readable build file",
                    "workspace members",
                    "",
                    signal.nextStep());
            case "gradle.plugin.convention" -> finding(
                    "ci",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "convention plugin",
                    "explicit Zolt project model",
                    "",
                    signal.nextStep());
            case "gradle.included-build.detected" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "includeBuild(...)",
                    "workspace member or external dependency",
                    "",
                    signal.nextStep());
            case "gradle.imperative-dependency-logic" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "imperative dependency or configuration mutation",
                    "[dependencies], classpath lanes, processors, and generated roots",
                    "",
                    signal.nextStep());
            case "gradle.dependency.dynamic-version" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "dynamic Gradle version",
                    "fixed versions, [versions], and [platforms]",
                    "",
                    signal.nextStep());
            case "gradle.cross-project-build-logic" -> finding(
                    "ci",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "allprojects/subprojects shared mutable build logic",
                    "explicit per-member workspace configuration",
                    "",
                    signal.nextStep());
            case "gradle.custom-task.detected" -> finding(
                    "ci",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "tasks.register(...) or task callbacks",
                    "typed Zolt command primitives",
                    "",
                    signal.nextStep());
            case "gradle.version-catalog.malformed" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "malformed gradle/libs.versions.toml",
                    "[versions] and [platforms]",
                    "",
                    signal.nextStep());
            case "gradle.enterprise-plugin.mapped" -> mapGradlePlugin(signal);
            case "gradle.repository.credentials" -> finding(
                    "repositories",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "credentials resolved from Gradle properties, env, or defaults",
                    "[repositories] credential identities",
                    "",
                    signal.nextStep());
            case "gradle.repository.maven-local" -> finding(
                    "repositories",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "mavenLocal() property switch",
                    "local repository overlays",
                    "",
                    signal.nextStep());
            case "gradle.dependency-policy.mutation" -> finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "configurations.all, excludes, force, or resolutionStrategy",
                    "[dependencyPolicy] and [dependencyConstraints]",
                    "",
                    signal.nextStep());
            case "gradle.openapi.generated-sources" -> finding(
                    "generated-sources",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "OpenAPI GenerateTask wired into sourceSets",
                    "kind = \"openapi\" generated-source steps",
                    "",
                    signal.nextStep());
            case "gradle.resource-filtering" -> finding(
                    "resources",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "processResources filter(ReplaceTokens, ...)",
                    "[resources.filtering] and [resources.tokens]",
                    "",
                    signal.nextStep());
            case "gradle.test-runtime-settings" -> finding(
                    "tests",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "test { systemProperty, environment, testLogging }",
                    "[test.runtime], reports, and event output",
                    "",
                    signal.nextStep());
            case "gradle.package.archive-mutation" -> finding(
                    "package",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "bootWar archive mutation",
                    "package placement policy",
                    "",
                    signal.nextStep());
            case "gradle.publication.detected" -> finding(
                    "publish",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "publishing { publications; repositories }",
                    "[publish] and zolt publish --dry-run",
                    "",
                    signal.nextStep());
            default -> generic(signal);
        };
    }

    private static MigrationReadinessFinding mapGradlePlugin(ExplainSignal signal) {
        String message = signal.message();
        if (message.contains("jacoco")) {
            return finding(
                    "coverage",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "id 'jacoco' and jacocoTestReport",
                    "zolt coverage",
                    "",
                    "Use explicit Zolt coverage commands instead of wiring coverage through test task finalizers.");
        }
        if (message.contains("maven-publish")) {
            return finding(
                    "publish",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "id 'maven-publish'",
                    "[publish] and zolt publish --dry-run",
                    "",
                    "Map Maven Publish configuration to Zolt publication metadata, dry-run routing, and credential policy.");
        }
        if (message.contains("org.openapi.generator")) {
            return finding(
                    "generated-sources",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "id 'org.openapi.generator'",
                    "kind = \"openapi\" generated-source steps",
                    "",
                    "Model OpenAPI generation as typed Zolt generated-source steps instead of Gradle GenerateTask closures.");
        }
        if (message.contains("org.springframework.boot") || message.contains("war")) {
            return finding(
                    "package",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "Spring Boot or WAR Gradle plugin",
                    "spring-boot, war, and spring-boot-war package modes",
                    "",
                    "Declare the package mode and verify package placement with zolt package --plan.");
        }
        if (message.contains("io.spring.dependency-management")) {
            return finding(
                    "dependencies",
                    MigrationReadinessCategory.SUPPORTED,
                    signal,
                    "io.spring.dependency-management BOM imports",
                    "[platforms]",
                    "",
                    "Declare BOMs as Zolt platforms and verify the resolved lockfile.");
        }
        return finding(
                "dependencies",
                MigrationReadinessCategory.SUPPORTED,
                signal,
                "recognized Gradle plugin",
                "Zolt-owned Java build primitive",
                "",
                signal.nextStep());
    }

    private static MigrationReadinessFinding generic(ExplainSignal signal) {
        MigrationReadinessCategory category = switch (signal.category()) {
            case NON_DETERMINISM -> MigrationReadinessCategory.NON_DETERMINISTIC;
            case MIGRATION_BLOCKER -> MigrationReadinessCategory.BLOCKED;
            case BUILDABILITY, CACHEABILITY -> signal.severity() == ExplainSignal.Severity.BLOCK
                    ? MigrationReadinessCategory.BLOCKED
                    : MigrationReadinessCategory.PLANNED;
        };
        return finding("dependencies", category, signal, signal.id(), "explicit Zolt model", "", signal.nextStep());
    }

    private static MigrationReadinessFinding finding(
            String concern,
            MigrationReadinessCategory category,
            ExplainSignal signal,
            String sourcePattern,
            String zoltPrimitive,
            String followUp,
            String nextStep) {
        return new MigrationReadinessFinding(
                category,
                signal.severity(),
                "concern:" + concern + " " + sourcePattern,
                zoltPrimitive,
                followUp,
                signal.message(),
                nextStep,
                signal.id(),
                signal.project());
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
