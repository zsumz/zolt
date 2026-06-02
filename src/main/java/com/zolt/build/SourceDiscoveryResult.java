package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

public record SourceDiscoveryResult(
        List<Path> mainSources,
        List<Path> testSources) {
    public SourceDiscoveryResult {
        mainSources = List.copyOf(mainSources);
        testSources = List.copyOf(testSources);
    }

    public boolean empty() {
        return mainSources.isEmpty() && testSources.isEmpty();
    }
}
