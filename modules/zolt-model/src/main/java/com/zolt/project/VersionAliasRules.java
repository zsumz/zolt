package com.zolt.project;

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
        return VersionPolicy.isSupported(VersionPolicy.Context.VERSION_ALIAS, version);
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9');
    }

}
