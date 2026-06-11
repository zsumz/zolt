package com.zolt.explain;

public record MigrationReadinessFinding(
        MigrationReadinessCategory category,
        ExplainSignal.Severity severity,
        String sourcePattern,
        String zoltPrimitive,
        String followUp,
        String message,
        String nextStep,
        String signalId,
        String project) {
}
