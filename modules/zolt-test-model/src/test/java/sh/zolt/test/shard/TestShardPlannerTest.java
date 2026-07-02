package sh.zolt.test.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.test.TestInventoryEntry;
import sh.zolt.test.TestProfileHistory;
import sh.zolt.test.TestSuitePlan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestShardPlannerTest {
    @TempDir
    private Path projectDir;

    @Test
    void assignsSortedEntriesByDeterministicRoundRobin() {
        TestSuitePlan suite = suitePlan(
                entry("com.example.CccTest"),
                entry("com.example.AaaTest"),
                entry("com.example.BbbTest"),
                entry("com.example.DddTest"));
        TestShardPlanner planner = new TestShardPlanner();

        TestShardPlan first = planner.plan(projectDir, config(), suite, new TestShardSpec(1, 2));
        TestShardPlan second = planner.plan(projectDir, config(), suite, new TestShardSpec(2, 2));

        assertEquals(List.of("com.example.AaaTest", "com.example.CccTest"), classNames(first.entries()));
        assertEquals(List.of("com.example.BbbTest", "com.example.DddTest"), classNames(second.entries()));
        assertEquals(new HashSet<>(classNames(suite.entries())), new HashSet<>(
                java.util.stream.Stream.concat(first.entries().stream(), second.entries().stream())
                        .map(TestInventoryEntry::className)
                        .toList()));
        assertEquals("target/test-shards/fast/shard-1-of-2.json", first.projectRelativeManifestPath(projectDir).toString());
        assertEquals(first.inventoryFingerprint(), second.inventoryFingerprint());
    }

    @Test
    void balancesKnownDurationsAndFallsBackForMissingHistory() {
        TestSuitePlan suite = suitePlan(
                entry("com.example.DddTest"),
                entry("com.example.AaaTest"),
                entry("com.example.BbbTest"),
                entry("com.example.CccTest"));
        TestProfileHistory history = new TestProfileHistory(
                Optional.of(projectDir.resolve("profile.json")),
                Map.of(
                        "com.example.AaaTest", 100L,
                        "com.example.BbbTest", 60L,
                        "com.example.CccTest", 50L,
                        "com.example.UnusedTest", 900L),
                List.of());

        List<TestShardPlan> shards = new TestShardPlanner().plans(projectDir, config(), suite, 2, history);

        assertEquals(List.of("com.example.AaaTest", "com.example.DddTest"), classNames(shards.get(0).entries()));
        assertEquals(List.of("com.example.BbbTest", "com.example.CccTest"), classNames(shards.get(1).entries()));
        assertEquals(100L, shards.get(0).estimatedCostMillis());
        assertEquals(110L, shards.get(1).estimatedCostMillis());
        TestShardBalancing balancing = shards.getFirst().balancing().orElseThrow();
        assertEquals(TestShardBalancing.PROFILE_HISTORY, balancing.mode());
        assertEquals(List.of("com.example.DddTest"), balancing.missingHistoryEntries());
        assertEquals(List.of("com.example.UnusedTest"), balancing.unmatchedHistoryEntries());
        assertTrue(balancing.diagnostics().stream().anyMatch(value -> value.contains("missing 1 selected classes")));
    }

    @Test
    void fallsBackToRoundRobinWhenProfileHasNoMatchingClasses() {
        TestSuitePlan suite = suitePlan(
                entry("com.example.AaaTest"),
                entry("com.example.BbbTest"),
                entry("com.example.CccTest"));
        TestProfileHistory history = new TestProfileHistory(
                Optional.of(projectDir.resolve("profile.json")),
                Map.of("com.example.OtherTest", 100L),
                List.of());

        List<TestShardPlan> shards = new TestShardPlanner().plans(projectDir, config(), suite, 2, history);

        assertEquals(List.of("com.example.AaaTest", "com.example.CccTest"), classNames(shards.get(0).entries()));
        assertEquals(List.of("com.example.BbbTest"), classNames(shards.get(1).entries()));
        assertEquals(TestShardBalancing.ROUND_ROBIN, shards.getFirst().balancing().orElseThrow().mode());
    }

    @Test
    void exposesEmptyShardWhenShardCountExceedsInventory() {
        TestSuitePlan suite = suitePlan(entry("com.example.AaaTest"));
        TestShardPlan empty = new TestShardPlanner().plan(projectDir, config(), suite, new TestShardSpec(3, 3));

        assertTrue(empty.empty());
        assertEquals(1, empty.inventoryEntries().size());
        assertEquals(List.of(), empty.entries());
    }

    @Test
    void sanitizesSuiteNameInManifestPath() {
        TestSuitePlan suite = suitePlan("fast suite!", entry("com.example.AaaTest"));
        TestShardPlan shard = new TestShardPlanner().plan(projectDir, config(), suite, new TestShardSpec(1, 2));

        assertEquals(
                "target/test-shards/fast_suite_/shard-1-of-2.json",
                shard.projectRelativeManifestPath(projectDir).toString());
    }

    @Test
    void writesDeterministicManifest() throws IOException {
        TestSuitePlan suite = suitePlan(entry("com.example.AaaTest"), entry("com.example.BbbTest"));
        TestShardPlan shard = new TestShardPlanner().plan(projectDir, config(), suite, new TestShardSpec(1, 2));

        new TestShardManifestWriter().write(shard);

        Path manifest = projectDir.resolve("target/test-shards/fast/shard-1-of-2.json");
        assertTrue(Files.exists(manifest));
        String json = Files.readString(manifest);
        assertTrue(json.contains("\"suite\": \"fast\""));
        assertTrue(json.contains("\"index\": 1"));
        assertTrue(json.contains("\"total\": 2"));
        assertTrue(json.contains("\"inventoryFingerprint\": \"sha256:"));
        assertTrue(json.contains("\"inventoryEntries\": 2"));
        assertTrue(json.contains("\"selectedEntries\": 1"));
        assertTrue(json.contains("\"empty\": false"));
        assertTrue(json.contains("\"com.example.AaaTest\""));
        assertFalse(json.contains("\"com.example.BbbTest\""));
    }

    private static TestSuitePlan suitePlan(TestInventoryEntry... entries) {
        return suitePlan("fast", entries);
    }

    private static TestSuitePlan suitePlan(String suiteName, TestInventoryEntry... entries) {
        return new TestSuitePlan(
                suiteName,
                true,
                Path.of("target/test-classes"),
                List.of(entries),
                List.of("*Test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of());
    }

    private static TestInventoryEntry entry(String className) {
        return new TestInventoryEntry(
                className,
                Path.of("target/test-classes"),
                Path.of("target/test-classes").resolve(className.replace('.', '/') + ".class"),
                List.of(),
                "",
                List.of());
    }

    private static List<String> classNames(List<TestInventoryEntry> entries) {
        return entries.stream().map(TestInventoryEntry::className).toList();
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }
}
