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
                .resolve(safeSegment(suiteName))
                .resolve("shard-" + shard.index() + "-of-" + shard.total() + ".json");
    }

    private static String safeSegment(String value) {
        String text = value == null || value.isBlank() ? "all" : value;
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.'
                    || character == '_'
                    || character == '-') {
                safe.append(character);
            } else {
                safe.append('_');
            }
        }
        return safe.toString();
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
