package com.zolt.explain;

final class MigrationReadinessFindings {
    private MigrationReadinessFindings() {
    }

    static MigrationReadinessFinding defaultFinding(
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

    static MigrationReadinessFinding generic(ExplainSignal signal) {
        MigrationReadinessCategory category = switch (signal.category()) {
            case NON_DETERMINISM -> MigrationReadinessCategory.NON_DETERMINISTIC;
            case MIGRATION_BLOCKER -> MigrationReadinessCategory.BLOCKED;
            case BUILDABILITY, CACHEABILITY -> signal.severity() == ExplainSignal.Severity.BLOCK
                    ? MigrationReadinessCategory.BLOCKED
                    : MigrationReadinessCategory.PLANNED;
        };
        return finding("dependencies", category, signal, signal.id(), "explicit Zolt model", "", signal.nextStep());
    }

    static MigrationReadinessFinding finding(
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
}
