package sh.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public record MigrationReadinessScorecard(
        String source,
        Path root,
        String status,
        List<MigrationReadinessConcern> concerns,
        List<String> checklist) {
    public MigrationReadinessScorecard {
        concerns = List.copyOf(concerns);
        checklist = List.copyOf(checklist);
    }
}
