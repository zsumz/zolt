package com.zolt.explain.maven;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.MigrationReadinessCategory;
import com.zolt.explain.MigrationReadinessFinding;
import com.zolt.explain.MigrationReadinessFindings;
import java.util.Locale;

public final class MavenMigrationReadinessFindings {
    private MavenMigrationReadinessFindings() {
    }

    public static MigrationReadinessFinding map(ExplainSignal signal) {
        return switch (signal.id()) {
            case "maven.module.missing-pom" -> MigrationReadinessFindings.finding(
                    "dependencies",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "module listed in <modules> without a readable pom.xml",
                    "workspace members",
                    "",
                    signal.nextStep());
            case "maven.packaging.unsupported" -> MigrationReadinessFindings.finding(
                    "package",
                    MigrationReadinessCategory.UNSUPPORTED,
                    signal,
                    "<packaging> value without a Zolt package mode",
                    "[package].mode",
                    "",
                    "Replace this packaging with a supported Zolt package mode or keep it outside MVP migration scope.");
            case "maven.dependency.dynamic-version" -> MigrationReadinessFindings.finding(
                    "dependencies",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "SNAPSHOT or Maven version range",
                    "fixed versions and [platforms]",
                    "",
                    signal.nextStep());
            case "maven.plugin.lifecycle-binding" -> mapPlugin(
                    signal,
                    MigrationReadinessCategory.BLOCKED,
                    "Maven plugin bound to lifecycle phase");
            case "maven.plugin.static-signal" -> mapPlugin(
                    signal,
                    MigrationReadinessCategory.PLANNED,
                    "Maven plugin declared for static migration review");
            case "maven.reactor.detected" -> MigrationReadinessFindings.finding(
                    "dependencies",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "multi-module reactor (pom aggregator with <modules>)",
                    "[workspace] with one member draft per module",
                    "",
                    signal.nextStep());
            case "maven.annotation-processor.path" -> MigrationReadinessFindings.finding(
                    "generated-sources",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "maven-compiler-plugin annotationProcessorPaths",
                    "[annotationProcessors]",
                    "",
                    signal.nextStep());
            case "maven.profile.detected" -> MigrationReadinessFindings.finding(
                    "ci",
                    MigrationReadinessCategory.NON_DETERMINISTIC,
                    signal,
                    "Maven profile activation",
                    "execution context policy",
                    "",
                    signal.nextStep());
            default -> MigrationReadinessFindings.generic(signal);
        };
    }

    private static MigrationReadinessFinding mapPlugin(
            ExplainSignal signal,
            MigrationReadinessCategory category,
            String fallbackPattern) {
        String coordinate = pluginCoordinate(signal);
        if (matchesAny(coordinate, "jacoco-maven-plugin", "cobertura-maven-plugin")) {
            return MigrationReadinessFindings.finding(
                    "coverage",
                    category,
                    signal,
                    "Maven coverage plugin",
                    "zolt coverage",
                    "",
                    "Map Maven coverage configuration to explicit Zolt coverage commands and reports.");
        }
        if (matchesAny(coordinate,
                "maven-jar-plugin",
                "maven-war-plugin",
                "maven-assembly-plugin",
                "maven-shade-plugin",
                "maven-bundle-plugin",
                "spring-boot-maven-plugin")) {
            return MigrationReadinessFindings.finding(
                    "package",
                    category,
                    signal,
                    "Maven package plugin",
                    "[package]",
                    "",
                    "Model the Maven packaging plugin configuration as explicit Zolt package settings.");
        }
        if (matchesAny(coordinate,
                "maven-deploy-plugin",
                "maven-gpg-plugin",
                "maven-scm-publish-plugin",
                "nexus-staging-maven-plugin",
                "central-publishing-maven-plugin")) {
            return MigrationReadinessFindings.finding(
                    "publish",
                    category,
                    signal,
                    "Maven publish/signing plugin",
                    "[publish] and zolt publish --dry-run",
                    "",
                    "Map Maven publication, signing, and deployment metadata to Zolt publish settings and credential policy.");
        }
        if (matchesAny(coordinate,
                "maven-surefire-plugin",
                "maven-failsafe-plugin",
                "junit-platform-maven-plugin")) {
            return MigrationReadinessFindings.finding(
                    "tests",
                    category,
                    signal,
                    "Maven test execution plugin",
                    "[test] and integration-test settings",
                    "",
                    "Map Maven test execution settings to explicit Zolt test runtime configuration.");
        }
        if (matchesAny(coordinate,
                "antlr4-maven-plugin",
                "javacc-maven-plugin",
                "ph-javacc-maven-plugin",
                "openapi-generator-maven-plugin",
                "protobuf-maven-plugin",
                "jaxb2-maven-plugin",
                "jaxb-maven-plugin",
                "jooq-codegen-maven",
                "build-helper-maven-plugin")) {
            return MigrationReadinessFindings.finding(
                    "generated-sources",
                    category,
                    signal,
                    "Maven generated-source plugin",
                    "[generatedSources]",
                    "",
                    "Model generated sources as typed Zolt generated-source steps with explicit inputs and outputs.");
        }
        return MigrationReadinessFindings.finding(
                "ci",
                category,
                signal,
                fallbackPattern,
                "explicit Zolt command or project model",
                "",
                signal.nextStep());
    }

    private static String pluginCoordinate(ExplainSignal signal) {
        String prefix = "Plugin `";
        int start = signal.message().indexOf(prefix);
        if (start < 0) {
            return "";
        }
        int coordinateStart = start + prefix.length();
        int coordinateEnd = signal.message().indexOf('`', coordinateStart);
        if (coordinateEnd <= coordinateStart) {
            return "";
        }
        return signal.message().substring(coordinateStart, coordinateEnd).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesAny(String coordinate, String... artifactIds) {
        for (String artifactId : artifactIds) {
            if (coordinate.contains(":" + artifactId) || coordinate.endsWith(artifactId)) {
                return true;
            }
        }
        return false;
    }
}
