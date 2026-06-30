package com.zolt.explain.maven;

import java.util.List;

public record MavenProfileInspection(String id, List<String> activationHints) {
    public MavenProfileInspection {
        activationHints = List.copyOf(activationHints);
    }
}
