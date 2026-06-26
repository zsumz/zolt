package com.zolt.build;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;
import java.util.Optional;

public record TestProfileSettings(
        boolean enabled,
        Optional<Path> profileDirectory,
        int summaryLimit,
        long minimumDurationMillis) {
    private static final Path DEFAULT_PROFILE_DIRECTORY = Path.of("target/test-profile");
    private static final int DEFAULT_SUMMARY_LIMIT = 10;

    public TestProfileSettings {
        profileDirectory = profileDirectory == null ? Optional.empty() : profileDirectory;
        if (summaryLimit < 1) {
            throw new TestRunException("--profile-top requires a positive integer.");
        }
        if (minimumDurationMillis < 0L) {
            throw new TestRunException("--profile-min requires a non-negative duration.");
        }
    }

    public static TestProfileSettings disabled() {
        return new TestProfileSettings(false, Optional.empty(), DEFAULT_SUMMARY_LIMIT, 0L);
    }

    public static TestProfileSettings fromCli(boolean enabled, Path profileDirectory) {
        return fromCli(enabled, profileDirectory, null, null);
    }

    public static TestProfileSettings fromCli(
            boolean enabled,
            Path profileDirectory,
            Integer summaryLimit,
            String minimumDuration) {
        if (!enabled && profileDirectory == null) {
            if (summaryLimit != null || minimumDuration != null) {
                return new TestProfileSettings(true, Optional.empty(), validatedSummaryLimit(summaryLimit), parseDuration(minimumDuration));
            }
            return disabled();
        }
        return new TestProfileSettings(
                enabled || profileDirectory != null || summaryLimit != null || minimumDuration != null,
                Optional.ofNullable(profileDirectory),
                validatedSummaryLimit(summaryLimit),
                parseDuration(minimumDuration));
    }

    public Optional<Path> absoluteProfileDirectory(Path projectDirectory) {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.of(safeProfileDirectory(projectDirectory, profileDirectory.orElse(DEFAULT_PROFILE_DIRECTORY)));
    }

    private static Path safeProfileDirectory(Path projectDirectory, Path profileDirectory) {
        try {
            return ProjectPaths.output(
                    ProjectPaths.root(projectDirectory),
                    "--profile-dir",
                    profileDirectory.toString());
        } catch (ProjectPathException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    private static int validatedSummaryLimit(Integer summaryLimit) {
        if (summaryLimit == null) {
            return DEFAULT_SUMMARY_LIMIT;
        }
        if (summaryLimit < 1) {
            throw new TestRunException("Invalid --profile-top `" + summaryLimit + "`. Use a positive integer.");
        }
        return summaryLimit;
    }

    private static long parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        long multiplier;
        String number;
        if (text.endsWith("ms")) {
            multiplier = 1L;
            number = text.substring(0, text.length() - 2);
        } else if (text.endsWith("s")) {
            multiplier = 1_000L;
            number = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 60_000L;
            number = text.substring(0, text.length() - 1);
        } else {
            throw invalidDuration(value);
        }
        try {
            long amount = Long.parseLong(number);
            if (amount < 0L) {
                throw invalidDuration(value);
            }
            return Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalidDuration(value);
        }
    }

    private static TestRunException invalidDuration(String value) {
        return new TestRunException(
                "Invalid --profile-min `" + value + "`. Use a duration such as 250ms, 3s, or 1m.");
    }
}
