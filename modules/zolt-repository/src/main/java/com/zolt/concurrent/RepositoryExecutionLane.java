package com.zolt.concurrent;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public enum RepositoryExecutionLane {
    PLATFORM("platform"),
    VIRTUAL("virtual");

    public static final String ENVIRONMENT_KEY = "ZOLT_REPOSITORY_EXECUTION_LANE";
    public static final RepositoryExecutionLane DEFAULT = PLATFORM;

    private final String id;

    RepositoryExecutionLane(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public ExecutorService openExecutor(int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("Repository execution concurrency must be at least 1.");
        }
        return switch (this) {
            case PLATFORM -> Executors.newFixedThreadPool(concurrency);
            case VIRTUAL -> Executors.newVirtualThreadPerTaskExecutor();
        };
    }

    public static RepositoryExecutionLane fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        return fromIdOrDefault(environment.get(ENVIRONMENT_KEY));
    }

    public static RepositoryExecutionLane fromIdOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RepositoryExecutionLane lane : values()) {
            if (lane.id.equals(normalized)) {
                return lane;
            }
        }
        return DEFAULT;
    }
}
