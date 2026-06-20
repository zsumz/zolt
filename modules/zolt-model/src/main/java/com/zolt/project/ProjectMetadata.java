package com.zolt.project;

import java.util.Optional;

public record ProjectMetadata(
        String name,
        String version,
        String group,
        String java,
        Optional<String> main) {
    public ProjectMetadata {
        main = main == null ? Optional.empty() : main;
    }
}
