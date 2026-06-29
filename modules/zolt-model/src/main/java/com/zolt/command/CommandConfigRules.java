package com.zolt.command;

import java.nio.file.Path;
import java.util.Locale;

final class CommandConfigRules {
    private CommandConfigRules() {
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isBlank() || !name.equals(name.trim()) || name.startsWith("-")) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!isAsciiLetterOrDigit(character)
                    && character != '.'
                    && character != '_'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidEnvironmentName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        char first = name.charAt(0);
        if (!isAsciiLetter(first) && first != '_') {
            return false;
        }
        for (int index = 1; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!isAsciiLetterOrDigit(character) && character != '_') {
                return false;
            }
        }
        return true;
    }

    public static boolean isEnvironmentAssignmentPrefix(String value) {
        if (value == null) {
            return false;
        }
        int equals = value.indexOf('=');
        return equals > 0 && isValidEnvironmentName(value.substring(0, equals));
    }

    public static boolean isExecutablePath(String value) {
        return value != null && (value.contains("/") || value.contains("\\"));
    }

    public static boolean isShellExecutable(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        return switch (normalized) {
            case "sh", "bash", "zsh", "fish", "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "pwsh.exe" -> true;
            default -> false;
        };
    }

    public static boolean looksLikeShellString(String value) {
        return value != null
                && (value.contains("&&")
                        || value.contains("||")
                        || value.contains(";")
                        || value.contains("|")
                        || value.contains("$(")
                        || value.contains("`"));
    }

    public static boolean isRelativeNormalizedPath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return false;
        }
        Path normalized = path.normalize();
        if (normalized.startsWith("..")) {
            return false;
        }
        return normalized.toString().equals(value);
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return isAsciiLetter(character) || (character >= '0' && character <= '9');
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }
}
