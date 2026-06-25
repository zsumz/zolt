package com.zolt.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestPlanJsonFormatterTest {
    @TempDir
    private Path projectDir;

    @Test
    void emitsCiShardMatrixWithWorkspaceMemberAndCommandArguments() {
        TestInventoryEntry alpha = entry("com.example.AlphaTest");
        TestInventoryEntry beta = entry("com.example.BetaTest");
        Map<String, List<String>> overlaps = new LinkedHashMap<>();
        overlaps.put("com.example.BetaTest", List.of("contract"));
        TestSuitePlan plan = new TestSuitePlan(
                "fast",
                true,
                projectDir.resolve("target/test-classes"),
                List.of(alpha, beta),
                List.of("*Test"),
                List.of("*ContractTest"),
                List.of("fast"),
                List.of("slow"),
                List.of("com.example.AlphaTest", "com.example.BetaTest#runs", "*ServiceTest"),
                List.of("smoke"),
                List.of("quarantine"),
                List.of("com.example.MissingTest"),
                overlaps,
                List.of("com.example.OtherTest"));
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.AlphaTest"),
                List.of(new TestSelection.MethodSelector("com.example.BetaTest", "runs")),
                List.of("*ServiceTest"),
                List.of("smoke"),
                List.of("quarantine"));
        List<TestShardPlan> shards = List.of(new TestShardPlan(
                "fast",
                new TestShardSpec(1, 2),
                projectDir.resolve("target/test-shards/fast/shard-1-of-2.json"),
                "sha256:abc123",
                List.of(alpha, beta),
                List.of(alpha)));

        String json = new TestPlanJsonFormatter().json(
                config(),
                projectDir,
                Optional.of("modules/api"),
                selection,
                plan,
                shards,
                Optional.of(Path.of("target/test-reports")));

        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(json.contains("\"project\": \"demo\""));
        assertTrue(json.contains("\"member\": \"modules/api\""));
        assertTrue(json.contains("\"suite\": {\n    \"name\": \"fast\""));
        assertTrue(json.contains("\"entryCount\": 2"));
        assertTrue(json.contains("\"missingExplicitSelectors\": [\"com.example.MissingTest\"]"));
        assertTrue(json.contains("""
                  "overlappingEntries": [
                    {
                      "className": "com.example.BetaTest",
                      "suites": ["contract"]
                    }
                  ],
                """));
        assertTrue(json.contains("\"unassignedEntries\": [\"com.example.OtherTest\"]"));
        assertTrue(json.contains("\"shards\": ["));
        assertTrue(json.contains("\"index\": 1"));
        assertTrue(json.contains("\"total\": 2"));
        assertTrue(json.contains("\"manifest\": \"target/test-shards/fast/shard-1-of-2.json\""));
        assertTrue(json.contains("""
                      "arguments": ["test", "--workspace", "--member", "modules/api", "--suite", "fast", "--test", "com.example.AlphaTest", "--test", "com.example.BetaTest#runs", "--tests", "*ServiceTest", "--include-tag", "smoke", "--exclude-tag", "quarantine", "--shard", "1/2", "--reports-dir", "target/test-reports"]
                """));
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

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }
}
