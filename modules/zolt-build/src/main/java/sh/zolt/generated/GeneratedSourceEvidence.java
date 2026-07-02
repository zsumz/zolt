package sh.zolt.generated;

import sh.zolt.project.GeneratedSourceStep;
import java.nio.file.Path;
import java.util.List;

public record GeneratedSourceEvidence(
        String id,
        String sourceRootId,
        String scope,
        GeneratedSourceStep step,
        Path output,
        List<Path> inputs,
        boolean outputExists,
        boolean inputsPresent,
        String freshness,
        String ownership,
        String compileLane,
        String toolArtifact,
        String toolFingerprint,
        String optionsFingerprint) {
    public GeneratedSourceEvidence {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
