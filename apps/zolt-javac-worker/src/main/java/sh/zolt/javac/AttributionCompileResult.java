package sh.zolt.javac;

import java.util.List;

/**
 * Outcome of an attribution compile: the javac exit code and textual diagnostics (byte-identical in
 * intent to the legacy {@code compiler.run} path), plus the generated-output attribution. When
 * {@code unattributed} is set, the worker could not fully explain every generated output through the
 * Filer, so the caller must fall back to a full recompile rather than trust the attribution.
 */
record AttributionCompileResult(
        int exitCode,
        String diagnostics,
        boolean present,
        boolean unattributed,
        List<GeneratedFileRecord> entries) {
    AttributionCompileResult {
        entries = List.copyOf(entries);
    }
}
