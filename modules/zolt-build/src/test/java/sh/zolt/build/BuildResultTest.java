package sh.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BuildResultTest {
    private static final Path OUTPUT_DIRECTORY = Path.of("target/classes");

    @Test
    void normalizesNullLegacyFieldsAndNegativeFingerprintTiming() {
        BuildResult result = new BuildResult(
                null,
                3,
                2,
                OUTPUT_DIRECTORY,
                "javac output",
                false,
                " ",
                null,
                null,
                -25L,
                2_999_999L);

        assertFalse(result.resolvedLockfile());
        assertEquals("full", result.mainCompilationMode());
        assertEquals("", result.mainIncrementalFallbackReason());
        assertEquals(CompileDiagnostics.empty(), result.mainCompileDiagnostics());
        assertEquals(0L, result.mainFingerprintCheckNanos());
        assertEquals(0L, result.mainFingerprintCheckMillis());
        assertEquals(2_999_999L, result.mainFingerprintWriteNanos());
        assertEquals(2L, result.mainFingerprintWriteMillis());
    }

    @Test
    void skippedLegacyResultForcesSkippedModeAndNoRecompiledSources() {
        BuildResult result = new BuildResult(
                Optional.empty(),
                4,
                1,
                OUTPUT_DIRECTORY,
                "",
                true,
                3_400_000L,
                4_500_000L);

        assertEquals("skipped", result.mainCompilationMode());
        assertEquals(CompileDiagnostics.legacy(4, true), result.mainCompileDiagnostics());
        assertEquals(0, result.mainCompileDiagnostics().sourcesRecompiled());
        assertEquals(3L, result.mainFingerprintCheckMillis());
        assertEquals(4L, result.mainFingerprintWriteMillis());
    }
}
