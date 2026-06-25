package com.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
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
    void exposesEmptyShardWhenShardCountExceedsInventory() {
        TestSuitePlan suite = suitePlan(entry("com.example.AaaTest"));
        TestShardPlan empty = new TestShardPlanner().plan(projectDir, config(), suite, new TestShardSpec(3, 3));

        assertTrue(empty.empty());
        assertEquals(1, empty.inventoryEntries().size());
        assertEquals(List.of(), empty.entries());
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
        return new TestSuitePlan(
                "fast",
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
