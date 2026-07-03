package sh.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.shard.TestWorkerPoolPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TestInventoryModelTest {
    @Test
    void inventoryEntryNormalizesPathsDefaultsEngineAndCopiesLists() {
        List<String> patterns = new ArrayList<>(List.of(".*Test"));
        List<String> tags = new ArrayList<>(List.of("fast"));

        TestInventoryEntry entry = new TestInventoryEntry(
                "com.example.MainTest",
                Path.of("target/./test-classes"),
                Path.of("target/test-classes/com/example/../example/MainTest.class"),
                patterns,
                null,
                tags);

        patterns.add(".*Spec");
        tags.add("slow");

        assertEquals(Path.of("target/test-classes").toAbsolutePath().normalize(), entry.outputRoot());
        assertEquals(
                Path.of("target/test-classes/com/example/MainTest.class").toAbsolutePath().normalize(),
                entry.classFile());
        assertEquals(List.of(".*Test"), entry.matchedClassNamePatterns());
        assertEquals("", entry.engineId());
        assertEquals(List.of("fast"), entry.tags());
        assertThrows(UnsupportedOperationException.class, () -> entry.tags().add("late"));
    }

    @Test
    void inventoryEntryRejectsMissingRequiredFieldsWithClearMessages() {
        IllegalArgumentException missingClass = assertThrows(
                IllegalArgumentException.class,
                () -> new TestInventoryEntry(" ", Path.of("target/test-classes"), Path.of("MainTest.class"), List.of(), "", List.of()));
        IllegalArgumentException missingRoot = assertThrows(
                IllegalArgumentException.class,
                () -> new TestInventoryEntry("com.example.MainTest", null, Path.of("MainTest.class"), List.of(), "", List.of()));
        IllegalArgumentException missingClassFile = assertThrows(
                IllegalArgumentException.class,
                () -> new TestInventoryEntry("com.example.MainTest", Path.of("target/test-classes"), null, List.of(), "", List.of()));

        assertEquals("Test inventory entry requires a class name.", missingClass.getMessage());
        assertEquals("Test inventory entry requires an output root.", missingRoot.getMessage());
        assertEquals("Test inventory entry requires a class file.", missingClassFile.getMessage());
    }

    @Test
    void inventoryDefaultsSummaryAndCopiesEntries() {
        List<TestInventoryEntry> entries = new ArrayList<>(List.of(entry("com.example.MainTest")));

        TestInventory inventory = new TestInventory(entries, null);
        entries.clear();

        assertEquals(1, inventory.entries().size());
        assertEquals(TestInventorySummary.empty(), inventory.summary());
        assertThrows(UnsupportedOperationException.class, () -> inventory.entries().clear());
        assertEquals(List.of(), TestInventory.empty(null).entries());
        assertEquals(TestInventorySummary.empty(), TestInventory.empty(null).summary());
    }

    @Test
    void suiteExecutionPlanDefaultsSelectionAndWorkerPool() {
        TestSuiteExecutionPlan plan = new TestSuiteExecutionPlan(null, null);

        assertTrue(plan.selection().emptySelection());
        assertFalse(plan.workerPoolPlan().enabled());
        assertEquals(1, plan.workerPoolPlan().maxWorkers());
        assertTrue(plan.workerPoolPlan().empty());
    }

    @Test
    void suitePlanNormalizesDefaultsAndCopiesCollections() {
        List<TestInventoryEntry> entries = new ArrayList<>(List.of(entry("com.example.MainTest")));

        TestSuitePlan plan = new TestSuitePlan(
                " ",
                true,
                Path.of("target/../target/test-classes"),
                entries,
                List.of("*Test"),
                List.of("*IT"),
                List.of("fast"),
                List.of("slow"),
                List.of("com.example.MainTest"),
                List.of("unit"),
                List.of("broken"),
                List.of("com.example.MissingTest"),
                java.util.Map.of("com.example.MainTest", List.of("suite-a", "suite-b")),
                List.of("com.example.UnassignedTest"));
        entries.clear();

        assertEquals("all", plan.suiteName());
        assertEquals(Path.of("target/test-classes").toAbsolutePath().normalize(), plan.outputDirectory());
        assertEquals(1, plan.entries().size());
        assertFalse(plan.empty());
        assertThrows(UnsupportedOperationException.class, () -> plan.includeClassname().add("*Spec"));
        assertThrows(UnsupportedOperationException.class, () -> plan.overlappingEntries().put("late", List.of()));
    }

    private static TestInventoryEntry entry(String className) {
        return new TestInventoryEntry(
                className,
                Path.of("target/test-classes"),
                Path.of("target/test-classes").resolve(className.replace('.', '/') + ".class"),
                List.of(".*Test"),
                "junit-jupiter",
                List.of("fast"));
    }
}
