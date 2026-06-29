package com.zolt.build.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.build.CompileDiagnostics;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IncrementalCompilePlanTest {
    @TempDir
    private Path tempDir;

    @Test
    void fullPlanNormalizesDeletedOutputsAndDiagnostics() {
        Path alpha = tempDir.resolve("classes/com/example/Alpha.class");
        Path beta = tempDir.resolve("classes/com/example/../example/Beta.class");

        IncrementalCompilePlan plan = IncrementalCompilePlan.full("source-deleted", List.of(beta, alpha), 2);

        assertEquals(
                List.of(alpha.toAbsolutePath().normalize(), beta.toAbsolutePath().normalize()),
                plan.outputsToDelete());
        assertEquals(
                new CompileDiagnostics(0, 0, 2, 5, 0, 2, 0, 0),
                plan.fullDiagnostics(5));
    }

    @Test
    void incrementalPlanDiagnosticsIncludeValidationImpact() {
        IncrementalCompilePlan plan = IncrementalCompilePlan.incremental(
                List.of(tempDir.resolve("src/main/java/com/example/App.java")),
                1,
                List.of(sourceRecord("Changed.java")),
                List.of(sourceRecord("Changed.java"), sourceRecord("Dependent.java")),
                List.of(),
                null);
        IncrementalCompileValidation validation = IncrementalCompileValidation.success(
                List.of(tempDir.resolve("src/main/java/com/example/Dependent.java")),
                1,
                2);

        assertEquals(
                new CompileDiagnostics(1, 1, 0, 2, 1, 0, 1, 2),
                plan.diagnostics(2, validation));
    }

    private IncrementalCompileState.SourceRecord sourceRecord(String fileName) {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path source = sourceRoot.resolve("com/example/" + fileName);
        return new IncrementalCompileState.SourceRecord(
                source,
                sourceRoot,
                Optional.empty(),
                "sha256:" + fileName,
                "com.example",
                List.of(fileName.replace(".java", "")),
                List.of(tempDir.resolve("target/classes/com/example/" + fileName.replace(".java", ".class"))),
                List.of());
    }
}
