package sh.zolt.explain.verify;

import java.util.List;

/**
 * The full migration equivalence report: every module's per-scope comparison plus workspace totals.
 * {@link #hasDifferences()} drives the command exit code (non-zero when any module is one-sided or any
 * scope shows drift or a one-sided artifact).
 */
public record VerifyReport(
        String mavenRoot,
        String zoltRoot,
        List<ModuleComparison> modules,
        VerifySummary summary) {

    public VerifyReport {
        mavenRoot = mavenRoot == null ? "" : mavenRoot;
        zoltRoot = zoltRoot == null ? "" : zoltRoot;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public boolean hasDifferences() {
        return summary.hasDifferences();
    }
}
