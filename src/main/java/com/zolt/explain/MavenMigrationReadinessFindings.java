package com.zolt.explain;

final class MavenMigrationReadinessFindings {
    private MavenMigrationReadinessFindings() {
    }

    static MigrationReadinessFinding map(ExplainSignal signal) {
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
            case "maven.plugin.lifecycle-binding" -> MigrationReadinessFindings.finding(
                    "generated-sources",
                    MigrationReadinessCategory.BLOCKED,
                    signal,
                    "Maven plugin bound to lifecycle phase",
                    "typed Zolt command or generated-source/resource/package primitive",
                    "",
                    signal.nextStep());
            case "maven.plugin.static-signal" -> MigrationReadinessFindings.finding(
                    "tests",
                    MigrationReadinessCategory.PLANNED,
                    signal,
                    "known Maven plugin signal",
                    "explicit Zolt test/package/generation settings",
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
}
