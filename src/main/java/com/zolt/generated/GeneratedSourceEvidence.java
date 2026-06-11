package com.zolt.generated;

import com.zolt.project.GeneratedSourceStep;
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
        String compileLane) {
    public GeneratedSourceEvidence {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
