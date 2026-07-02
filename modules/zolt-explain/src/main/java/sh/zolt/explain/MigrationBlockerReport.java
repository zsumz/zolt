package sh.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public record MigrationBlockerReport(
        String source,
        Path root,
        String status,
        List<MigrationReadinessFinding> findings,
        List<String> nextSteps) {
    public MigrationBlockerReport {
        findings = List.copyOf(findings);
        nextSteps = List.copyOf(nextSteps);
    }
}
