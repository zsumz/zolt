package sh.zolt.update;

import java.util.List;

/** The reportable entries for one scope (a single project, or one workspace member or root). */
public record OutdatedScopeReport(String label, List<OutdatedEntry> entries) {
    public OutdatedScopeReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
