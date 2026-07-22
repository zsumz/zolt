package sh.zolt.explain.verify;

import java.util.List;

/**
 * Renders a {@link VerifyReport} as a plain, deterministic text report (matching the style of the
 * sibling {@code zolt explain} reports — a returned String printed verbatim by the command). Only
 * differences are itemized; matched artifacts are summarized by count to keep the report auditable
 * without drowning the signal.
 *
 * <p>Incumbent-facing labels ("Maven root", "only in maven", …) are named from
 * {@link VerifyReport#buildTool()}, so a Gradle comparison reads "Gradle" throughout while a Maven one
 * is byte-identical to before.
 */
public final class VerifyReportFormatter {

    public String text(VerifyReport report) {
        String tool = report.buildTool().displayName();
        String toolToken = report.buildTool().token();
        StringBuilder output = new StringBuilder();
        output.append("Zolt migration verify: ").append(tool).append(" vs Zolt resolved dependencies\n\n");

        int rootWidth = Math.max((tool + " root:").length(), "Zolt root:".length()) + 1;
        output.append(padRight(tool + " root:", rootWidth)).append(report.mavenRoot()).append('\n');
        output.append(padRight("Zolt root:", rootWidth)).append(report.zoltRoot()).append('\n');
        VerifySummary summary = report.summary();
        output.append("Modules:    ").append(moduleCounts(summary, toolToken)).append("\n");

        for (ModuleComparison module : report.modules()) {
            module(output, module, tool, toolToken);
        }

        int labelWidth = Math.max(("only in " + toolToken + ":").length(), "version drift:".length());
        output.append("\nSummary\n");
        summaryLine(output, "matched:", summary.matched(), labelWidth);
        summaryLine(output, "version drift:", summary.versionDrift(), labelWidth);
        summaryLine(output, "only in " + toolToken + ":", summary.onlyInMaven(), labelWidth);
        summaryLine(output, "only in zolt:", summary.onlyInZolt(), labelWidth);
        output.append("  ").append(padRight("modules:", labelWidth)).append(' ')
                .append(moduleCounts(summary, toolToken)).append('\n');
        output.append("  result: ")
                .append(report.hasDifferences() ? "differences found" : "resolved sets match")
                .append('\n');
        return output.toString();
    }

    private static void module(StringBuilder output, ModuleComparison module, String tool, String toolToken) {
        output.append("\nModule ").append(module.moduleKey())
                .append(" [").append(module.presence().token()).append("]\n");
        int dirWidth = Math.max((tool + " dir:").length(), "Zolt member:".length()) + 1;
        module.mavenDirectory().ifPresent(dir ->
                output.append("  ").append(padRight(tool + " dir:", dirWidth)).append(dir).append('\n'));
        module.zoltMember().ifPresent(member ->
                output.append("  ").append(padRight("Zolt member:", dirWidth)).append(member).append('\n'));
        for (ScopeComparison scope : module.scopes()) {
            scope(output, scope, toolToken);
        }
        for (String note : module.notes()) {
            output.append("  note: ").append(note).append('\n');
        }
    }

    private static void scope(StringBuilder output, ScopeComparison scope, String toolToken) {
        if (scope.isEmpty()) {
            return;
        }
        output.append("  ").append(pad(scope.scope().token()))
                .append(" matched ").append(scope.matched().size())
                .append("  drift ").append(scope.versionDrift().size())
                .append("  only-in-").append(toolToken).append(' ').append(scope.onlyInMaven().size())
                .append("  only-in-zolt ").append(scope.onlyInZolt().size())
                .append('\n');
        for (VersionDrift drift : scope.versionDrift()) {
            output.append("    ~ ").append(drift.key())
                    .append("  ").append(toolToken).append(' ').append(drift.mavenVersion())
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

    private static void summaryLine(StringBuilder output, String label, int value, int labelWidth) {
        output.append("  ").append(padRight(label, labelWidth)).append(' ').append(value).append('\n');
    }

    private static String moduleCounts(VerifySummary summary, String toolToken) {
        return summary.modules() + " (both " + summary.modulesBoth()
                + ", " + toolToken + "-only " + summary.modulesMavenOnly()
                + ", zolt-only " + summary.modulesZoltOnly() + ")";
    }

    private static String pad(String scopeToken) {
        return String.format("%-9s", scopeToken);
    }

    private static String padRight(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    /** Exposed for callers that want the marker legend alongside the report. */
    public static List<String> legend() {
        return List.of(
                "~ version drift (same group:artifact, different version)",
                "- only in incumbent (Maven or Gradle)",
                "+ only in Zolt");
    }
}
