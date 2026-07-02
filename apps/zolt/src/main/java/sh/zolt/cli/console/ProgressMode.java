package sh.zolt.cli.console;

import java.util.Locale;

public enum ProgressMode {
    AUTO("auto"),
    ALWAYS("always"),
    NEVER("never");

    private final String label;

    ProgressMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static ProgressMode from(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ProgressMode mode : values()) {
            if (mode.label.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown progress mode `" + value + "`.");
    }
}
