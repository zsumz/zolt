package com.zolt.build.discovery;

import java.nio.file.Path;
import java.util.List;

public record SourceDiscoveryResult(
        List<Path> mainSources,
        List<Path> testSources,
        List<Path> groovyTestSources) {
    public SourceDiscoveryResult(List<Path> mainSources, List<Path> testSources) {
        this(mainSources, testSources, List.of());
    }

    public SourceDiscoveryResult {
        mainSources = List.copyOf(mainSources);
        testSources = List.copyOf(testSources);
        groovyTestSources = List.copyOf(groovyTestSources);
    }

    public boolean empty() {
        return mainSources.isEmpty() && testSources.isEmpty() && groovyTestSources.isEmpty();
    }

    public List<Path> allTestSources() {
        return java.util.stream.Stream.concat(testSources.stream(), groovyTestSources.stream())
                .sorted()
                .toList();
    }
}
