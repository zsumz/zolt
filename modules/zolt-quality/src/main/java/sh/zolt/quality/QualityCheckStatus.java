package sh.zolt.quality;

public enum QualityCheckStatus {
    PASSED("passed", "ok"),
    WARNING("warning", "warning"),
    FAILED("failed", "error"),
    SKIPPED("skipped", "skip");

    private final String jsonValue;
    private final String marker;

    QualityCheckStatus(String jsonValue, String marker) {
        this.jsonValue = jsonValue;
        this.marker = marker;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public String marker() {
        return marker;
    }
}
