package com.zolt.project;

import java.util.Locale;
import java.util.Optional;

public final class VersionPolicy {
    private VersionPolicy() {
    }

    public enum Context {
        PROJECT_VERSION("project version", true, false),
        VERSION_ALIAS("version alias", false, false),
        EXTERNAL_DEPENDENCY("external dependency version", false, false),
        PLATFORM("platform version", false, false),
        CONSTRAINT("dependency constraint version", false, false),
        TOOL_DEPENDENCY("tool dependency version", false, false),
        PUBLISH_RELEASE("publish release version", false, false),
        PUBLISH_SNAPSHOT("publish snapshot version", true, true);

        private final String description;
        private final boolean allowSnapshot;
        private final boolean requireSnapshot;

        Context(String description, boolean allowSnapshot, boolean requireSnapshot) {
            this.description = description;
            this.allowSnapshot = allowSnapshot;
            this.requireSnapshot = requireSnapshot;
        }

        public String description() {
            return description;
        }
    }

    public record Violation(String rule, String guidance) {
    }

    public static Optional<Violation> violation(Context context, String version) {
        if (version == null || version.isBlank() || !version.equals(version.trim())) {
            return Optional.of(new Violation(
                    "non-empty-literal",
                    "Use a non-empty literal version string without leading or trailing whitespace."));
        }
        if (containsInterpolation(version)) {
            return Optional.of(new Violation(
                    "no-interpolation",
                    "Interpolation is not supported; use a fixed literal version."));
        }
        if (isVersionRange(version)) {
            return Optional.of(new Violation(
                    "version-range",
                    "Version ranges are not supported; use a fixed released version."));
        }
        if (isDynamicVersion(version)) {
            return Optional.of(new Violation(
                    "dynamic-version",
                    "Dynamic versions are not supported; use a fixed released version."));
        }
        if (isIncomplete(version)) {
            return Optional.of(new Violation(
                    "incomplete-version",
                    "Incomplete versions are not supported; use a complete fixed version."));
        }
        if (!context.allowSnapshot && isSnapshot(version)) {
            return Optional.of(new Violation(
                    "snapshot-version",
                    "SNAPSHOT versions are not supported in this context; use a fixed released version."));
        }
        if (context.requireSnapshot && !isSnapshot(version)) {
            return Optional.of(new Violation(
                    "snapshot-required",
                    "Use a SNAPSHOT version for snapshot publishing."));
        }
        return Optional.empty();
    }

    public static boolean isSupported(Context context, String version) {
        return violation(context, version).isEmpty();
    }

    public static boolean isSnapshot(String version) {
        return version != null && version.endsWith("-SNAPSHOT");
    }

    public static String classifyPublishVersion(String version) {
        return isSnapshot(version) ? "snapshot" : "release";
    }

    private static boolean containsInterpolation(String version) {
        return version.contains("${")
                || (version.length() > 2 && version.startsWith("@") && version.endsWith("@"));
    }

    private static boolean isVersionRange(String version) {
        return version.length() >= 2
                && (version.charAt(0) == '[' || version.charAt(0) == '(')
                && (version.endsWith("]") || version.endsWith(")"));
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

    private static boolean isIncomplete(String version) {
        return version.endsWith(".")
                || version.endsWith("-")
                || version.contains("..");
    }
}
