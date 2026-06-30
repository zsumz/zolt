package com.zolt.test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TestSuitePlan(
        String suiteName,
        boolean configuredSuite,
        Path outputDirectory,
        List<TestInventoryEntry> entries,
        List<String> includeClassname,
        List<String> excludeClassname,
        List<String> includeTag,
        List<String> excludeTag,
        List<String> selectionClassname,
        List<String> selectionIncludeTag,
        List<String> selectionExcludeTag,
        List<String> missingExplicitClassSelectors,
        Map<String, List<String>> overlappingEntries,
        List<String> unassignedEntries) {
    public TestSuitePlan {
        if (suiteName == null || suiteName.isBlank()) {
            suiteName = "all";
        }
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        entries = List.copyOf(entries);
        includeClassname = List.copyOf(includeClassname);
        excludeClassname = List.copyOf(excludeClassname);
        includeTag = List.copyOf(includeTag);
        excludeTag = List.copyOf(excludeTag);
        selectionClassname = List.copyOf(selectionClassname);
        selectionIncludeTag = List.copyOf(selectionIncludeTag);
        selectionExcludeTag = List.copyOf(selectionExcludeTag);
        missingExplicitClassSelectors = List.copyOf(missingExplicitClassSelectors);
        overlappingEntries = Collections.unmodifiableMap(new LinkedHashMap<>(overlappingEntries));
        unassignedEntries = List.copyOf(unassignedEntries);
    }

    public boolean empty() {
        return entries.isEmpty();
    }
}
