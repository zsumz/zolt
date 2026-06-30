package com.zolt.test.shard;

import com.zolt.test.TestInventoryEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record TestShardPlan(
        String suiteName,
        TestShardSpec shard,
        Path manifestPath,
        String inventoryFingerprint,
        List<TestInventoryEntry> inventoryEntries,
        List<TestInventoryEntry> entries,
        long estimatedCostMillis,
        Optional<TestShardBalancing> balancing) {
    public TestShardPlan {
        suiteName = suiteName == null || suiteName.isBlank() ? "all" : suiteName;
        manifestPath = manifestPath.toAbsolutePath().normalize();
        inventoryEntries = List.copyOf(inventoryEntries);
        entries = List.copyOf(entries);
        estimatedCostMillis = Math.max(0L, estimatedCostMillis);
        balancing = balancing == null ? Optional.empty() : balancing;
    }

    public TestShardPlan(
            String suiteName,
            TestShardSpec shard,
            Path manifestPath,
            String inventoryFingerprint,
            List<TestInventoryEntry> inventoryEntries,
            List<TestInventoryEntry> entries) {
        this(suiteName, shard, manifestPath, inventoryFingerprint, inventoryEntries, entries, 0L, Optional.empty());
    }

    public boolean empty() {
        return entries.isEmpty();
    }

    public Path projectRelativeManifestPath(Path projectDirectory) {
        return projectDirectory.toAbsolutePath().normalize().relativize(manifestPath);
    }
}
