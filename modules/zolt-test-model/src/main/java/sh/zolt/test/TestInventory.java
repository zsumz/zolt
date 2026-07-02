package sh.zolt.test;

import java.util.List;

public record TestInventory(
        List<TestInventoryEntry> entries,
        TestInventorySummary summary) {
    public TestInventory {
        entries = List.copyOf(entries);
        summary = summary == null ? TestInventorySummary.empty() : summary;
    }

    public static TestInventory empty(TestInventorySummary summary) {
        return new TestInventory(List.of(), summary);
    }
}
