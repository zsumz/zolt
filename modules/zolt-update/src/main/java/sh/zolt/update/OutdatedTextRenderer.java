package sh.zolt.update;

import sh.zolt.dependency.UpdateClass;
import java.util.Locale;
import java.util.Optional;

/** Renders an outdated report as a readable, deterministic text table grouped per scope. */
public final class OutdatedTextRenderer {
    public String render(OutdatedReport report) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < report.scopes().size(); index++) {
            if (index > 0) {
                text.append('\n');
            }
            renderScope(text, report.scopes().get(index));
        }
        if (!report.notes().isEmpty()) {
            text.append('\n');
            for (String note : report.notes()) {
                text.append("note: ").append(note).append('\n');
            }
        }
        return text.toString();
    }

    private void renderScope(StringBuilder text, OutdatedScopeReport scope) {
        text.append(scope.label()).append('\n');
        if (scope.entries().isEmpty()) {
            text.append("  All tracked versions are up to date.\n");
            return;
        }
        int identifierWidth = scope.entries().stream().mapToInt(entry -> entry.identifier().length()).max().orElse(0);
        int currentWidth = scope.entries().stream().mapToInt(entry -> entry.currentVersion().length()).max().orElse(0);
        for (OutdatedEntry entry : scope.entries()) {
            renderEntry(text, entry, identifierWidth, currentWidth);
        }
    }

    private void renderEntry(StringBuilder text, OutdatedEntry entry, int identifierWidth, int currentWidth) {
        text.append("  ")
                .append(pad(entry.identifier(), identifierWidth))
                .append("  ")
                .append(pad(entry.currentVersion(), currentWidth))
                .append("  ");
        switch (entry.status()) {
            case CURRENT -> text.append("up to date");
            case UNKNOWN -> text.append("unknown");
            case UPDATE_AVAILABLE -> renderUpdate(text, entry);
        }
        text.append('\n');
        renderDetails(text, entry);
    }

    private void renderUpdate(StringBuilder text, OutdatedEntry entry) {
        OutdatedCandidates candidates = entry.candidates();
        boolean inMajor = candidates.selectedInMajor().isPresent();
        String target = (inMajor ? candidates.selectedInMajor() : candidates.selectedLatest()).orElse("?");
        Optional<UpdateClass> targetClass = inMajor ? candidates.selectedInMajorClass() : candidates.selectedLatestClass();
        text.append("-> ").append(target);
        targetClass.ifPresent(value -> text.append(" (").append(className(value)).append(')'));
        candidates.selectedLatest().ifPresent(latest -> {
            if (!latest.equals(target)) {
                text.append("  latest ").append(latest);
                candidates.selectedLatestClass().ifPresent(value -> text.append(" (").append(className(value)).append(')'));
            }
        });
        entry.sourceRepository().ifPresent(source -> text.append("  ").append(source));
    }

    private void renderDetails(StringBuilder text, OutdatedEntry entry) {
        for (String label : entry.governs()) {
            text.append("      governs ").append(label).append('\n');
        }
        if (!entry.members().isEmpty()) {
            text.append("      members ").append(String.join(", ", entry.members())).append('\n');
        }
        for (String note : entry.notes()) {
            text.append("      note: ").append(note).append('\n');
        }
    }

    private static String className(UpdateClass updateClass) {
        return updateClass.name().toLowerCase(Locale.ROOT);
    }

    private static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
