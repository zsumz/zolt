package sh.zolt.update;

import java.util.List;

/**
 * A complete outdated report: one block per scope, plus workspace-level notes such as
 * shared-coordinate dedup annotations.
 */
public record OutdatedReport(List<OutdatedScopeReport> scopes, List<String> notes) {
    public OutdatedReport {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean hasEntries() {
        return scopes.stream().anyMatch(scope -> !scope.entries().isEmpty());
    }
}
