package sh.zolt.explain.verify;

import java.util.List;

/**
 * The full migration equivalence report: every module's per-scope comparison plus workspace totals.
 * {@link #hasDifferences()} drives the command exit code (non-zero when any module is one-sided or any
 * scope shows drift or a one-sided artifact).
 *
 * <p>{@code buildTool} names the incumbent side (Maven or Gradle). The {@code mavenRoot} field name is
 * retained regardless of incumbent for JSON-schema stability; it holds the incumbent project root.
 */
public record VerifyReport(
        BuildTool buildTool,
        String mavenRoot,
        String zoltRoot,
        List<ModuleComparison> modules,
        VerifySummary summary) {

    public VerifyReport {
        buildTool = buildTool == null ? BuildTool.MAVEN : buildTool;
        mavenRoot = mavenRoot == null ? "" : mavenRoot;
        zoltRoot = zoltRoot == null ? "" : zoltRoot;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public boolean hasDifferences() {
        return summary.hasDifferences();
    }
}
