package sh.zolt.update;

/** Whether a surface is up to date, has a suggestable newer version, or could not be determined. */
public enum OutdatedStatus {
    CURRENT("current"),
    UPDATE_AVAILABLE("update-available"),
    UNKNOWN("unknown");

    private final String jsonName;

    OutdatedStatus(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }
}
