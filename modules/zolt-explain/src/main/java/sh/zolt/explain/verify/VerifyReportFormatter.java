package sh.zolt.explain.verify;

import java.util.List;

/**
 * Renders a {@link VerifyReport} as a plain, deterministic text report (matching the style of the
 * sibling {@code zolt explain} reports — a returned String printed verbatim by the command). Only
 * differences are itemized; matched artifacts are summarized by count to keep the report auditable
 * without drowning the signal.
 */
public final class VerifyReportFormatter {

    public String text(VerifyReport report) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt migration verify: Maven vs Zolt resolved dependencies\n\n");
        output.append("Maven root: ").append(report.mavenRoot()).append('\n');
        output.append("Zolt root:  ").append(report.zoltRoot()).append('\n');
        VerifySummary summary = report.summary();
        output.append("Modules:    ").append(moduleCounts(summary)).append("\n");

        for (ModuleComparison module : report.modules()) {
            module(output, module);
        }

        output.append("\nSummary\n");
        output.append("  matched:       ").append(summary.matched()).append('\n');
        output.append("  version drift: ").append(summary.versionDrift()).append('\n');
        output.append("  only in maven: ").append(summary.onlyInMaven()).append('\n');
        output.append("  only in zolt:  ").append(summary.onlyInZolt()).append('\n');
        output.append("  modules:       ").append(moduleCounts(summary)).append('\n');
        output.append("  result: ")
                .append(report.hasDifferences() ? "differences found" : "resolved sets match")
                .append('\n');
        return output.toString();
    }

    private static void module(StringBuilder output, ModuleComparison module) {
        output.append("\nModule ").append(module.moduleKey())
                .append(" [").append(module.presence().token()).append("]\n");
        module.mavenDirectory().ifPresent(dir -> output.append("  Maven dir:   ").append(dir).append('\n'));
        module.zoltMember().ifPresent(member -> output.append("  Zolt member: ").append(member).append('\n'));
        for (ScopeComparison scope : module.scopes()) {
            scope(output, scope);
        }
        for (String note : module.notes()) {
            output.append("  note: ").append(note).append('\n');
        }
    }

    private static void scope(StringBuilder output, ScopeComparison scope) {
        if (scope.isEmpty()) {
            return;
        }
        output.append("  ").append(pad(scope.scope().token()))
                .append(" matched ").append(scope.matched().size())
                .append("  drift ").append(scope.versionDrift().size())
                .append("  only-in-maven ").append(scope.onlyInMaven().size())
                .append("  only-in-zolt ").append(scope.onlyInZolt().size())
                .append('\n');
        for (VersionDrift drift : scope.versionDrift()) {
            output.append("    ~ ").append(drift.key())
                    .append("  maven ").append(drift.mavenVersion())
                    .append("  zolt ").append(drift.zoltVersion())
                    .append('\n');
        }
        for (ResolvedArtifact artifact : scope.onlyInMaven()) {
            output.append("    - ").append(artifact.coordinate()).append('\n');
        }
        for (ResolvedArtifact artifact : scope.onlyInZolt()) {
            output.append("    + ").append(artifact.coordinate()).append('\n');
        }
    }

    private static String moduleCounts(VerifySummary summary) {
        return summary.modules() + " (both " + summary.modulesBoth()
                + ", maven-only " + summary.modulesMavenOnly()
                + ", zolt-only " + summary.modulesZoltOnly() + ")";
    }

    private static String pad(String scopeToken) {
        return String.format("%-9s", scopeToken);
    }

    /** Exposed for callers that want the marker legend alongside the report. */
    public static List<String> legend() {
        return List.of(
                "~ version drift (same group:artifact, different version)",
                "- only in Maven",
                "+ only in Zolt");
    }
}
