package com.zolt.project;

import java.util.Locale;

public final class VersionAliasRules {
    private VersionAliasRules() {}

    public static boolean isValidName(String alias) {
        if (alias == null || alias.isBlank() || !alias.equals(alias.trim())) {
            return false;
        }
        for (int index = 0; index < alias.length(); index++) {
            char character = alias.charAt(index);
            if (!isAsciiLetterOrDigit(character)
                    && character != '.'
                    && character != '_'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidValue(String version) {
        if (version == null || version.isBlank() || !version.equals(version.trim())) {
            return false;
        }
        return !containsInterpolation(version)
                && !isSnapshot(version)
                && !isVersionRange(version)
                && !isDynamicVersion(version);
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9');
    }

    private static boolean containsInterpolation(String version) {
        return version.contains("${")
                || (version.length() > 2 && version.startsWith("@") && version.endsWith("@"));
    }

    private static boolean isSnapshot(String version) {
        return version.endsWith("-SNAPSHOT");
    }

    private static boolean isVersionRange(String version) {
        return version.length() >= 3
                && (version.charAt(0) == '[' || version.charAt(0) == '(')
                && (version.endsWith("]") || version.endsWith(")"))
                && version.contains(",");
    }

    private static boolean isDynamicVersion(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.equals("+")
                || lower.endsWith(".+")
                || lower.equals("latest")
                || lower.equals("latest.release")
                || lower.equals("latest.integration")
                || lower.equals("release");
    }
}
