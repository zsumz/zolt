package com.zolt.explain.emit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaVersionNotation {
    private static final Pattern LEGACY_FEATURE = Pattern.compile("1\\.(\\d+)");

    private JavaVersionNotation() {
    }

    static String normalizeLegacyFeature(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        Matcher matcher = LEGACY_FEATURE.matcher(stripped);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return value;
    }
}
