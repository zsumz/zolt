package sh.zolt.test.shard;

import sh.zolt.test.TestInventoryEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record TestWorkerPoolWave(
        List<TestInventoryEntry> entries,
        Map<String, List<String>> resourceLocks) {
    public TestWorkerPoolWave {
        entries = List.copyOf(entries == null ? List.of() : entries);
        resourceLocks = copyLocks(resourceLocks);
    }

    private static Map<String, List<String>> copyLocks(Map<String, List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(values).entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copied);
    }
}
