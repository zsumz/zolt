package com.zolt.maven;

import java.util.List;
import java.util.Optional;

public record RawPomDependency(
        String groupId,
        String artifactId,
        Optional<String> version,
        Optional<String> scope,
        Optional<String> type,
        Optional<String> classifier,
        boolean optional,
        List<RawPomExclusion> exclusions) {
    public RawPomDependency {
        version = version == null ? Optional.empty() : version;
        scope = scope == null ? Optional.empty() : scope;
        type = type == null ? Optional.empty() : type;
        classifier = classifier == null ? Optional.empty() : classifier;
        exclusions = List.copyOf(exclusions);
    }
}
