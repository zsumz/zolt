package sh.zolt.update;

import java.util.Locale;

/** Renders an update plan as a semantic edit list (whole-file re-serialization makes text diffs noise). */
public final class UpdatePlanTextRenderer {
    public String render(UpdatePlan plan, boolean dryRun) {
        if (!plan.hasEdits() && plan.skips().isEmpty()) {
            return "Everything is up to date.\n";
        }
        StringBuilder text = new StringBuilder();
        if (plan.hasEdits()) {
            text.append(dryRun ? "Planned updates (dry run):\n" : "Updated:\n");
            int width = plan.edits().stream().mapToInt(edit -> label(edit).length()).max().orElse(0);
            for (UpdateEdit edit : plan.edits()) {
                text.append("  ")
                        .append(pad(label(edit), width))
                        .append("  ")
                        .append(edit.fromVersion())
                        .append(" -> ")
                        .append(edit.toVersion())
                        .append("  (")
                        .append(edit.changeClass().name().toLowerCase(Locale.ROOT))
                        .append(")\n");
            }
        }
        if (!plan.skips().isEmpty()) {
            text.append("Skipped:\n");
            for (UpdateSkip skip : plan.skips()) {
                text.append("  ").append(skip.section()).append('.').append(skip.identifier()).append('\n');
                text.append("      ").append(skip.reason()).append('\n');
            }
        }
        for (String warning : plan.warnings()) {
            text.append("warning: ").append(warning).append('\n');
        }
        return text.toString();
    }

    private static String label(UpdateEdit edit) {
        return edit.section() + "." + edit.identifier();
    }

    private static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
