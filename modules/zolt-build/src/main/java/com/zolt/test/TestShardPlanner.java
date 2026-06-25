package com.zolt.test;

import com.zolt.project.ProjectConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class TestShardPlanner {
    public TestShardPlan plan(Path projectDirectory, ProjectConfig config, TestSuitePlan suitePlan, TestShardSpec shard) {
        List<TestInventoryEntry> inventory = suitePlan.entries().stream()
                .sorted(java.util.Comparator.comparing(TestInventoryEntry::className))
                .toList();
        List<TestInventoryEntry> selected = java.util.stream.IntStream.range(0, inventory.size())
                .filter(index -> index % shard.total() == shard.index() - 1)
                .mapToObj(inventory::get)
                .toList();
        return new TestShardPlan(
                suitePlan.suiteName(),
                shard,
                manifestPath(projectDirectory, config, suitePlan.suiteName(), shard),
                fingerprint(inventory),
                inventory,
                selected);
    }

    private static Path manifestPath(
            Path projectDirectory,
            ProjectConfig config,
            String suiteName,
            TestShardSpec shard) {
        return projectDirectory.resolve(config.build().outputRoot())
                .resolve("test-shards")
                .resolve(TestSuitePathSegments.suiteSegment(suiteName))
                .resolve("shard-" + shard.index() + "-of-" + shard.total() + ".json");
    }

    private static String fingerprint(List<TestInventoryEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TestInventoryEntry entry : entries) {
                digest.update(entry.className().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
