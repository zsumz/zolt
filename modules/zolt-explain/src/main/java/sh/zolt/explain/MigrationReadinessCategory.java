package sh.zolt.explain;

public enum MigrationReadinessCategory {
    SUPPORTED("supported"),
    UNKNOWN("unknown"),
    PLANNED("planned"),
    BLOCKED("blocked"),
    NON_DETERMINISTIC("non-deterministic"),
    UNSUPPORTED("unsupported");

    private final String label;

    MigrationReadinessCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
