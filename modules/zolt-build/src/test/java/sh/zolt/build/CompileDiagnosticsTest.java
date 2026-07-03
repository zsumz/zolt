package sh.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CompileDiagnosticsTest {
    @Test
    void clampsNegativeCountersToZero() {
        CompileDiagnostics diagnostics = new CompileDiagnostics(-1, -2, -3, -4, -5, -6, -7, -8);

        assertEquals(0, diagnostics.sourcesAdded());
        assertEquals(0, diagnostics.sourcesChanged());
        assertEquals(0, diagnostics.sourcesDeleted());
        assertEquals(0, diagnostics.sourcesRecompiled());
        assertEquals(0, diagnostics.dependentSourcesRecompiled());
        assertEquals(0, diagnostics.classesDeleted());
        assertEquals(0, diagnostics.abiChangedClasses());
        assertEquals(0, diagnostics.packagePrivateAbiChangedClasses());
    }

    @Test
    void legacyDiagnosticsTreatSkippedCompilationAsNoRecompiledSources() {
        assertEquals(
                new CompileDiagnostics(0, 0, 0, 7, 0, 0, 0, 0),
                CompileDiagnostics.legacy(7, false));
        assertEquals(CompileDiagnostics.empty(), CompileDiagnostics.legacy(7, true));
    }
}
