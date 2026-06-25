package com.zolt.test;

import java.nio.file.Path;
import java.util.List;

public record TestShardPlan(
        String suiteName,
        TestShardSpec shard,
        Path manifestPath,
        String inventoryFingerprint,
        List<TestInventoryEntry> inventoryEntries,
        List<TestInventoryEntry> entries) {
    public TestShardPlan {
        suiteName = suiteName == null || suiteName.isBlank() ? "all" : suiteName;
        manifestPath = manifestPath.toAbsolutePath().normalize();
        inventoryEntries = List.copyOf(inventoryEntries);
        entries = List.copyOf(entries);
    }

    public boolean empty() {
        return entries.isEmpty();
    }

    public Path projectRelativeManifestPath(Path projectDirectory) {
        return projectDirectory.toAbsolutePath().normalize().relativize(manifestPath);
    }
}
