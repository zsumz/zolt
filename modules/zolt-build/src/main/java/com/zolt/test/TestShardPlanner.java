package com.zolt.test;

import com.zolt.project.ProjectConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TestShardPlanner {
    public TestShardPlan plan(Path projectDirectory, ProjectConfig config, TestSuitePlan suitePlan, TestShardSpec shard) {
        return roundRobinPlan(projectDirectory, config, suitePlan, shard, Optional.empty());
    }

    public List<TestShardPlan> plans(
            Path projectDirectory,
            ProjectConfig config,
            TestSuitePlan suitePlan,
            int shardCount,
            TestProfileHistory history) {
        TestProfileHistory profileHistory = history == null ? TestProfileHistory.none() : history;
        List<TestInventoryEntry> inventory = sortedInventory(suitePlan);
        if (!profileHistory.requested()) {
            return roundRobinPlans(projectDirectory, config, suitePlan, shardCount, Optional.empty());
        }
        List<String> inventoryClassNames = inventory.stream().map(TestInventoryEntry::className).toList();
        Set<String> inventoryClassNameSet = new LinkedHashSet<>(inventoryClassNames);
        List<String> missingHistory = inventoryClassNames.stream()
                .filter(className -> !profileHistory.classDurations().containsKey(className))
                .toList();
        List<String> unmatchedHistory = profileHistory.classDurations().keySet().stream()
                .filter(className -> !inventoryClassNameSet.contains(className))
                .sorted()
                .toList();
        List<String> diagnostics = new ArrayList<>(profileHistory.diagnostics());
        if (!missingHistory.isEmpty() && !profileHistory.classDurations().isEmpty()) {
            diagnostics.add("Profile history is missing " + missingHistory.size()
                    + " selected classes; those classes use deterministic round-robin fallback.");
        }
        if (!unmatchedHistory.isEmpty()) {
            diagnostics.add("Profile history contains " + unmatchedHistory.size()
                    + " classes outside the selected suite.");
        }
        boolean hasMatchedHistory = inventoryClassNames.stream().anyMatch(profileHistory.classDurations()::containsKey);
        TestShardBalancing balancing = new TestShardBalancing(
                hasMatchedHistory ? TestShardBalancing.PROFILE_HISTORY : TestShardBalancing.ROUND_ROBIN,
                profileHistory.source(),
                missingHistory,
                unmatchedHistory,
                diagnostics);
        if (!hasMatchedHistory) {
            return roundRobinPlans(projectDirectory, config, suitePlan, shardCount, Optional.of(balancing));
        }
        return balancedPlans(projectDirectory, config, suitePlan, shardCount, profileHistory.classDurations(), balancing);
    }

    private List<TestShardPlan> balancedPlans(
            Path projectDirectory,
            ProjectConfig config,
            TestSuitePlan suitePlan,
            int shardCount,
            Map<String, Long> durations,
            TestShardBalancing balancing) {
        List<TestInventoryEntry> inventory = sortedInventory(suitePlan);
        List<ShardBucket> buckets = new ArrayList<>();
        for (int index = 1; index <= shardCount; index++) {
            buckets.add(new ShardBucket(new TestShardSpec(index, shardCount)));
        }
        inventory.stream()
                .filter(entry -> durations.containsKey(entry.className()))
                .sorted(Comparator
                        .comparingLong((TestInventoryEntry entry) -> durations.get(entry.className()))
                        .reversed()
                        .thenComparing(TestInventoryEntry::className))
                .forEach(entry -> lightestBucket(buckets).add(entry, durations.get(entry.className())));
        List<TestInventoryEntry> missingHistory = inventory.stream()
                .filter(entry -> !durations.containsKey(entry.className()))
                .toList();
        for (int index = 0; index < missingHistory.size(); index++) {
            buckets.get(index % shardCount).add(missingHistory.get(index), 0L);
        }
        String fingerprint = fingerprint(inventory);
        return buckets.stream()
                .map(bucket -> plan(projectDirectory, config, suitePlan, fingerprint, inventory, bucket, Optional.of(balancing)))
                .toList();
    }

    private List<TestShardPlan> roundRobinPlans(
            Path projectDirectory,
            ProjectConfig config,
            TestSuitePlan suitePlan,
            int shardCount,
            Optional<TestShardBalancing> balancing) {
        List<TestShardPlan> plans = new ArrayList<>();
        for (int index = 1; index <= shardCount; index++) {
            plans.add(roundRobinPlan(projectDirectory, config, suitePlan, new TestShardSpec(index, shardCount), balancing));
        }
        return List.copyOf(plans);
    }

    private TestShardPlan roundRobinPlan(
            Path projectDirectory,
            ProjectConfig config,
            TestSuitePlan suitePlan,
            TestShardSpec shard,
            Optional<TestShardBalancing> balancing) {
        List<TestInventoryEntry> inventory = sortedInventory(suitePlan);
        ShardBucket bucket = new ShardBucket(shard);
        java.util.stream.IntStream.range(0, inventory.size())
                .filter(index -> index % shard.total() == shard.index() - 1)
                .mapToObj(inventory::get)
                .forEach(entry -> bucket.add(entry, 0L));
        return plan(projectDirectory, config, suitePlan, fingerprint(inventory), inventory, bucket, balancing);
    }

    private static TestShardPlan plan(
            Path projectDirectory,
            ProjectConfig config,
            TestSuitePlan suitePlan,
            String fingerprint,
            List<TestInventoryEntry> inventory,
            ShardBucket bucket,
            Optional<TestShardBalancing> balancing) {
        return new TestShardPlan(
                suitePlan.suiteName(),
                bucket.shard(),
                manifestPath(projectDirectory, config, suitePlan.suiteName(), bucket.shard()),
                fingerprint,
                inventory,
                bucket.entries(),
                bucket.estimatedCostMillis(),
                balancing);
    }

    private static List<TestInventoryEntry> sortedInventory(TestSuitePlan suitePlan) {
        return suitePlan.entries().stream()
                .sorted(Comparator.comparing(TestInventoryEntry::className))
                .toList();
    }

    private static ShardBucket lightestBucket(List<ShardBucket> buckets) {
        return buckets.stream()
                .min(Comparator
                        .comparingLong(ShardBucket::estimatedCostMillis)
                        .thenComparing(bucket -> bucket.shard().index()))
                .orElseThrow();
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

    private static final class ShardBucket {
        private final TestShardSpec shard;
        private final List<TestInventoryEntry> entries = new ArrayList<>();
        private long estimatedCostMillis;

        private ShardBucket(TestShardSpec shard) {
            this.shard = shard;
        }

        private void add(TestInventoryEntry entry, long costMillis) {
            entries.add(entry);
            entries.sort(Comparator.comparing(TestInventoryEntry::className));
            estimatedCostMillis += Math.max(0L, costMillis);
        }

        private TestShardSpec shard() {
            return shard;
        }

        private List<TestInventoryEntry> entries() {
            return List.copyOf(entries);
        }

        private long estimatedCostMillis() {
            return estimatedCostMillis;
        }
    }
}
