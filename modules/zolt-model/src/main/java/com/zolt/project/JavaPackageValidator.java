package com.zolt.project;

import java.util.Set;
import java.util.regex.Pattern;

public final class JavaPackageValidator {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Set<String> RESERVED_SEGMENTS = Set.of(
            "_",
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "exports",
            "extends",
            "false",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "module",
            "native",
            "new",
            "null",
            "open",
            "opens",
            "package",
            "private",
            "protected",
            "provides",
            "public",
            "record",
            "requires",
            "return",
            "sealed",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "to",
            "transient",
            "transitive",
            "true",
            "try",
            "uses",
            "var",
            "void",
            "volatile",
            "while",
            "with",
            "yield");

    private JavaPackageValidator() {
    }

    public static String requireValid(String subject, String packageName) {
        if (packageName == null || packageName.isEmpty() || !PACKAGE.matcher(packageName).matches()) {
            throw invalid(subject);
        }
        for (String segment : packageName.split("\\.")) {
            if (RESERVED_SEGMENTS.contains(segment)) {
                throw new IllegalArgumentException(
                        "Invalid "
                                + subject
                                + " Java package segment `"
                                + segment
                                + "`. Use segment(.segment)* with Java identifier segments that are not Java keywords.");
            }
        }
        return packageName;
    }

    private static IllegalArgumentException invalid(String subject) {
        return new IllegalArgumentException(
                "Invalid "
                        + subject
                        + " Java package. Use segment(.segment)* with segments matching [A-Za-z_$][A-Za-z0-9_$]*.");
    }
}
