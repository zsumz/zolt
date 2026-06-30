package com.zolt.quality;

import java.nio.file.Path;

public final class QualityCheckText {
    private QualityCheckText() {
    }

    public static String displayPath(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    public static String plural(long count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

    public static String verb(long count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
