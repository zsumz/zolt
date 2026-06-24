package com.zolt.cli.console;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ProgressPolicy(ProgressMode mode, boolean interactiveStderr, Map<String, String> environment) {
    private static final Set<String> CI_ENVIRONMENT_KEYS = Set.of(
            "CI",
            "WOODPECKER",
            "GITHUB_ACTIONS",
            "BUILDKITE");

    public ProgressPolicy {
        environment = Map.copyOf(environment);
    }

    public static ProgressPolicy of(
            ProgressMode mode,
            boolean noProgress,
            boolean interactiveStderr,
            Map<String, String> environment) {
        return new ProgressPolicy(noProgress ? ProgressMode.NEVER : mode, interactiveStderr, environment);
    }

    public boolean enabledForHumanOutput() {
        return enabled(ProgressOutputContract.HUMAN);
    }

    public boolean enabledForParseableOutput() {
        return enabled(ProgressOutputContract.PARSEABLE);
    }

    public boolean enabled(ProgressOutputContract outputContract) {
        if (outputContract == ProgressOutputContract.PARSEABLE) {
            return false;
        }
        return switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> interactiveStderr && !ciLikeEnvironment();
        };
    }

    public boolean ciLikeEnvironment() {
        return CI_ENVIRONMENT_KEYS.stream().anyMatch(this::enabledEnvironmentFlag);
    }

    private boolean enabledEnvironmentFlag(String key) {
        String value = environment.get(key);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.isEmpty()
                && !normalized.equals("0")
                && !normalized.equals("false")
                && !normalized.equals("no");
    }
}
