package sh.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.test.shard.TestShardException;
import sh.zolt.test.shard.TestShardSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestSuitePlannerTest {
    private final TestSuitePlanner planner = new TestSuitePlanner();

    @TempDir
    private Path projectDir;

    @Test
    void plansConfiguredSuiteWithSelectionDiagnosticsAndWorkerPool() throws IOException {
        writeClass("com/example/FastServiceTest.class");
        writeClass("com/example/SharedContractTest.class");
        writeClass("com/example/LooseTest.class");
        writeClass("com/example/SlowServiceTest.class");
        ProjectConfig config = configWithSuites();
        TestSelection selection = TestSelection.fromCli(
                List.of(
                        "com.example.FastServiceTest",
                        "com.example.SharedContractTest#runs",
                        "com.example.MissingTest"),
                List.of("*ServiceTest", "*ContractTest"),
                List.of("smoke"),
                List.of("flaky"));

        TestSuitePlan plan = planner.plan(projectDir, config, "fast", selection);

        assertEquals("fast", plan.suiteName());
        assertTrue(plan.configuredSuite());
        assertEquals(projectDir.resolve("target/test-classes").toAbsolutePath().normalize(), plan.outputDirectory());
        assertEquals(
                List.of("com.example.FastServiceTest", "com.example.SharedContractTest"),
                classNames(plan.entries()));
        assertEquals(List.of("*ServiceTest", "*SharedContractTest"), plan.includeClassname());
        assertEquals(List.of("*Slow*"), plan.excludeClassname());
        assertEquals(List.of("fast"), plan.includeTag());
        assertEquals(List.of("slow"), plan.excludeTag());
        assertEquals(
                List.of(
                        "com.example.FastServiceTest",
                        "com.example.MissingTest",
                        "com.example.SharedContractTest#runs",
                        "*ServiceTest",
                        "*ContractTest"),
                plan.selectionClassname());
        assertEquals(List.of("smoke"), plan.selectionIncludeTag());
        assertEquals(List.of("flaky"), plan.selectionExcludeTag());
        assertEquals(List.of("com.example.MissingTest"), plan.missingExplicitClassSelectors());
        assertEquals(
                Map.of("com.example.SharedContractTest", List.of("contract")),
                plan.overlappingEntries());
        assertEquals(
                List.of("com.example.LooseTest", "com.example.SlowServiceTest"),
                plan.unassignedEntries());

        TestSuiteExecutionPlan executionPlan = planner.executionPlan(projectDir, config, "fast", selection, null);

        assertEquals(List.of("com.example.FastServiceTest"), executionPlan.selection().classSelectors());
        assertEquals(
                List.of(new TestSelection.MethodSelector("com.example.SharedContractTest", "runs")),
                executionPlan.selection().methodSelectors());
        assertEquals(List.of(), executionPlan.selection().classNamePatterns());
        assertEquals(List.of("fast", "smoke"), executionPlan.selection().includedTags());
        assertEquals(List.of("slow", "flaky"), executionPlan.selection().excludedTags());
        assertTrue(executionPlan.workerPoolPlan().enabled());
        assertEquals(2, executionPlan.workerPoolPlan().maxWorkers());
        assertEquals(
                List.of(
                        List.of("com.example.FastServiceTest"),
                        List.of("com.example.SharedContractTest")),
                executionPlan.workerPoolPlan().waves().stream()
                        .map(wave -> classNames(wave.entries()))
                        .toList());
    }

    @Test
    void allSuiteWithoutShardReturnsTheOriginalSelectionAndEmptyWorkerPlan() {
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.FastServiceTest"),
                List.of(),
                List.of("smoke"),
                List.of());

        TestSuiteExecutionPlan plan = planner.executionPlan(projectDir, configWithSuites(), "all", selection, null);

        assertEquals(selection, plan.selection());
        assertTrue(plan.workerPoolPlan().empty());
    }

    @Test
    void unknownSuiteErrorTellsTheUserHowToConfigureIt() {
        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> planner.plan(projectDir, configWithSuites(), "missing", TestSelection.empty()));

        assertEquals(
                "Unknown test suite `missing`. Add [test.suites.missing] to zolt.toml or use a configured suite.",
                exception.getMessage());
    }

    @Test
    void emptyConfiguredSuiteErrorPointsAtPlanInspection() {
        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> planner.executionPlan(projectDir, configWithSuites(), "fast", TestSelection.empty(), null));

        assertEquals(
                "Test suite `fast` did not match any compiled test classes. "
                        + "Run `zolt test plan --suite fast` to inspect suite membership.",
                exception.getMessage());
    }

    @Test
    void rejectsInvalidShardCountsBeforeScanningTests() {
        TestShardException exception = assertThrows(
                TestShardException.class,
                () -> planner.shardPlans(projectDir, configWithSuites(), "fast", TestSelection.empty(), 0));

        assertEquals("Invalid --shard-count `0`. Use a positive integer.", exception.getMessage());
    }

    @Test
    void emptyShardExecutionWritesManifestAndReportsRelativePath() throws IOException {
        writeClass("com/example/FastServiceTest.class");

        TestPlanException exception = assertThrows(
                TestPlanException.class,
                () -> planner.executionPlan(
                        projectDir,
                        configWithSuites(),
                        "fast",
                        TestSelection.empty(),
                        new TestShardSpec(2, 2)));

        assertEquals(
                "Test shard `2/2` for suite `fast` did not match any compiled test classes. "
                        + "Wrote shard manifest to target/test-shards/fast/shard-2-of-2.json.",
                exception.getMessage());
        Path manifest = projectDir.resolve("target/test-shards/fast/shard-2-of-2.json");
        assertTrue(Files.exists(manifest));
        String json = Files.readString(manifest);
        assertTrue(json.contains("\"selectedEntries\": 0"));
        assertTrue(json.contains("\"empty\": true"));
    }

    private ProjectConfig configWithSuites() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "sh.zolt.tests", "21", Optional.empty()),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults().withTestSuites(Map.of(
                        "contract",
                        new TestSuiteSettings(
                                List.of("*ContractTest"),
                                List.of(),
                                List.of("contract"),
                                List.of()),
                        "fast",
                        new TestSuiteSettings(
                                List.of("*ServiceTest", "*SharedContractTest"),
                                List.of("*Slow*"),
                                List.of("fast"),
                                List.of("slow"),
                                true,
                                2,
                                Map.of(
                                        "com.example.FastServiceTest",
                                        List.of("database"),
                                        "com.example.SharedContractTest",
                                        List.of("database"))))));
    }

    private void writeClass(String relativePath) throws IOException {
        Path file = projectDir.resolve("target/test-classes").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {0});
    }

    private static List<String> classNames(List<TestInventoryEntry> entries) {
        return entries.stream().map(TestInventoryEntry::className).toList();
    }
}
