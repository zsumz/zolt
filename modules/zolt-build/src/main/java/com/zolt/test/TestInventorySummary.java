package com.zolt.test;

import java.nio.file.Path;
import java.util.List;

public record TestInventorySummary(
        int totalEntries,
        List<Path> outputRoots,
        List<String> classSelectors,
        List<String> methodSelectors,
        List<String> classNamePatterns,
        List<String> includedTags,
        List<String> excludedTags,
        List<String> missingExplicitClassSelectors) {
    public TestInventorySummary {
        totalEntries = Math.max(0, totalEntries);
        outputRoots = outputRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        classSelectors = List.copyOf(classSelectors);
        methodSelectors = List.copyOf(methodSelectors);
        classNamePatterns = List.copyOf(classNamePatterns);
        includedTags = List.copyOf(includedTags);
        excludedTags = List.copyOf(excludedTags);
        missingExplicitClassSelectors = List.copyOf(missingExplicitClassSelectors);
    }

    public static TestInventorySummary empty() {
        return new TestInventorySummary(0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
