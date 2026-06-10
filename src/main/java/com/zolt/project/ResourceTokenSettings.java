package com.zolt.project;

import java.util.Optional;

public record ResourceTokenSettings(
        Optional<String> value,
        Optional<String> env,
        Optional<String> project) {
    public ResourceTokenSettings {
        value = normalize(value);
        env = normalize(env);
        project = normalize(project);
        int sources = (value.isPresent() ? 1 : 0) + (env.isPresent() ? 1 : 0) + (project.isPresent() ? 1 : 0);
        if (sources != 1) {
            throw new IllegalArgumentException("Resource token must declare exactly one of value, env, or project.");
        }
    }

    public static ResourceTokenSettings literal(String value) {
        return new ResourceTokenSettings(Optional.of(value), Optional.empty(), Optional.empty());
    }

    public static ResourceTokenSettings env(String env) {
        return new ResourceTokenSettings(Optional.empty(), Optional.of(env), Optional.empty());
    }

    public static ResourceTokenSettings project(String project) {
        return new ResourceTokenSettings(Optional.empty(), Optional.empty(), Optional.of(project));
    }

    private static Optional<String> normalize(Optional<String> value) {
        if (value == null || value.isEmpty() || value.orElseThrow().isBlank()) {
            return Optional.empty();
        }
        return value;
    }
}
