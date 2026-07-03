package sh.zolt.test.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.TestSuiteSettings;
import sh.zolt.test.TestInventoryEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TestWorkerPoolSchedulerTest {
    private final TestWorkerPoolScheduler scheduler = new TestWorkerPoolScheduler();

    @Test
    void keepsSerialSuitesDisabledByDefault() {
        TestWorkerPoolPlan plan = scheduler.plan(
                List.of(entry("com.example.AaaTest"), entry("com.example.BbbTest")),
                TestSuiteSettings.empty());

        assertFalse(plan.enabled());
        assertEquals(1, plan.maxWorkers());
        assertEquals(List.of(
                List.of("com.example.AaaTest"),
                List.of("com.example.BbbTest")),
                waveClassNames(plan));
    }

    @Test
    void boundsParallelSafeSuitesByMaxWorkers() {
        TestWorkerPoolPlan plan = scheduler.plan(
                List.of(
                        entry("com.example.AaaTest"),
                        entry("com.example.BbbTest"),
                        entry("com.example.CccTest"),
                        entry("com.example.DddTest"),
                        entry("com.example.EeeTest")),
                parallelSuite(2, Map.of()));

        assertTrue(plan.enabled());
        assertEquals(2, plan.maxWorkers());
        assertEquals(List.of(
                List.of("com.example.AaaTest", "com.example.BbbTest"),
                List.of("com.example.CccTest", "com.example.DddTest"),
                List.of("com.example.EeeTest")),
                waveClassNames(plan));
    }

    @Test
    void serializesEntriesWithTheSameResourceLock() {
        TestWorkerPoolPlan plan = scheduler.plan(
                List.of(
                        entry("com.example.DbOneTest"),
                        entry("com.example.NoLockTest"),
                        entry("com.example.DbTwoTest"),
                        entry("com.example.KafkaTest")),
                parallelSuite(
                        3,
                        Map.of(
                                "com.example.DbOneTest",
                                List.of("database"),
                                "com.example.DbTwoTest",
                                List.of("database"),
                                "com.example.KafkaTest",
                                List.of("kafka"))));

        assertEquals(List.of(
                List.of("com.example.DbOneTest", "com.example.NoLockTest", "com.example.KafkaTest"),
                List.of("com.example.DbTwoTest")),
                waveClassNames(plan));
        assertEquals(
                Map.of(
                        "com.example.DbOneTest",
                        List.of("database"),
                        "com.example.KafkaTest",
                        List.of("kafka")),
                plan.waves().getFirst().resourceLocks());
    }

    @Test
    void treatsUnknownResourceNamesAsLocks() {
        TestWorkerPoolPlan plan = scheduler.plan(
                List.of(
                        entry("com.example.FirstTest"),
                        entry("com.example.SecondTest"),
                        entry("com.example.ThirdTest")),
                parallelSuite(
                        3,
                        Map.of(
                                "com.example.FirstTest",
                                List.of("external-service"),
                                "com.example.SecondTest",
                                List.of("external-service"),
                                "com.example.ThirdTest",
                                List.of("another-external-service"))));

        assertEquals(List.of(
                List.of("com.example.FirstTest", "com.example.ThirdTest"),
                List.of("com.example.SecondTest")),
                waveClassNames(plan));
    }

    @Test
    void workerPoolPlanDefaultsNullWavesAndRejectsInvalidMaxWorkers() {
        TestWorkerPoolPlan plan = new TestWorkerPoolPlan(true, 2, null);

        assertTrue(plan.empty());
        assertEquals(List.of(), plan.waves());
        assertThrows(UnsupportedOperationException.class, () -> plan.waves().add(new TestWorkerPoolWave(List.of(), Map.of())));
        assertEquals(
                "Test worker pool maxWorkers must be greater than zero.",
                assertThrows(IllegalArgumentException.class, () -> new TestWorkerPoolPlan(true, 0, List.of()))
                        .getMessage());
    }

    @Test
    void workerPoolWaveSortsResourceLocksAndCopiesNestedLists() {
        java.util.ArrayList<String> databaseLocks = new java.util.ArrayList<>(List.of("database"));
        TestWorkerPoolWave wave = new TestWorkerPoolWave(
                List.of(entry("com.example.DbTest")),
                Map.of(
                        "com.example.ZedTest",
                        List.of("z"),
                        "com.example.DbTest",
                        databaseLocks));

        databaseLocks.add("late");

        assertEquals(List.of("com.example.DbTest", "com.example.ZedTest"), List.copyOf(wave.resourceLocks().keySet()));
        assertEquals(List.of("database"), wave.resourceLocks().get("com.example.DbTest"));
        assertThrows(UnsupportedOperationException.class, () -> wave.resourceLocks().get("com.example.DbTest").add("late"));
    }

    private static TestSuiteSettings parallelSuite(int maxWorkers, Map<String, List<String>> resourceLocks) {
        return new TestSuiteSettings(
                List.of("*Test"),
                List.of(),
                List.of(),
                List.of(),
                true,
                maxWorkers,
                resourceLocks);
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

    private static List<List<String>> waveClassNames(TestWorkerPoolPlan plan) {
        return plan.waves().stream()
                .map(wave -> wave.entries().stream().map(TestInventoryEntry::className).toList())
                .toList();
    }
}
