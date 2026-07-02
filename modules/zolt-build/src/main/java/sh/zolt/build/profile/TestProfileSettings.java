package sh.zolt.build.profile;

import sh.zolt.test.runtime.TestRunException;
import sh.zolt.project.ProjectPathException;
import sh.zolt.project.ProjectPaths;
import sh.zolt.test.shard.TestShardSpec;
import sh.zolt.test.TestSuitePathSegments;
import java.nio.file.Path;
import java.util.Optional;

public record TestProfileSettings(
        boolean enabled,
        Optional<Path> profileDirectory,
        int summaryLimit,
        long minimumDurationMillis,
        Optional<String> suiteName,
        Optional<String> shard,
        Optional<String> workspaceMember) {
    private static final Path DEFAULT_PROFILE_DIRECTORY = Path.of("target/test-profile");
    private static final int DEFAULT_SUMMARY_LIMIT = 10;

    public TestProfileSettings {
        profileDirectory = profileDirectory == null ? Optional.empty() : profileDirectory;
        suiteName = clean(suiteName);
        shard = clean(shard);
        workspaceMember = clean(workspaceMember);
        if (summaryLimit < 1) {
            throw new TestRunException("--profile-top requires a positive integer.");
        }
        if (minimumDurationMillis < 0L) {
            throw new TestRunException("--profile-min requires a non-negative duration.");
        }
    }

    public static TestProfileSettings disabled() {
        return new TestProfileSettings(false, Optional.empty(), DEFAULT_SUMMARY_LIMIT, 0L, Optional.empty(), Optional.empty(), Optional.empty());
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
                return new TestProfileSettings(
                        true,
                        Optional.empty(),
                        validatedSummaryLimit(summaryLimit),
                        parseDuration(minimumDuration),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
            }
            return disabled();
        }
        return new TestProfileSettings(
                enabled || profileDirectory != null || summaryLimit != null || minimumDuration != null,
                Optional.ofNullable(profileDirectory),
                validatedSummaryLimit(summaryLimit),
                parseDuration(minimumDuration),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public Optional<Path> absoluteProfileDirectory(Path projectDirectory) {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.of(safeProfileDirectory(projectDirectory, profileDirectory.orElse(DEFAULT_PROFILE_DIRECTORY)));
    }

    public TestProfileSettings forShard(String suiteName, TestShardSpec shard) {
        if (!enabled || shard == null) {
            return this;
        }
        Path root = profileDirectory.orElse(DEFAULT_PROFILE_DIRECTORY);
        return new TestProfileSettings(
                true,
                Optional.of(root
                        .resolve("shards")
                        .resolve(TestSuitePathSegments.suiteSegment(suiteName))
                        .resolve(TestSuitePathSegments.shardSegment(shard))),
                summaryLimit,
                minimumDurationMillis,
                Optional.ofNullable(suiteName == null || suiteName.isBlank() ? "all" : suiteName),
                Optional.of(shard.label()),
                workspaceMember);
    }

    public TestProfileSettings forWorkspaceMember(String memberPath) {
        if (!enabled) {
            return disabled();
        }
        Path root = profileDirectory.orElse(DEFAULT_PROFILE_DIRECTORY);
        return new TestProfileSettings(
                true,
                Optional.of(root.resolve(memberPath)),
                summaryLimit,
                minimumDurationMillis,
                suiteName,
                shard,
                Optional.ofNullable(memberPath));
    }

    public TestProfileSettings forSuite(String suiteName) {
        if (!enabled) {
            return this;
        }
        return new TestProfileSettings(
                true,
                profileDirectory,
                summaryLimit,
                minimumDurationMillis,
                Optional.ofNullable(suiteName == null || suiteName.isBlank() ? "all" : suiteName),
                shard,
                workspaceMember);
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

    private static Optional<String> clean(Optional<String> value) {
        if (value == null || value.isEmpty() || value.orElseThrow().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.orElseThrow());
    }
}
