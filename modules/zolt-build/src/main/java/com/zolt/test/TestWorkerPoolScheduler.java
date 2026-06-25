package com.zolt.test;

import com.zolt.project.TestSuiteSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestWorkerPoolScheduler {
    public TestWorkerPoolPlan plan(List<TestInventoryEntry> entries, TestSuiteSettings settings) {
        List<TestInventoryEntry> selectedEntries = List.copyOf(entries == null ? List.of() : entries);
        TestSuiteSettings suiteSettings = settings == null ? TestSuiteSettings.empty() : settings;
        if (!suiteSettings.parallelSafe() || suiteSettings.maxWorkers() == 1) {
            return serialPlan(selectedEntries);
        }

        List<TestInventoryEntry> remaining = new ArrayList<>(selectedEntries);
        List<TestWorkerPoolWave> waves = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<TestInventoryEntry> waveEntries = new ArrayList<>();
            Set<String> waveLocks = new LinkedHashSet<>();
            Map<String, List<String>> waveResourceLocks = new LinkedHashMap<>();
            int index = 0;
            while (index < remaining.size() && waveEntries.size() < suiteSettings.maxWorkers()) {
                TestInventoryEntry candidate = remaining.get(index);
                List<String> candidateLocks = locksFor(candidate, suiteSettings);
                if (canSchedule(candidateLocks, waveLocks)) {
                    waveEntries.add(candidate);
                    if (!candidateLocks.isEmpty()) {
                        waveResourceLocks.put(candidate.className(), candidateLocks);
                        waveLocks.addAll(candidateLocks);
                    }
                    remaining.remove(index);
                } else {
                    index++;
                }
            }
            if (waveEntries.isEmpty()) {
                TestInventoryEntry candidate = remaining.removeFirst();
                List<String> candidateLocks = locksFor(candidate, suiteSettings);
                waveEntries.add(candidate);
                if (!candidateLocks.isEmpty()) {
                    waveResourceLocks.put(candidate.className(), candidateLocks);
                }
            }
            waves.add(new TestWorkerPoolWave(waveEntries, waveResourceLocks));
        }
        return new TestWorkerPoolPlan(true, suiteSettings.maxWorkers(), waves);
    }

    private static TestWorkerPoolPlan serialPlan(List<TestInventoryEntry> entries) {
        List<TestWorkerPoolWave> waves = entries.stream()
                .map(entry -> new TestWorkerPoolWave(List.of(entry), Map.of()))
                .toList();
        return new TestWorkerPoolPlan(false, 1, waves);
    }

    private static boolean canSchedule(List<String> locks, Set<String> waveLocks) {
        for (String lock : locks) {
            if (waveLocks.contains(lock)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> locksFor(TestInventoryEntry entry, TestSuiteSettings settings) {
        return settings.resourceLocks().getOrDefault(entry.className(), List.of());
    }
}
