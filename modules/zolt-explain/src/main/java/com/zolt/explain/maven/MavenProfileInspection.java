package com.zolt.explain.maven;

import java.util.List;

public record MavenProfileInspection(String id, List<String> activationHints, List<String> modules) {
    public MavenProfileInspection(String id, List<String> activationHints) {
        this(id, activationHints, List.of());
    }

    public MavenProfileInspection {
        activationHints = List.copyOf(activationHints);
        modules = List.copyOf(modules);
    }
}
