package com.zolt.cli.console;

import java.util.Locale;
import java.util.Map;

public enum ColorMode {
    AUTO("auto"),
    ALWAYS("always"),
    NEVER("never");

    private final String label;

    ColorMode(String label) {
        this.label = label;
    }

    public boolean enabled(boolean interactive, Map<String, String> environment) {
        return switch (this) {
            case AUTO -> interactive && !environment.containsKey("NO_COLOR");
            case ALWAYS -> true;
            case NEVER -> false;
        };
    }

    @Override
    public String toString() {
        return label;
    }

    public static ColorMode from(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ColorMode mode : values()) {
            if (mode.label.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown color mode `" + value + "`.");
    }
}
