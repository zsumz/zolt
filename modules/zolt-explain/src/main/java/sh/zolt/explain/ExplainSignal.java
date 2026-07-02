package sh.zolt.explain;

public record ExplainSignal(
        Severity severity,
        Category category,
        String project,
        String id,
        String message,
        String nextStep) {
    public enum Severity {
        OK,
        WARN,
        BLOCK,
        UNKNOWN
    }

    public enum Category {
        BUILDABILITY,
        CACHEABILITY,
        NON_DETERMINISM,
        MIGRATION_BLOCKER
    }
}
